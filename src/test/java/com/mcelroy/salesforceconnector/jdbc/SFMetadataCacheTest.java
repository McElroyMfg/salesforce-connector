// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import org.json.JSONObject;
import org.junit.Test;

import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class SFMetadataCacheTest {
    @Test
    public void cachesCaseInsensitiveKeysAndExpires() throws Exception {
        SFMetadataCache cache = new SFMetadataCache(100, 5);
        AtomicInteger loads = new AtomicInteger();
        Supplier<JSONObject> loader = () -> new JSONObject().put("load", loads.incrementAndGet());
        JSONObject first = cache.get("sobject:Account", loader);
        assertSame(first, cache.get("SOBJECT:ACCOUNT", loader));
        Thread.sleep(150);
        assertNotSame(first, cache.get("sobject:account", loader));
        assertEquals(2, loads.get());
    }

    @Test
    public void evictsLeastRecentlyUsedEntry() {
        SFMetadataCache cache = new SFMetadataCache(10000, 2);
        JSONObject a = cache.get("a", JSONObject::new);
        JSONObject b = cache.get("b", JSONObject::new);
        assertSame(a, cache.get("a", JSONObject::new));
        cache.get("c", JSONObject::new);
        assertSame(a, cache.get("a", JSONObject::new));
        assertNotSame(b, cache.get("b", JSONObject::new));
    }

    @Test
    public void disabledCacheAlwaysLoads() {
        for (int ttl : new int[]{0, -1}) {
            SFMetadataCache cache = new SFMetadataCache(ttl, 5);
            assertNotSame(cache.get("a", JSONObject::new), cache.get("a", JSONObject::new));
        }
    }

    @Test
    public void evictAndClearResetEntries() {
        SFMetadataCache cache = new SFMetadataCache(10000, 5);
        JSONObject a = cache.get("a", JSONObject::new);
        JSONObject b = cache.get("b", JSONObject::new);
        cache.evict("A");
        assertNotSame(a, cache.get("a", JSONObject::new));
        assertSame(b, cache.get("b", JSONObject::new));
        cache.clear();
        assertNotSame(b, cache.get("b", JSONObject::new));
    }

    @Test
    public void connectionResetsAndScopesCache() throws Exception {
        SFConnection connection = new SFConnection(null, "demo");
        SFMetadataCache cache = connection.getMetadataCache();
        JSONObject first = cache.get("global", JSONObject::new);
        assertNotSame(first, new SFConnection(null, "demo").getMetadataCache().get("global", JSONObject::new));
        connection.close();
        JSONObject second = cache.get("global", JSONObject::new);
        assertNotSame(first, second);
        connection.setCatalog("other");
        JSONObject third = cache.get("global", JSONObject::new);
        assertNotSame(second, third);
        assertEquals("other", connection.getCatalog());
        assertTrue(connection.isWrapperFor(SFConnection.class));
        assertSame(connection, connection.unwrap(SFConnection.class));
        connection.unwrap(SFConnection.class).clearMetadataCache();
        assertNotSame(third, cache.get("global", JSONObject::new));
        assertNotSame(connection.getMetaData(), connection.getMetaData());
        assertSame(connection, connection.getMetaData().getConnection());
    }

    @Test
    public void driverPassesCacheProperties() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("clientId", "test-client");
        properties.setProperty("clientSecret", "test-placeholder");
        properties.setProperty("metadataCacheTtlSeconds", "0");
        properties.setProperty("metadataCacheMaxEntries", "2");
        SFConnection connection = (SFConnection) new SFDriver().connect("jdbc:sf:https://example.invalid/demo", properties);
        assertNotSame(connection.getMetadataCache().get("global", JSONObject::new),
                connection.getMetadataCache().get("global", JSONObject::new));
        properties.setProperty("metadataCacheTtlSeconds", "300");
        SFConnection cached = (SFConnection) new SFDriver().connect("jdbc:sf:https://example.invalid/demo", properties);
        JSONObject first = cached.getMetadataCache().get("a", JSONObject::new);
        cached.getMetadataCache().get("b", JSONObject::new);
        cached.getMetadataCache().get("c", JSONObject::new);
        assertNotSame(first, cached.getMetadataCache().get("a", JSONObject::new));
        DriverPropertyInfo[] info = new SFDriver().getPropertyInfo("jdbc:sf:https://example.invalid", new Properties());
        assertEquals("metadataCacheTtlSeconds", info[5].name);
        assertEquals("300", info[5].value);
        assertEquals("metadataCacheMaxEntries", info[6].name);
        assertEquals("500", info[6].value);
    }

    @Test
    public void driverRejectsInvalidProperties() throws Exception {
        for (String[] setting : new String[][]{
                {"metadataCacheTtlSeconds", "invalid"}, {"metadataCacheMaxEntries", "0"},
                {"metadataCacheMaxEntries", "-1"}, {"metadataCacheMaxEntries", "invalid"}}) {
            Properties properties = new Properties();
            properties.setProperty("clientId", "test-client");
            properties.setProperty("clientSecret", "test-placeholder");
            properties.setProperty(setting[0], setting[1]);
            try {
                new SFDriver().connect("jdbc:sf:https://example.invalid", properties);
                fail("Expected invalid cache property to fail");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage().contains("metadata cache"));
            }
        }
    }
}
