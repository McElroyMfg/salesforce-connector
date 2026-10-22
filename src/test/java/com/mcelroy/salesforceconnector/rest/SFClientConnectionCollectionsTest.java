package com.mcelroy.salesforceconnector.rest;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class SFClientConnectionCollectionsTest {
    private HttpServer server;
    private SFClientConnection client;
    private int status = 200;
    private String response = "[{\"id\":\"001000000000001AAA\",\"success\":true,\"errors\":[]},"
            + "{\"success\":false,\"errors\":[{\"statusCode\":\"REQUIRED_FIELD_MISSING\",\"message\":\"Name\"}]}]";
    private String requestUri;
    private String requestBody;
    private String requestMethod;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            boolean login = exchange.getRequestURI().getPath().equals("/services/oauth2/token");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream input = exchange.getRequestBody()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1)
                    body.write(buffer, 0, count);
            }
            if (!login) {
                requestUri = exchange.getRequestURI().toASCIIString();
                requestBody = new String(body.toByteArray(), StandardCharsets.UTF_8);
                requestMethod = exchange.getRequestMethod();
            }
            byte[] bytes = (login ? "{\"access_token\":\"test-placeholder\"}" : response)
                    .getBytes(StandardCharsets.UTF_8);
            int code = login ? 200 : status;
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new SFClientConnection(new SFRestConnection.SFRestConfig(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-client", null, null, null));
    }

    @After
    public void tearDown() {
        if (server != null)
            server.stop(0);
    }

    private static JSONArray records() {
        return new JSONArray()
                .put(new JSONObject().put("External_Id__c", "a").put("Name", "Acme").put("Phone", JSONObject.NULL))
                .put(new JSONObject().put("External_Id__c", "b"));
    }

    @Test
    public void insertCollectionPostsTypedRecordsAndReturnsEveryResult() {
        JSONArray results = client.insertCollection("Account", records(), false);
        assertEquals("POST", requestMethod);
        assertEquals("/services/data/v50.0/composite/sobjects", requestUri);
        JSONObject body = new JSONObject(requestBody);
        assertFalse(body.getBoolean("allOrNone"));
        JSONArray sent = body.getJSONArray("records");
        assertEquals(2, sent.length());
        assertEquals("Account", sent.getJSONObject(0).getJSONObject("attributes").getString("type"));
        assertEquals("Acme", sent.getJSONObject(0).getString("Name"));
        assertTrue(sent.getJSONObject(0).isNull("Phone"));
        assertEquals(2, results.length());
        assertTrue(results.getJSONObject(0).getBoolean("success"));
        assertFalse(results.getJSONObject(1).getBoolean("success"));
    }

    @Test
    public void upsertCollectionPatchesByExternalIdAndKeepsKeyInBody() {
        client.upsertCollection("Account", "External_Id__c", records(), true);
        assertEquals("POST", requestMethod);
        assertEquals("/services/data/v50.0/composite/sobjects/Account/External_Id__c?_HttpMethod=PATCH", requestUri);
        JSONObject body = new JSONObject(requestBody);
        assertTrue(body.getBoolean("allOrNone"));
        assertEquals("a", body.getJSONArray("records").getJSONObject(0).getString("External_Id__c"));
        assertEquals("Account", body.getJSONArray("records").getJSONObject(1)
                .getJSONObject("attributes").getString("type"));
    }

    @Test
    public void httpErrorsRaiseWithDetails() {
        status = 400;
        response = "[{\"errorCode\":\"INVALID_FIELD\",\"message\":\"No such column\"}]";
        try {
            client.insertCollection("Account", records(), false);
            fail("Accepted HTTP error");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().startsWith("HTTP 400"));
            assertTrue(expected.getMessage().contains("INVALID_FIELD"));
        }
    }

    @Test
    public void rejectsMoreThanCollectionLimitWithoutCallingApi() {
        JSONArray many = new JSONArray();
        for (int i = 0; i <= SFClientConnection.COLLECTION_LIMIT; i++)
            many.put(new JSONObject().put("Name", "n" + i));
        try {
            client.insertCollection("Account", many, false);
            fail("Accepted oversized collection");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("at most 200"));
        }
        assertNull(requestUri);
    }
}
