package com.mcelroy.salesforceconnector.rest;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SFRestConnectionAuthFailureTest {
    private HttpServer server;
    private final AtomicInteger logins = new AtomicInteger();
    private SFClientConnection client;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().close();
            int code;
            String body;
            if (exchange.getRequestURI().getPath().equals("/services/oauth2/token")) {
                if (logins.getAndIncrement() == 0) {
                    code = 200;
                    body = "{\"access_token\":\"test-placeholder\"}";
                } else {
                    code = 400;
                    body = "{\"error\":\"invalid_grant\",\"error_description\":\"expired access/refresh token\"}";
                }
            } else {
                code = 401;
                body = "[{\"message\":\"Session expired or invalid\",\"errorCode\":\"INVALID_SESSION_ID\"}]";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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

    @Test
    public void repeatedUnauthorizedReportsResponseAndLoginFailure() {
        JSONArray records = new JSONArray().put(new JSONObject().put("Name", "a"));
        try {
            client.upsertCollection("Account", "Name", records, false);
            fail("Accepted 401");
        } catch (RuntimeException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage(), e.getMessage().startsWith("HTTP 401"));
            assertTrue(e.getMessage(), e.getMessage().contains("INVALID_SESSION_ID"));
            assertTrue(e.getMessage(), e.getMessage().contains("re-login failed: HTTP 400"));
            assertTrue(e.getMessage(), e.getMessage().contains("invalid_grant"));
        }
        assertTrue(logins.get() >= 2);
    }
}
