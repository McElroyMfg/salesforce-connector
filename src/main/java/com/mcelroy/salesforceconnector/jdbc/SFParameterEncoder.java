// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;

import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;

final class SFParameterEncoder {
    private SFParameterEncoder() {
    }

    static String toSoql(Object value) {
        if (value == null || value == JSONObject.NULL)
            return "null";
        // Byte is quoted for compatibility with the original setByte -> setString routing.
        if (value instanceof Boolean || (value instanceof Number && !(value instanceof Byte)))
            return value.toString();
        if (isTemporal(value))
            return temporal(value);
        if (value instanceof byte[])
            return "'" + Base64.getEncoder().encodeToString((byte[]) value) + "'";
        return "'" + value.toString().replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    static Object toJson(Object value) {
        if (value == null || value == JSONObject.NULL)
            return JSONObject.NULL;
        if (value instanceof byte[])
            return Base64.getEncoder().encodeToString((byte[]) value);
        if (isTemporal(value))
            return temporal(value);
        if (value instanceof Map)
            return new JSONObject((Map<?, ?>) value);
        if (value instanceof Collection)
            return new JSONArray((Collection<?>) value);
        if (value instanceof Number || value instanceof Boolean || value instanceof String
                || value instanceof JSONObject || value instanceof JSONArray || value instanceof JSONString)
            return value;
        return value.toString();
    }

    private static boolean isTemporal(Object value) {
        return value instanceof java.sql.Date || value instanceof Time || value instanceof Timestamp;
    }

    private static String temporal(Object value) {
        return formatDate((java.util.Date) value,
                value instanceof java.sql.Date ? "yyyy-MM-dd" : "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    }

    static String formatDate(java.util.Date date, String fmt) {
        return new SimpleDateFormat(fmt).format(date);
    }
}
