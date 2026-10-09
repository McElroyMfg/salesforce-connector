// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.node.SQL_Call_Statement;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;

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
    private final List<String> inputNames = new ArrayList<>();
    private final String flowName;
    private JSONObject outParams;
    private final boolean positionalInputs;
    private boolean namesResolved;
    private boolean wasNull = true;
    private final String catalog;

    private int indexOf(String name) throws SQLException {
        if (name == null)
            throw new SQLException("Property name can not be null");
        resolveInputNames();
        int index = inputNames.indexOf(name);
        if (index < 0) {
            inputNames.add(name);
            index = inputNames.size() - 1;
        }
        return index + 1;
    }

    // Resolved once so indexes already bound by name stay stable for this statement.
    private void resolveInputNames() throws SQLException {
        if (namesResolved)
            return;
        if (positionalInputs || inputNames.isEmpty())
            inputNames.addAll(parameterNames("inputs"));
        namesResolved = true;
    }

    public SFCallableStatement(SFConnection sfConnection, SFClientConnection apiConnection, String sql) {
        super(sfConnection, apiConnection, sql);
        catalog = sfConnection.environment;
        if (!(getSqlStatement() instanceof SQL_Call_Statement))
            throw new IllegalArgumentException("Expected a CALL statement: " + sql);
        SQL_Call_Statement call = (SQL_Call_Statement) getSqlStatement();
        flowName = call.getFlowName();
        positionalInputs = call.isPositional();
        inputNames.addAll(call.getInputNames());
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
            JSONObject values = new JSONObject();
            for (Map.Entry<Integer, Object> entry : params.entrySet())
                values.put(inputNames.get(entry.getKey() - 1), SFParameterEncoder.toJson(entry.getValue()));
            return new JSONObject().put("inputs", new JSONArray().put(values)).toString();
        } catch (RuntimeException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public boolean execute() throws SQLException {
        outParams = null;
        wasNull = true;
        resolveInputNames();
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
        setURL(indexOf(s), url);
    }

    @Override
    public void setNull(String s, int i) throws SQLException {
        setNull(indexOf(s), i);
    }

    @Override
    public void setBoolean(String s, boolean b) throws SQLException {
        setBoolean(indexOf(s), b);
    }

    @Override
    public void setByte(String s, byte b) throws SQLException {
        setByte(indexOf(s), b);
    }

    @Override
    public void setShort(String s, short i) throws SQLException {
        setShort(indexOf(s), i);
    }

    @Override
    public void setInt(String s, int i) throws SQLException {
        setInt(indexOf(s), i);
    }

    @Override
    public void setLong(String s, long l) throws SQLException {
        setLong(indexOf(s), l);
    }

    @Override
    public void setFloat(String s, float v) throws SQLException {
        setFloat(indexOf(s), v);
    }

    @Override
    public void setDouble(String s, double v) throws SQLException {
        setDouble(indexOf(s), v);
    }

    @Override
    public void setBigDecimal(String s, BigDecimal bigDecimal) throws SQLException {
        setBigDecimal(indexOf(s), bigDecimal);
    }

    @Override
    public void setString(String s, String s1) throws SQLException {
        setString(indexOf(s), s1);
    }

    @Override
    public void setBytes(String s, byte[] bytes) throws SQLException {
        setBytes(indexOf(s), bytes);
    }

    @Override
    public void setDate(String s, Date date) throws SQLException {
        setDate(indexOf(s), date);
    }

    @Override
    public void setTime(String s, Time time) throws SQLException {
        setTime(indexOf(s), time);
    }

    @Override
    public void setTimestamp(String s, Timestamp timestamp) throws SQLException {
        setTimestamp(indexOf(s), timestamp);
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream, int i) throws SQLException {
        setAsciiStream(indexOf(s), inputStream, i);
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream, int i) throws SQLException {
        setBinaryStream(indexOf(s), inputStream, i);
    }

    @Override
    public void setObject(String s, Object o, int i, int i1) throws SQLException {
        setObject(indexOf(s), o, i, i1);
    }

    @Override
    public void setObject(String s, Object o, int i) throws SQLException {
        setObject(indexOf(s), o, i);
    }

    @Override
    public void setObject(String s, Object o) throws SQLException {
        setObject(indexOf(s), o);
    }

    @Override
    public void setCharacterStream(String s, Reader reader, int i) throws SQLException {
        setCharacterStream(indexOf(s), reader, i);
    }

    @Override
    public void setDate(String s, Date date, Calendar calendar) throws SQLException {
        setDate(indexOf(s), date, calendar);
    }

    @Override
    public void setTime(String s, Time time, Calendar calendar) throws SQLException {
        setTime(indexOf(s), time, calendar);
    }

    @Override
    public void setTimestamp(String s, Timestamp timestamp, Calendar calendar) throws SQLException {
        setTimestamp(indexOf(s), timestamp, calendar);
    }

    @Override
    public void setNull(String s, int i, String s1) throws SQLException {
        setNull(indexOf(s), i, s1);
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
        setRowId(indexOf(s), rowId);
    }

    @Override
    public void setNString(String s, String s1) throws SQLException {
        setNString(indexOf(s), s1);
    }

    @Override
    public void setNCharacterStream(String s, Reader reader, long l) throws SQLException {
        setNCharacterStream(indexOf(s), reader, l);
    }

    @Override
    public void setNClob(String s, NClob nClob) throws SQLException {
        setNClob(indexOf(s), nClob);
    }

    @Override
    public void setClob(String s, Reader reader, long l) throws SQLException {
        setClob(indexOf(s), reader, l);
    }

    @Override
    public void setBlob(String s, InputStream inputStream, long l) throws SQLException {
        setBlob(indexOf(s), inputStream, l);
    }

    @Override
    public void setNClob(String s, Reader reader, long l) throws SQLException {
        setNClob(indexOf(s), reader, l);
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
        setSQLXML(indexOf(s), sqlxml);
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
        setBlob(indexOf(s), blob);
    }

    @Override
    public void setClob(String s, Clob clob) throws SQLException {
        setClob(indexOf(s), clob);
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream, long l) throws SQLException {
        setAsciiStream(indexOf(s), inputStream, l);
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream, long l) throws SQLException {
        setBinaryStream(indexOf(s), inputStream, l);
    }

    @Override
    public void setCharacterStream(String s, Reader reader, long l) throws SQLException {
        setCharacterStream(indexOf(s), reader, l);
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream) throws SQLException {
        setAsciiStream(indexOf(s), inputStream);
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream) throws SQLException {
        setBinaryStream(indexOf(s), inputStream);
    }

    @Override
    public void setCharacterStream(String s, Reader reader) throws SQLException {
        setCharacterStream(indexOf(s), reader);
    }

    @Override
    public void setNCharacterStream(String s, Reader reader) throws SQLException {
        setNCharacterStream(indexOf(s), reader);
    }

    @Override
    public void setClob(String s, Reader reader) throws SQLException {
        setClob(indexOf(s), reader);
    }

    @Override
    public void setBlob(String s, InputStream inputStream) throws SQLException {
        setBlob(indexOf(s), inputStream);
    }

    @Override
    public void setNClob(String s, Reader reader) throws SQLException {
        setNClob(indexOf(s), reader);
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
