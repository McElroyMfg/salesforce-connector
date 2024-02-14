// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.rest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SFRestConnection {
    static {
        SFRestConnection.allowMethods("PATCH");
    }

    private static class SFRestResponse {
        public boolean authException = false;
        public boolean isError = false;
        String response;
    }

    public static class SFRestConfig {
        private final String servicePath = "services/data/v50.0/";
        private final String host;
        private final String clientId;
        private final String clientSecret;
        private final String user;
        private final String pass;
        private transient String accessToken;

        public SFRestConfig(String host, String clientId, String clientSecret, String user, String pass) {
            this.host = host;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.user = user;
            this.pass = pass;
        }
    }


    private final SFRestConfig config;
    private String token;


    public SFRestConnection(SFRestConfig config) {
        this.config = config;
        connect();
    }


    public String getHost() {
        return config.host;
    }


    public String getServiceUrl() {
        return config.host + config.servicePath;
    }


    private synchronized void connect() {
        // if connect is called with token == accessToken then there was
        // a problem with the access token so get a new one
        // if != then there WAS a problem but another thread already
        // got a new access token so just use the new one
        if (config.accessToken == null || (token != null && token.equals(config.accessToken))) {
            // Get new access token
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("grant_type", "password");
            params.put("client_id", config.clientId);
            params.put("client_secret", config.clientSecret);
            params.put("username", config.user);
            params.put("password", config.pass);
            try {
                token = null;
                JSONObject resp = logIn(config.host + "services/oauth2/token", params);
                if (resp != null)
                    config.accessToken = resp.getString("access_token");
            } catch (Exception e) {
                config.accessToken = null;
                e.printStackTrace();
            }
        }

        token = config.accessToken; // set connection token to current access token
    }


    private JSONObject logIn(String urlString, Map<String, Object> params) throws JSONException {
        SFRestResponse restResponse = logIn(urlString, "application/json", params);
        if (restResponse.authException || restResponse.isError)
            return null;
        else
            return new JSONObject(restResponse.response);
    }


    public JSONObject getJSON(String urlString, Map<String, Object> params) throws Exception {
        SFRestResponse restResponse;
        int tries = 0;
        do {
            restResponse = get(urlString, "application/json", params);
            if (restResponse.authException)
                connect();
            else if (restResponse.isError)
                throw new Exception(restResponse.response);
            else
                return new JSONObject(restResponse.response);
        } while (++tries < 2);
        throw new Exception(restResponse.response);
    }


    public JSONObject postJSON(String urlString, String data) throws Exception {
        return sendJSON("POST", urlString, data);
    }


    public JSONObject patchJSON(String urlString, String data) throws Exception {
        return sendJSON("PATCH", urlString, data);
    }


    private JSONObject sendJSON(String method, String urlString, String data) throws Exception {
        SFRestResponse restResponse;
        int tries = 0;
        do {
            restResponse = send(method, urlString, "application/json", data);
            if (restResponse.authException)
                connect();
            else if (restResponse.isError)
                throw new Exception(restResponse.response);
            else {
                if (restResponse.response.startsWith("{"))
                    return new JSONObject(restResponse.response);
                else if (restResponse.response.startsWith("[")) {
                    JSONArray a = new JSONArray(restResponse.response);
                    return a.getJSONObject(0);
                } else
                    return null;
            }
        } while (++tries < 2);
        throw new Exception(restResponse.response);
    }


    private SFRestResponse logIn(String urlString, String acceptType, Map<String, Object> params) {
        Map<String, String> headers = new LinkedHashMap<>();
        byte[] postDataBytes = null;
        if (params != null) {
            try {
                postDataBytes = buildParamString(params).getBytes(StandardCharsets.UTF_8);
            } catch (UnsupportedEncodingException e) {
                SFRestResponse restResponse = new SFRestResponse();
                restResponse.isError = true;
                restResponse.response = e.getMessage();
                return restResponse;
            }
            headers.put("Content-Type", "application/x-www-form-urlencoded");
        }
        return call("POST", acceptType, urlString, headers, postDataBytes);
    }


    private SFRestResponse send(String method, String urlString, String acceptType, String data) {
        Map<String, String> headers = new LinkedHashMap<>();
        byte[] postDataBytes = null;
        headers.put("Content-Type", "application/json");
        if (data != null) {
            postDataBytes = data.getBytes(StandardCharsets.UTF_8);
        }
        return call(method, acceptType, urlString, headers, postDataBytes);
    }


    private SFRestResponse get(String urlString, String acceptType, Map<String, Object> params) {
        if (params != null) {
            String queryString;
            try {
                queryString = buildParamString(params);
            } catch (UnsupportedEncodingException e) {
                SFRestResponse restResponse = new SFRestResponse();
                restResponse.isError = true;
                restResponse.response = e.getMessage();
                return restResponse;
            }
            if (queryString.length() > 0)
                urlString += "?" + queryString;
        }
        return call("GET", acceptType, urlString, null, null);
    }


    private SFRestResponse call(String method, String acceptType, String urlString, Map<String, String> headers, byte[] data) {
        SFRestResponse restResponse = new SFRestResponse();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", acceptType);
            if (token != null)
                conn.setRequestProperty("Authorization", "Bearer " + token);

            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    conn.setRequestProperty(h.getKey(), h.getValue());
                }
            }

            if (data != null) {
                conn.setRequestProperty("Content-Length", String.valueOf(data.length));
                conn.setDoOutput(true);
                conn.getOutputStream().write(data);
            }

            int status = conn.getResponseCode();

            if (status == 401) {
                restResponse.authException = true;
                return restResponse;
            }

            StringBuilder sb = new StringBuilder();
            InputStream stream = conn.getErrorStream();
            if (stream == null)
                stream = conn.getInputStream();
            if (stream != null) {
                Reader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                for (int c; (c = in.read()) >= 0; )
                    sb.append((char) c);
            }

            restResponse.response = sb.toString();
            if (status >= 400)
                restResponse.isError = true;
        } catch (UnknownHostException e) {
            restResponse.isError = true;
            restResponse.response = "Unknown host: " + e.getMessage();
        } catch (Exception e) {
            restResponse.isError = true;
            restResponse.response = e.getMessage();
        }

        return restResponse;
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


    // Fix HttpURLConnection to allow PATCH http method
    private static void allowMethods(String... methods) {
        try {
            Field methodsField = HttpURLConnection.class.getDeclaredField("methods");

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(methodsField, methodsField.getModifiers() & ~Modifier.FINAL);

            methodsField.setAccessible(true);

            String[] oldMethods = (String[]) methodsField.get(null);
            Set<String> methodsSet = new LinkedHashSet<>(Arrays.asList(oldMethods));
            methodsSet.addAll(Arrays.asList(methods));
            String[] newMethods = methodsSet.toArray(new String[0]);

            methodsField.set(null/*static field*/, newMethods);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
