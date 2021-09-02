// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.rest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

public class SalesForceAPI {
    private String servicePath = "services/data/v50.0/";
    private String host;
    private String clientId;
    private String clientSecret;
    private String user;
    private String pass;
    private transient String accessToken;

    public interface QueryResultProcessor {
        public void processResult(JSONObject r);
    }

    public interface QueryResultMapper<E> {
        public E map(JSONObject r);
    }

    public SalesForceAPI(String host, String clientId, String clientSecret, String user, String pass) {
        this.host = host;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.user = user;
        this.pass = pass;
    }

    public Connection getConnection() {
        return new Connection();
    }

    public class Connection {
        private String token;

        public Connection() {
            connect();
        }

        private synchronized void connect() {
            // if connect is called with token == accessToken then there was
            // a problem with the access token so get a new one
            // if != then there WAS a problem but another thread already
            // got a new access token so just use the new one
            if (accessToken == null || (token != null && token.equals(accessToken))) {
                // Get new access token
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("grant_type", "password");
                params.put("client_id", clientId);
                params.put("client_secret", clientSecret);
                params.put("username", user);
                params.put("password", pass);
                try {
                    JSONObject resp = postJSON(host + "services/oauth2/token", params);
                    accessToken = resp.getString("access_token");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            token = accessToken; // set connection token to current access token
        }

        public JSONObject get(String object) {
            try {
                return getJSON(host + servicePath + "sobjects/" + object, null);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public void query(String query, QueryResultProcessor resultProcessor) {
            JSONObject r = query(query);
            resultProcessor.processResult(r);
            String next = r.optString("nextRecordsUrl");

            while (next != null && !next.trim().equals("")) {
                r = queryNext(next);
                resultProcessor.processResult(r);
                next = r.optString("nextRecordsUrl");
            }
        }

        public JSONObject query(String query) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("q", query);
                return getJSON(host + servicePath + "query", params);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public JSONObject queryNext(String service) {
            try {
                return getJSON(host + service, null);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public <E> List<E> queryMapped(String query, QueryResultMapper<E> mapper) {
            List<E> results = new ArrayList<>();
            query(query, batch -> {
                JSONArray a = batch.optJSONArray("records");
                if (a != null) {
                    try {
                        // for each record in the batch add the object to the results
                        for (int i = 0; i < a.length(); i++)
                            results.add(mapper.map(a.getJSONObject(i)));
                    } catch (Exception e) {
                        throw new RuntimeException("Could not get record from JSONArray", e);
                    }
                }
            });
            return results;
        }

        public JSONObject insert(String object, String statement) {
            try {
                return postJSON(host + servicePath + "sobjects/" + object, statement);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public JSONObject launchFlow(String flowName, String statement) {
            try {
                return postJSON(host + servicePath + "actions/custom/flow/" + flowName, statement);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        public String batchQuery(String query) {
            try {
                JSONObject job = postJSON(host + "jobs/query", "{\"operation\": \"query\", \"query\": \"" + query + "\"}");
                String id = job.getString("id");
                // TODO: check job status
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private JSONObject getJSON(String urlString, Map<String, Object> params) throws Exception {
            return new JSONObject(get(urlString, "application/json", params));
        }

        private JSONObject postJSON(String urlString, Map<String, Object> params) throws Exception {
            return new JSONObject(post(urlString, "application/json", params, null));
        }

        private JSONObject postJSON(String urlString, String data) throws Exception {
            String response = post(urlString, "application/json", null, data);
            try {
                return new JSONObject(response);
            } catch (Exception e) {
                JSONArray a = new JSONArray(response);
                return a.getJSONObject(0);
            }
        }

        private String getCSV(String urlString, Map<String, Object> params) throws Exception {
            return get(urlString, "text/csv", params);
        }

        private String post(String urlString, String acceptType, Map<String, Object> params, String data) throws UnsupportedEncodingException {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", acceptType);

            byte[] postDataBytes = null;
            int retries = 1;

            if (params != null) {
                postDataBytes = buildParamString(params).getBytes("UTF-8");
                headers.put("Content-Type", "application/x-www-form-urlencoded");
                retries = 0; // if posting form login don't do any retries or we'll get stuck in a loop
            } else if (data != null) {
                headers.put("Authorization", "");
                headers.put("Content-Type", "application/json");
                postDataBytes = data.getBytes("UTF-8");
            }

            return call("POST", urlString, headers, postDataBytes, retries);
        }

        private String get(String urlString, String acceptType, Map<String, Object> params) throws UnsupportedEncodingException {
            if (params != null) {
                String queryString = buildParamString(params);
                if (queryString.length() > 0)
                    urlString += "?" + queryString;
            }

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "");
            headers.put("Accept", acceptType);

            return call("GET", urlString, headers, null, 1);
        }

        private String call(String method, String urlString, Map<String, String> headers, byte[] data, int retries) {
            Exception caught = null;
            int maxCount = retries + 1;
            int count = 0;
            int status = 0;
            while (count < maxCount) {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod(method);
                    for (Map.Entry<String, String> h : headers.entrySet()) {
                        if (h.getKey().equals("Authorization")) {
                            if (token == null) {
                                throw new RuntimeException("SalesForce connection not authorized");
                            }
                            conn.setRequestProperty("Authorization", "Bearer " + token);
                        } else {
                            conn.setRequestProperty(h.getKey(), h.getValue());
                        }
                    }

                    if (data != null) {
                        conn.setRequestProperty("Content-Length", String.valueOf(data.length));
                        conn.setDoOutput(true);
                        conn.getOutputStream().write(data);
                    }

                    Reader in = null;
                    status = conn.getResponseCode();

                    // if not a login error then set count so we don't try to log in again
                    if (status != 401)
                        count = maxCount;

                    if (status != 400)
                        in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    else
                        in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

                    StringBuilder sb = new StringBuilder();
                    for (int c; (c = in.read()) >= 0; )
                        sb.append((char) c);

                    if (status == 400) {
                        throw new RuntimeException("Error 400: " + sb.toString());
                    }

                    return sb.toString();
                } catch (Exception e) {
                    caught = e;
                }

                // try resetting the connection on an error
                count++;
                if (count < maxCount)
                    connect();
            }

            throw new RuntimeException("Error connecting to SalesForce", caught);
        }

        private String buildParamString(Map<String, Object> params) throws UnsupportedEncodingException {
            if (params != null) {
                StringBuilder paramString = new StringBuilder();
                for (Map.Entry<String, Object> param : params.entrySet()) {
                    if (paramString.length() != 0) paramString.append('&');
                    paramString.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                    paramString.append('=');
                    paramString.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
                }
                return paramString.toString();
            }
            return "";
        }

    }
}
