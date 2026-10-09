package com.mcelroy.salesforceconnector.rest;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class SFClientConnectionUpsertTest {
    private HttpServer server;
    private SFClientConnection client;
    private int status = 201;
    private String response = "{\"id\":\"001000000000001AAA\",\"created\":true}";
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
            exchange.sendResponseHeaders(code, code == 204 ? -1 : bytes.length);
            if (code != 204)
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

    @Test
    public void encodesExternalIdAsOnePathSegmentAndPreservesCreateResponse() {
        JSONObject result = client.upsert("Account", "External_Id__c", "a/b +?#%雪", "{\"Name\":\"Acme\"}");
        assertEquals("/services/data/v50.0/sobjects/Account/External_Id__c/"
                + "a%2Fb%20%2B%3F%23%25%E9%9B%AA?_HttpMethod=PATCH", requestUri);
        assertEquals("POST", requestMethod);
        assertEquals("{\"Name\":\"Acme\"}", requestBody);
        assertEquals("001000000000001AAA", result.getString("id"));
        assertTrue(result.getBoolean("created"));
    }

    @Test
    public void distinguishesCreateStatusWhenCreatedFlagIsAbsent() {
        response = "{\"id\":\"001000000000001AAA\"}";
        assertTrue(client.upsert("Account", "External_Id__c", "new", "{}").getBoolean("created"));
        status = 200;
        assertFalse(client.upsert("Account", "External_Id__c", "existing", "{}").getBoolean("created"));
    }

    @Test
    public void handlesOkAndNoContentUpdates() {
        status = 200;
        response = "{}";
        JSONObject result = client.upsert("Account", "Id", "001000000000001AAA", "{}");
        assertFalse(result.getBoolean("created"));
        assertFalse(result.has("id"));
        status = 204;
        response = "";
        result = client.upsert("Account", "Id", "001000000000001AAA", "{}");
        assertFalse(result.getBoolean("created"));
        assertFalse(result.has("id"));
    }

    @Test
    public void rejectsMultipleMatchesAndInvalidExternalIdField() {
        status = 300;
        response = "[\"/sobjects/Account/001000000000001AAA\",\"/sobjects/Account/001000000000002AAA\"]";
        try {
            client.upsert("Account", "External_Id__c", "ambiguous", "{}");
            fail("Accepted multiple matches");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("300"));
            assertTrue(expected.getMessage().contains("Multiple records matched"));
        }
        status = 404;
        response = "[{\"errorCode\":\"NOT_FOUND\",\"message\":\"Invalid external ID field\"}]";
        try {
            client.upsert("Account", "Missing__c", "key", "{}");
            fail("Accepted invalid field");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("404"));
            assertTrue(expected.getMessage().contains("Invalid external ID field"));
        }
    }
}
