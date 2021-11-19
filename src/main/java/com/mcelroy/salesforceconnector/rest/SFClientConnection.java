// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.rest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SFClientConnection {
    private final SFRestConnection connection;

    public interface QueryResultProcessor {
        void processResult(JSONObject r);
    }

    public interface QueryResultMapper<E> {
        E map(JSONObject r);
    }


    public SFClientConnection(SFRestConnection.SFRestConfig config) {
        this.connection = new SFRestConnection(config);
    }


    public JSONObject get(String object) {
        try {
            return connection.getJSON(connection.getServiceUrl() + "sobjects/" + object, null);
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
            return connection.getJSON(connection.getServiceUrl() + "query", params);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public JSONObject queryNext(String service) {
        try {
            return connection.getJSON(connection.getHost() + service, null);
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
            return connection.postJSON(connection.getServiceUrl() + "sobjects/" + object, statement);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public JSONObject launchFlow(String flowName, String statement) {
        try {
            return connection.postJSON(connection.getServiceUrl() + "actions/custom/flow/" + flowName, statement);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public String batchQuery(String query) {
        try {
            JSONObject job = connection.postJSON(connection.getHost() + "jobs/query", "{\"operation\": \"query\", \"query\": \"" + query + "\"}");
            String id = job.getString("id");
            // TODO: check job status
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
