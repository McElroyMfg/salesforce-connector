// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.sql.Date;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SFCallableStatement extends SFPreparedStatement implements CallableStatement {
    List<String> inputNames = new ArrayList<>();
    String flowName;
    JSONObject outParams;
    private boolean positionalInputs;
    private boolean inferredInputs;
    private boolean wasNull = true;
    private final Map<String, String> namedParams = new LinkedHashMap<>();
    private final Map<String, Long> namedBindingOrder = new HashMap<>();
    private final Map<Integer, Long> positionalBindingOrder = new HashMap<>();
    private long bindingOrder;
    private final String catalog;

    private interface ParameterBinding {
        void bind() throws SQLException;
    }

    private void bindNamed(String name, ParameterBinding binding) throws SQLException {
        if (name == null)
            throw new SQLException("Property name can not be null");
        Map<Integer, String> positionalParams = params;
        // Reuse the indexed setter's encoding without changing positional bindings.
        params = new HashMap<>();
        try {
            binding.bind();
            namedParams.put(name, params.get(1));
            namedBindingOrder.put(name, ++bindingOrder);
        } finally {
            params = positionalParams;
        }
    }

    @Override
    public void clearParameters() throws SQLException {
        super.clearParameters();
        namedParams.clear();
        namedBindingOrder.clear();
        positionalBindingOrder.clear();
    }

    public SFCallableStatement(SFConnection sfConnection, SFClientConnection apiConnection, String sql) {
        super(sfConnection, apiConnection, "call");
        catalog = sfConnection.environment;
        params = new HashMap<Integer, String>() {
            @Override
            public String put(Integer index, String value) {
                positionalBindingOrder.put(index, ++bindingOrder);
                return super.put(index, value);
            }
        };
        sql = sql.trim();
        int callIdx = sql.toLowerCase(Locale.ROOT).indexOf("call ");
        if (callIdx < 0)
            throw new RuntimeException("Missing call keyword");
        callIdx += 5;

        int openParenIdx = sql.indexOf("(", callIdx);
        if (openParenIdx < 0) {
            flowName = sql.substring(callIdx).trim();
            if (flowName.endsWith("}"))
                flowName = flowName.substring(0, flowName.length() - 1).trim();
        } else {
            flowName = sql.substring(callIdx, openParenIdx).trim();

            int closeParenIdx = sql.indexOf(")", openParenIdx);
            if (closeParenIdx < 0)
                throw new RuntimeException("Missing closing ) for call parameter list");
            String ps = sql.substring(openParenIdx + 1, closeParenIdx).trim();
            if (!ps.isEmpty()) {
                String[] paramNames = ps.split(",");
                positionalInputs = Arrays.stream(paramNames).allMatch(p -> p.trim().equals("?"));
                if (!positionalInputs)
                    for (String name : paramNames)
                        inputNames.add(name.trim());
            }
        }
    }

    private JSONObject describeFlow() throws SQLException {
        try {
            SFConnection connection = (SFConnection) getConnection();
            if (!Objects.equals(catalog, connection.environment))
                return getApiConnection().describeFlow(flowName);
            return connection.getMetadataCache()
                    .get("flow:" + flowName, () -> getApiConnection().describeFlow(flowName));
        } catch (RuntimeException e) {
            throw new SQLException("Could not describe flow " + flowName, e);
        }
    }

    private void evictFlowDescription() throws SQLException {
        SFConnection connection = (SFConnection) getConnection();
        if (Objects.equals(catalog, connection.environment))
            connection.getMetadataCache().evict("flow:" + flowName);
    }

    private List<String> parameterNames(String direction) throws SQLException {
        try {
            List<String> names = new ArrayList<>();
            JSONObject description = describeFlow();
            if (description == null)
                return names;
            JSONArray parameters = description.optJSONArray(direction);
            if (parameters == null)
                parameters = description.optJSONArray(direction.equals("inputs")
                        ? "inputParameters" : "outputParameters");
            if (parameters != null)
                for (int i = 0; i < parameters.length(); i++)
                    names.add(parameters.getJSONObject(i).getString("name"));
            return names;
        } catch (RuntimeException e) {
            throw new SQLException("Invalid flow " + direction + " metadata", e);
        }
    }

    private String outputName(int index) throws SQLException {
        if (index < 1)
            throw new SQLException("Invalid output parameter index: " + index);
        List<String> names = parameterNames("outputs");
        if (index > names.size())
            throw new SQLException("Invalid output parameter index: " + index);
        return names.get(index - 1);
    }

    private Object outputValue(String name) throws SQLException {
        if (name == null)
            throw new SQLException("Output parameter name cannot be null");
        Object value = outParams == null ? null : outParams.opt(name);
        wasNull = value == null || value == JSONObject.NULL;
        return wasNull ? null : value;
    }

    private String parseBody() throws SQLException {
        try {
            JSONObject body = new JSONObject();
            JSONArray inputs = new JSONArray();
            body.put("inputs", inputs);
            JSONObject values = new JSONObject();
            inputs.put(values);
            for (int i = 0; i < inputNames.size(); i++) {
                String key = inputNames.get(i);
                String value = params.get(i + 1);
                if (value != null) {
                    JSONString js = new JSONString() {
                        @Override
                        public String toJSONString() {
                            return value;
                        }
                    };
                    values.put(key, js);
                }
            }
            for (Map.Entry<String, String> entry : namedParams.entrySet()) {
                int index = inputNames.indexOf(entry.getKey()) + 1;
                if (positionalBindingOrder.getOrDefault(index, 0L)
                        > namedBindingOrder.get(entry.getKey()))
                    continue;
                String value = entry.getValue();
                if (value != null)
                    values.put(entry.getKey(), (JSONString) () -> value);
            }

            return body.toString();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public boolean execute() throws SQLException {
        outParams = null;
        wasNull = true;
        if (positionalInputs || inferredInputs || inputNames.isEmpty()) {
            inferredInputs = true;
            inputNames.clear();
            inputNames.addAll(parameterNames("inputs"));
        }
        for (Integer index : params.keySet())
            if (index < 1 || index > inputNames.size())
                throw new SQLException("Invalid input parameter index: " + index);
        String body = parseBody();
        try {
            JSONObject response = getApiConnection().launchFlow(flowName, body);
            if (!response.optBoolean("isSuccess", true)) {
                String err = response.optString("errors", "Error calling procedure");
                evictFlowDescription();
                throw new SQLException(err);
            }
            outParams = response.optJSONObject("outputValues");
        } catch (RuntimeException e) {
            evictFlowDescription();
            throw new SQLException("Error calling flow " + flowName, e);
        }
        return false;
    }

    @Override
    public void setString(int i, String s) throws SQLException {
        params.put(i, JSONObject.quote(s));
    }

    @Override
    public void setObject(int i, Object o) throws SQLException {
        if (Collection.class.isInstance(o)) {
            Collection c = (Collection) o;
            JSONArray a = new JSONArray();
            for (Object x : c) {
                a.put(x);
            }
            params.put(i, a.toString());
        } else if (o instanceof Map) {
            JSONObject obj = new JSONObject((Map) o);
            params.put(i, obj.toString());
        } else {
            JSONObject obj = new JSONObject(o);
            params.put(i, obj.toString());
        }
    }

    @Override
    public void registerOutParameter(int i, int i1) throws SQLException {

    }

    @Override
    public void registerOutParameter(int i, int i1, int i2) throws SQLException {

    }

    @Override
    public boolean wasNull() throws SQLException {
        return wasNull;
    }

    @Override
    public String getString(int i) throws SQLException {
        return getString(outputName(i));
    }

    @Override
    public boolean getBoolean(int i) throws SQLException {
        return getBoolean(outputName(i));
    }

    @Override
    public byte getByte(int i) throws SQLException {
        return getByte(outputName(i));
    }

    @Override
    public short getShort(int i) throws SQLException {
        return getShort(outputName(i));
    }

    @Override
    public int getInt(int i) throws SQLException {
        return getInt(outputName(i));
    }

    @Override
    public long getLong(int i) throws SQLException {
        return getLong(outputName(i));
    }

    @Override
    public float getFloat(int i) throws SQLException {
        return getFloat(outputName(i));
    }

    @Override
    public double getDouble(int i) throws SQLException {
        return getDouble(outputName(i));
    }

    @Override
    public BigDecimal getBigDecimal(int i, int i1) throws SQLException {
        BigDecimal value = getBigDecimal(i);
        return value == null ? null : value.setScale(i1, RoundingMode.HALF_UP);
    }

    @Override
    public byte[] getBytes(int i) throws SQLException {
        return getBytes(outputName(i));
    }

    @Override
    public Date getDate(int i) throws SQLException {
        return getDate(outputName(i));
    }

    @Override
    public Time getTime(int i) throws SQLException {
        return getTime(outputName(i));
    }

    @Override
    public Timestamp getTimestamp(int i) throws SQLException {
        return getTimestamp(outputName(i));
    }

    @Override
    public Object getObject(int i) throws SQLException {
        return getObject(outputName(i));
    }

    @Override
    public BigDecimal getBigDecimal(int i) throws SQLException {
        return getBigDecimal(outputName(i));
    }

    @Override
    public Object getObject(int i, Map<String, Class<?>> map) throws SQLException {
        return getObject(outputName(i), map);
    }

    @Override
    public Ref getRef(int i) throws SQLException {
        return getRef(outputName(i));
    }

    @Override
    public Blob getBlob(int i) throws SQLException {
        return getBlob(outputName(i));
    }

    @Override
    public Clob getClob(int i) throws SQLException {
        return getClob(outputName(i));
    }

    @Override
    public Array getArray(int i) throws SQLException {
        return getArray(outputName(i));
    }

    @Override
    public Date getDate(int i, Calendar calendar) throws SQLException {
        return getDate(outputName(i), calendar);
    }

    @Override
    public Time getTime(int i, Calendar calendar) throws SQLException {
        return getTime(outputName(i), calendar);
    }

    @Override
    public Timestamp getTimestamp(int i, Calendar calendar) throws SQLException {
        return getTimestamp(outputName(i), calendar);
    }

    @Override
    public void registerOutParameter(int i, int i1, String s) throws SQLException {

    }

    @Override
    public void registerOutParameter(String s, int i) throws SQLException {

    }

    @Override
    public void registerOutParameter(String s, int i, int i1) throws SQLException {

    }

    @Override
    public void registerOutParameter(String s, int i, String s1) throws SQLException {

    }

    @Override
    public URL getURL(int i) throws SQLException {
        return getURL(outputName(i));
    }

    @Override
    public void setURL(String s, URL url) throws SQLException {
        bindNamed(s, () -> setURL(1, url));
    }

    @Override
    public void setNull(String s, int i) throws SQLException {
        bindNamed(s, () -> setNull(1, i));
    }

    @Override
    public void setBoolean(String s, boolean b) throws SQLException {
        bindNamed(s, () -> setBoolean(1, b));
    }

    @Override
    public void setByte(String s, byte b) throws SQLException {
        bindNamed(s, () -> setByte(1, b));
    }

    @Override
    public void setShort(String s, short i) throws SQLException {
        bindNamed(s, () -> setShort(1, i));
    }

    @Override
    public void setInt(String s, int i) throws SQLException {
        bindNamed(s, () -> setInt(1, i));
    }

    @Override
    public void setLong(String s, long l) throws SQLException {
        bindNamed(s, () -> setLong(1, l));
    }

    @Override
    public void setFloat(String s, float v) throws SQLException {
        bindNamed(s, () -> setFloat(1, v));
    }

    @Override
    public void setDouble(String s, double v) throws SQLException {
        bindNamed(s, () -> setDouble(1, v));
    }

    @Override
    public void setBigDecimal(String s, BigDecimal bigDecimal) throws SQLException {
        bindNamed(s, () -> setBigDecimal(1, bigDecimal));
    }

    @Override
    public void setString(String s, String s1) throws SQLException {
        bindNamed(s, () -> setString(1, s1));
    }

    @Override
    public void setBytes(String s, byte[] bytes) throws SQLException {
        bindNamed(s, () -> setBytes(1, bytes));
    }

    @Override
    public void setDate(String s, Date date) throws SQLException {
        bindNamed(s, () -> setDate(1, date));
    }

    @Override
    public void setTime(String s, Time time) throws SQLException {
        bindNamed(s, () -> setTime(1, time));
    }

    @Override
    public void setTimestamp(String s, Timestamp timestamp) throws SQLException {
        bindNamed(s, () -> setTimestamp(1, timestamp));
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream, int i) throws SQLException {
        bindNamed(s, () -> setAsciiStream(1, inputStream, i));
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream, int i) throws SQLException {
        bindNamed(s, () -> setBinaryStream(1, inputStream, i));
    }

    @Override
    public void setObject(String s, Object o, int i, int i1) throws SQLException {
        bindNamed(s, () -> setObject(1, o, i, i1));
    }

    @Override
    public void setObject(String s, Object o, int i) throws SQLException {
        bindNamed(s, () -> setObject(1, o, i));
    }

    @Override
    public void setObject(String s, Object o) throws SQLException {
        bindNamed(s, () -> setObject(1, o));
    }

    @Override
    public void setCharacterStream(String s, Reader reader, int i) throws SQLException {
        bindNamed(s, () -> setCharacterStream(1, reader, i));
    }

    @Override
    public void setDate(String s, Date date, Calendar calendar) throws SQLException {
        bindNamed(s, () -> setDate(1, date, calendar));
    }

    @Override
    public void setTime(String s, Time time, Calendar calendar) throws SQLException {
        bindNamed(s, () -> setTime(1, time, calendar));
    }

    @Override
    public void setTimestamp(String s, Timestamp timestamp, Calendar calendar) throws SQLException {
        bindNamed(s, () -> setTimestamp(1, timestamp, calendar));
    }

    @Override
    public void setNull(String s, int i, String s1) throws SQLException {
        bindNamed(s, () -> setNull(1, i, s1));
    }

    @Override
    public String getString(String s) throws SQLException {
        Object value = outputValue(s);
        return value == null ? null : value.toString();
    }

    @Override
    public boolean getBoolean(String s) throws SQLException {
        String value = getString(s);
        return value != null && (value.trim().equalsIgnoreCase("true") || value.trim().equals("1"));
    }

    @Override
    public byte getByte(String s) throws SQLException {
        return (byte) integralValue(s, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    @Override
    public short getShort(String s) throws SQLException {
        return (short) integralValue(s, Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override
    public int getInt(String s) throws SQLException {
        return (int) integralValue(s, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public long getLong(String s) throws SQLException {
        return integralValue(s, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private long integralValue(String name, long minimum, long maximum) throws SQLException {
        BigDecimal value = getBigDecimal(name);
        if (value == null)
            return 0;
        BigDecimal truncated = value.setScale(0, RoundingMode.DOWN);
        if (truncated.compareTo(BigDecimal.valueOf(minimum)) < 0
                || truncated.compareTo(BigDecimal.valueOf(maximum)) > 0)
            throw new SQLException("Numeric output is out of range: " + name);
        return truncated.longValue();
    }

    @Override
    public float getFloat(String s) throws SQLException {
        return (float) getDouble(s);
    }

    @Override
    public double getDouble(String s) throws SQLException {
        String value = getString(s);
        try {
            return value == null ? 0 : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid numeric output: " + s, e);
        }
    }

    @Override
    public byte[] getBytes(String s) throws SQLException {
        String value = getString(s);
        return value == null ? null : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public Date getDate(String s) throws SQLException {
        return getDate(s, null);
    }

    @Override
    public Time getTime(String s) throws SQLException {
        return getTime(s, null);
    }

    @Override
    public Timestamp getTimestamp(String s) throws SQLException {
        return getTimestamp(s, null);
    }

    @Override
    public Object getObject(String s) throws SQLException {
        return outputValue(s);
    }

    @Override
    public BigDecimal getBigDecimal(String s) throws SQLException {
        String value = getString(s);
        try {
            return value == null ? null : new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid numeric output: " + s, e);
        }
    }

    @Override
    public Object getObject(String s, Map<String, Class<?>> map) throws SQLException {
        if (map != null && !map.isEmpty())
            throw new SQLFeatureNotSupportedException("Custom output type mappings are not supported");
        return getObject(s);
    }

    @Override
    public Ref getRef(String s) throws SQLException {
        return getObject(s, Ref.class);
    }

    @Override
    public Blob getBlob(String s) throws SQLException {
        return getObject(s, Blob.class);
    }

    @Override
    public Clob getClob(String s) throws SQLException {
        return getObject(s, Clob.class);
    }

    @Override
    public Array getArray(String s) throws SQLException {
        return getObject(s, Array.class);
    }

    @Override
    public Date getDate(String s, Calendar calendar) throws SQLException {
        String value = getString(s);
        if (value == null)
            return null;
        try {
            LocalDate date = LocalDate.parse(value);
            return new Date(date.atStartOfDay(zone(calendar)).toInstant().toEpochMilli());
        } catch (DateTimeException e) {
            throw new SQLException("Invalid date output: " + s, e);
        }
    }

    @Override
    public Time getTime(String s, Calendar calendar) throws SQLException {
        String value = getString(s);
        if (value == null)
            return null;
        try {
            if (value.contains("T"))
                return new Time(timestamp(value, calendar).getTime());
            String normalized = value.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
            if (normalized.endsWith("Z") || normalized.matches(".*[+-]\\d{2}:\\d{2}$"))
                return new Time(OffsetTime.parse(normalized).atDate(LocalDate.of(1970, 1, 1))
                        .toInstant().toEpochMilli());
            return new Time(LocalTime.parse(normalized).atDate(LocalDate.of(1970, 1, 1))
                    .atZone(zone(calendar)).toInstant().toEpochMilli());
        } catch (DateTimeException e) {
            throw new SQLException("Invalid time output: " + s, e);
        }
    }

    @Override
    public Timestamp getTimestamp(String s, Calendar calendar) throws SQLException {
        String value = getString(s);
        if (value == null)
            return null;
        try {
            return timestamp(value, calendar);
        } catch (DateTimeException e) {
            throw new SQLException("Invalid timestamp output: " + s, e);
        }
    }

    private ZoneId zone(Calendar calendar) {
        return calendar == null ? ZoneId.systemDefault() : calendar.getTimeZone().toZoneId();
    }

    private Timestamp timestamp(String value, Calendar calendar) {
        String normalized = value.replace(' ', 'T').replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
        if (normalized.endsWith("Z") || normalized.matches(".*[+-]\\d{2}:\\d{2}$"))
            return Timestamp.from(OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
        return Timestamp.from(LocalDateTime.parse(normalized).atZone(zone(calendar)).toInstant());
    }

    @Override
    public URL getURL(String s) throws SQLException {
        String value = getString(s);
        try {
            return value == null ? null : new URL(value);
        } catch (java.net.MalformedURLException e) {
            throw new SQLException("Invalid URL output: " + s, e);
        }
    }

    @Override
    public RowId getRowId(int i) throws SQLException {
        return getRowId(outputName(i));
    }

    @Override
    public RowId getRowId(String s) throws SQLException {
        return getObject(s, RowId.class);
    }

    @Override
    public void setRowId(String s, RowId rowId) throws SQLException {
        bindNamed(s, () -> setRowId(1, rowId));
    }

    @Override
    public void setNString(String s, String s1) throws SQLException {
        bindNamed(s, () -> setNString(1, s1));
    }

    @Override
    public void setNCharacterStream(String s, Reader reader, long l) throws SQLException {
        bindNamed(s, () -> setNCharacterStream(1, reader, l));
    }

    @Override
    public void setNClob(String s, NClob nClob) throws SQLException {
        bindNamed(s, () -> setNClob(1, nClob));
    }

    @Override
    public void setClob(String s, Reader reader, long l) throws SQLException {
        bindNamed(s, () -> setClob(1, reader, l));
    }

    @Override
    public void setBlob(String s, InputStream inputStream, long l) throws SQLException {
        bindNamed(s, () -> setBlob(1, inputStream, l));
    }

    @Override
    public void setNClob(String s, Reader reader, long l) throws SQLException {
        bindNamed(s, () -> setNClob(1, reader, l));
    }

    @Override
    public NClob getNClob(int i) throws SQLException {
        return getNClob(outputName(i));
    }

    @Override
    public NClob getNClob(String s) throws SQLException {
        return getObject(s, NClob.class);
    }

    @Override
    public void setSQLXML(String s, SQLXML sqlxml) throws SQLException {
        bindNamed(s, () -> setSQLXML(1, sqlxml));
    }

    @Override
    public SQLXML getSQLXML(int i) throws SQLException {
        return getSQLXML(outputName(i));
    }

    @Override
    public SQLXML getSQLXML(String s) throws SQLException {
        return getObject(s, SQLXML.class);
    }

    @Override
    public String getNString(int i) throws SQLException {
        return getNString(outputName(i));
    }

    @Override
    public String getNString(String s) throws SQLException {
        return getString(s);
    }

    @Override
    public Reader getNCharacterStream(int i) throws SQLException {
        return getNCharacterStream(outputName(i));
    }

    @Override
    public Reader getNCharacterStream(String s) throws SQLException {
        return getCharacterStream(s);
    }

    @Override
    public Reader getCharacterStream(int i) throws SQLException {
        return getCharacterStream(outputName(i));
    }

    @Override
    public Reader getCharacterStream(String s) throws SQLException {
        String value = getString(s);
        return value == null ? null : new StringReader(value);
    }

    @Override
    public void setBlob(String s, Blob blob) throws SQLException {
        bindNamed(s, () -> setBlob(1, blob));
    }

    @Override
    public void setClob(String s, Clob clob) throws SQLException {
        bindNamed(s, () -> setClob(1, clob));
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream, long l) throws SQLException {
        bindNamed(s, () -> setAsciiStream(1, inputStream, l));
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream, long l) throws SQLException {
        bindNamed(s, () -> setBinaryStream(1, inputStream, l));
    }

    @Override
    public void setCharacterStream(String s, Reader reader, long l) throws SQLException {
        bindNamed(s, () -> setCharacterStream(1, reader, l));
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream) throws SQLException {
        bindNamed(s, () -> setAsciiStream(1, inputStream));
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream) throws SQLException {
        bindNamed(s, () -> setBinaryStream(1, inputStream));
    }

    @Override
    public void setCharacterStream(String s, Reader reader) throws SQLException {
        bindNamed(s, () -> setCharacterStream(1, reader));
    }

    @Override
    public void setNCharacterStream(String s, Reader reader) throws SQLException {
        bindNamed(s, () -> setNCharacterStream(1, reader));
    }

    @Override
    public void setClob(String s, Reader reader) throws SQLException {
        bindNamed(s, () -> setClob(1, reader));
    }

    @Override
    public void setBlob(String s, InputStream inputStream) throws SQLException {
        bindNamed(s, () -> setBlob(1, inputStream));
    }

    @Override
    public void setNClob(String s, Reader reader) throws SQLException {
        bindNamed(s, () -> setNClob(1, reader));
    }

    @Override
    public <T> T getObject(int i, Class<T> aClass) throws SQLException {
        return getObject(outputName(i), aClass);
    }

    @Override
    public <T> T getObject(String s, Class<T> aClass) throws SQLException {
        if (aClass == null)
            throw new SQLException("Output type cannot be null");
        Object value = getObject(s);
        if (value == null)
            return null;
        if (aClass.isInstance(value))
            return aClass.cast(value);
        if (aClass == String.class) value = getString(s);
        else if (aClass == Boolean.class || aClass == boolean.class) value = getBoolean(s);
        else if (aClass == Byte.class || aClass == byte.class) value = getByte(s);
        else if (aClass == Short.class || aClass == short.class) value = getShort(s);
        else if (aClass == Integer.class || aClass == int.class) value = getInt(s);
        else if (aClass == Long.class || aClass == long.class) value = getLong(s);
        else if (aClass == Float.class || aClass == float.class) value = getFloat(s);
        else if (aClass == Double.class || aClass == double.class) value = getDouble(s);
        else if (aClass == BigDecimal.class) value = getBigDecimal(s);
        else if (aClass == Date.class) value = getDate(s);
        else if (aClass == Time.class) value = getTime(s);
        else if (aClass == Timestamp.class) value = getTimestamp(s);
        else if (aClass == byte[].class) value = getBytes(s);
        else if (aClass == URL.class) value = getURL(s);
        else throw new SQLException("Unsupported output type: " + aClass.getName());
        @SuppressWarnings("unchecked")
        T converted = (T) value;
        return converted;
    }
}
