// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class SFMetadataCache {
    private static final class Entry {
        final JSONObject value;
        final long createdAt;

        Entry(JSONObject value) {
            this.value = value;
            this.createdAt = System.nanoTime();
        }
    }

    private final long ttlNanos;
    private final Map<String, Entry> entries;

    public SFMetadataCache(long ttlMillis, int maxEntries) {
        if (maxEntries <= 0)
            throw new IllegalArgumentException("metadataCacheMaxEntries must be positive");
        ttlNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(ttlMillis);
        entries = Collections.synchronizedMap(new LinkedHashMap<String, Entry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > maxEntries;
            }
        });
    }

    public JSONObject get(String key, Supplier<JSONObject> loader) {
        if (ttlNanos <= 0)
            return loader.get();
        String normalized = key.toLowerCase(Locale.ROOT);
        synchronized (entries) {
            Entry entry = entries.get(normalized);
            if (entry != null && System.nanoTime() - entry.createdAt < ttlNanos)
                return entry.value;
            entries.remove(normalized);
            JSONObject value = loader.get();
            entries.put(normalized, new Entry(value));
            return value;
        }
    }

    public void evict(String key) {
        entries.remove(key.toLowerCase(Locale.ROOT));
    }

    public void clear() {
        entries.clear();
    }
}
