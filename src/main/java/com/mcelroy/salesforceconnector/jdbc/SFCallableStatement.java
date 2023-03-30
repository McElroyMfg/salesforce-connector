// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Date;
import java.sql.*;
import java.util.*;

public class SFCallableStatement extends SFPreparedStatement implements CallableStatement {
    List<String> inputNames = new ArrayList<>();
    String flowName;

    public SFCallableStatement(SFConnection sfConnection, SFClientConnection apiConnection, String sql) {
        super(sfConnection, apiConnection, sql);
        int callIdx = sql.indexOf("call ");
        if (callIdx < 0)
            throw new RuntimeException("Missing call keyword");
        callIdx += 5;

        int openParenIdx = sql.indexOf("(", callIdx);
        if (openParenIdx < 0) {
            flowName = sql.substring(callIdx).trim();
            if (flowName.endsWith("}"))
                flowName = flowName.substring(0, flowName.length() - 1);
        } else {
            flowName = sql.substring(callIdx, openParenIdx).trim();

            int closeParenIdx = sql.indexOf(")", openParenIdx);
            if (closeParenIdx < 0)
                throw new RuntimeException("Missing closing ) for call parameter list");
            String ps = sql.substring(openParenIdx + 1, closeParenIdx);
            String[] paramNames = ps.split(",");
            for (int i = 0; i < paramNames.length; i++)
                inputNames.add(paramNames[i].trim());
        }
    }

    private int getIndex(String s) {
        if (s == null)
            throw new RuntimeException("Property name can not be null");
        for (int i = 0; i < inputNames.size(); i++) {
            String key = inputNames.get(i);
            if (key.equals(s))
                return i + 1; // sql index not java index
        }
        inputNames.add(s);
        return inputNames.size();
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
                String value = params.get(i);
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

            return body.toString();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public boolean execute() throws SQLException {
        resultSet = null;
        String body = parseBody();

        JSONObject response = apiConnection.launchFlow(flowName, body);
        if (!response.optBoolean("isSuccess", true)) {
            String err = response.optString("errors", "Error calling procedure");
            throw new SQLException(err);
        }
        JSONArray outParams = response.optJSONArray("outputValues");
        System.out.println(outParams != null ? outParams.toString() : response);
        return false;
    }

    @Override
    public void setString(int i, String s) throws SQLException {
        params.put(i - 1, JSONObject.quote(s));
    }

    @Override
    public void setObject(int i, Object o) throws SQLException {
        if (Collection.class.isInstance(o)) {
            Collection c = (Collection) o;
            JSONArray a = new JSONArray();
            for (Object x : c) {
                a.put(x);
            }
            params.put(i - 1, a.toString());
        } else if (o instanceof Map) {
            JSONObject obj = new JSONObject((Map) o);
            params.put(i - 1, obj.toString());
        } else {
            JSONObject obj = new JSONObject(o);
            params.put(i - 1, obj.toString());
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
        return false;
    }

    @Override
    public String getString(int i) throws SQLException {
        return null;
    }

    @Override
    public boolean getBoolean(int i) throws SQLException {
        return false;
    }

    @Override
    public byte getByte(int i) throws SQLException {
        return 0;
    }

    @Override
    public short getShort(int i) throws SQLException {
        return 0;
    }

    @Override
    public int getInt(int i) throws SQLException {
        return 0;
    }

    @Override
    public long getLong(int i) throws SQLException {
        return 0;
    }

    @Override
    public float getFloat(int i) throws SQLException {
        return 0;
    }

    @Override
    public double getDouble(int i) throws SQLException {
        return 0;
    }

    @Override
    public BigDecimal getBigDecimal(int i, int i1) throws SQLException {
        return null;
    }

    @Override
    public byte[] getBytes(int i) throws SQLException {
        return new byte[0];
    }

    @Override
    public Date getDate(int i) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(int i) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(int i) throws SQLException {
        return null;
    }

    @Override
    public Object getObject(int i) throws SQLException {
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(int i) throws SQLException {
        return null;
    }

    @Override
    public Object getObject(int i, Map<String, Class<?>> map) throws SQLException {
        return null;
    }

    @Override
    public Ref getRef(int i) throws SQLException {
        return null;
    }

    @Override
    public Blob getBlob(int i) throws SQLException {
        return null;
    }

    @Override
    public Clob getClob(int i) throws SQLException {
        return null;
    }

    @Override
    public Array getArray(int i) throws SQLException {
        return null;
    }

    @Override
    public Date getDate(int i, Calendar calendar) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(int i, Calendar calendar) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(int i, Calendar calendar) throws SQLException {
        return null;
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
        return null;
    }

    @Override
    public void setURL(String s, URL url) throws SQLException {
        setURL(getIndex(s), url);
    }

    @Override
    public void setNull(String s, int i) throws SQLException {
        setNull(getIndex(s), i);
    }

    @Override
    public void setBoolean(String s, boolean b) throws SQLException {
        setBoolean(getIndex(s), b);
    }

    @Override
    public void setByte(String s, byte b) throws SQLException {
        setByte(getIndex(s), b);
    }

    @Override
    public void setShort(String s, short i) throws SQLException {
        setShort(getIndex(s), i);
    }

    @Override
    public void setInt(String s, int i) throws SQLException {
        setInt(getIndex(s), i);
    }

    @Override
    public void setLong(String s, long l) throws SQLException {
        setLong(getIndex(s), l);
    }

    @Override
    public void setFloat(String s, float v) throws SQLException {
        setFloat(getIndex(s), v);
    }

    @Override
    public void setDouble(String s, double v) throws SQLException {
        setDouble(getIndex(s), v);
    }

    @Override
    public void setBigDecimal(String s, BigDecimal bigDecimal) throws SQLException {
        setBigDecimal(getIndex(s), bigDecimal);
    }

    @Override
    public void setString(String s, String s1) throws SQLException {
        setString(getIndex(s), s1);
    }

    @Override
    public void setBytes(String s, byte[] bytes) throws SQLException {
        setBytes(getIndex(s), bytes);
    }

    @Override
    public void setDate(String s, Date date) throws SQLException {
        setDate(getIndex(s), date);
    }

    @Override
    public void setTime(String s, Time time) throws SQLException {
        setTime(getIndex(s), time);
    }

    @Override
    public void setTimestamp(String s, Timestamp timestamp) throws SQLException {
        setTimestamp(getIndex(s), timestamp);
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream, int i) throws SQLException {
        setAsciiStream(getIndex(s), inputStream, i);
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream, int i) throws SQLException {
        setBinaryStream(getIndex(s), inputStream, i);
    }

    @Override
    public void setObject(String s, Object o, int i, int i1) throws SQLException {
        setObject(getIndex(s), o, i, i1);
    }

    @Override
    public void setObject(String s, Object o, int i) throws SQLException {
        setObject(getIndex(s), o, i);
    }

    @Override
    public void setObject(String s, Object o) throws SQLException {
        setObject(getIndex(s), o);
    }

    @Override
    public void setCharacterStream(String s, Reader reader, int i) throws SQLException {
        setCharacterStream(getIndex(s), reader, i);
    }

    @Override
    public void setDate(String s, Date date, Calendar calendar) throws SQLException {
        setDate(getIndex(s), date, calendar);
    }

    @Override
    public void setTime(String s, Time time, Calendar calendar) throws SQLException {
        setTime(getIndex(s), time, calendar);
    }

    @Override
    public void setTimestamp(String s, Timestamp timestamp, Calendar calendar) throws SQLException {
        setTimestamp(getIndex(s), timestamp, calendar);
    }

    @Override
    public void setNull(String s, int i, String s1) throws SQLException {
        setNull(getIndex(s), i, s1);
    }

    @Override
    public String getString(String s) throws SQLException {
        return null;
    }

    @Override
    public boolean getBoolean(String s) throws SQLException {
        return false;
    }

    @Override
    public byte getByte(String s) throws SQLException {
        return 0;
    }

    @Override
    public short getShort(String s) throws SQLException {
        return 0;
    }

    @Override
    public int getInt(String s) throws SQLException {
        return 0;
    }

    @Override
    public long getLong(String s) throws SQLException {
        return 0;
    }

    @Override
    public float getFloat(String s) throws SQLException {
        return 0;
    }

    @Override
    public double getDouble(String s) throws SQLException {
        return 0;
    }

    @Override
    public byte[] getBytes(String s) throws SQLException {
        return new byte[0];
    }

    @Override
    public Date getDate(String s) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(String s) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(String s) throws SQLException {
        return null;
    }

    @Override
    public Object getObject(String s) throws SQLException {
        return null;
    }

    @Override
    public BigDecimal getBigDecimal(String s) throws SQLException {
        return null;
    }

    @Override
    public Object getObject(String s, Map<String, Class<?>> map) throws SQLException {
        return null;
    }

    @Override
    public Ref getRef(String s) throws SQLException {
        return null;
    }

    @Override
    public Blob getBlob(String s) throws SQLException {
        return null;
    }

    @Override
    public Clob getClob(String s) throws SQLException {
        return null;
    }

    @Override
    public Array getArray(String s) throws SQLException {
        return null;
    }

    @Override
    public Date getDate(String s, Calendar calendar) throws SQLException {
        return null;
    }

    @Override
    public Time getTime(String s, Calendar calendar) throws SQLException {
        return null;
    }

    @Override
    public Timestamp getTimestamp(String s, Calendar calendar) throws SQLException {
        return null;
    }

    @Override
    public URL getURL(String s) throws SQLException {
        return null;
    }

    @Override
    public RowId getRowId(int i) throws SQLException {
        return null;
    }

    @Override
    public RowId getRowId(String s) throws SQLException {
        return null;
    }

    @Override
    public void setRowId(String s, RowId rowId) throws SQLException {
        setRowId(getIndex(s), rowId);
    }

    @Override
    public void setNString(String s, String s1) throws SQLException {
        setNString(getIndex(s), s1);
    }

    @Override
    public void setNCharacterStream(String s, Reader reader, long l) throws SQLException {
        setNCharacterStream(getIndex(s), reader, l);
    }

    @Override
    public void setNClob(String s, NClob nClob) throws SQLException {
        setNClob(getIndex(s), nClob);
    }

    @Override
    public void setClob(String s, Reader reader, long l) throws SQLException {
        setClob(getIndex(s), reader, l);
    }

    @Override
    public void setBlob(String s, InputStream inputStream, long l) throws SQLException {
        setBlob(getIndex(s), inputStream, l);
    }

    @Override
    public void setNClob(String s, Reader reader, long l) throws SQLException {
        setNClob(getIndex(s), reader, l);
    }

    @Override
    public NClob getNClob(int i) throws SQLException {
        return null;
    }

    @Override
    public NClob getNClob(String s) throws SQLException {
        return null;
    }

    @Override
    public void setSQLXML(String s, SQLXML sqlxml) throws SQLException {
        setSQLXML(getIndex(s), sqlxml);
    }

    @Override
    public SQLXML getSQLXML(int i) throws SQLException {
        return null;
    }

    @Override
    public SQLXML getSQLXML(String s) throws SQLException {
        return null;
    }

    @Override
    public String getNString(int i) throws SQLException {
        return null;
    }

    @Override
    public String getNString(String s) throws SQLException {
        return null;
    }

    @Override
    public Reader getNCharacterStream(int i) throws SQLException {
        return null;
    }

    @Override
    public Reader getNCharacterStream(String s) throws SQLException {
        return null;
    }

    @Override
    public Reader getCharacterStream(int i) throws SQLException {
        return null;
    }

    @Override
    public Reader getCharacterStream(String s) throws SQLException {
        return null;
    }

    @Override
    public void setBlob(String s, Blob blob) throws SQLException {
        setBlob(getIndex(s), blob);
    }

    @Override
    public void setClob(String s, Clob clob) throws SQLException {
        setClob(getIndex(s), clob);
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream, long l) throws SQLException {
        setAsciiStream(getIndex(s), inputStream, l);
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream, long l) throws SQLException {
        setBinaryStream(getIndex(s), inputStream, l);
    }

    @Override
    public void setCharacterStream(String s, Reader reader, long l) throws SQLException {
        setCharacterStream(getIndex(s), reader, l);
    }

    @Override
    public void setAsciiStream(String s, InputStream inputStream) throws SQLException {
        setAsciiStream(getIndex(s), inputStream);
    }

    @Override
    public void setBinaryStream(String s, InputStream inputStream) throws SQLException {
        setBinaryStream(getIndex(s), inputStream);
    }

    @Override
    public void setCharacterStream(String s, Reader reader) throws SQLException {
        setCharacterStream(getIndex(s), reader);
    }

    @Override
    public void setNCharacterStream(String s, Reader reader) throws SQLException {
        setNCharacterStream(getIndex(s), reader);
    }

    @Override
    public void setClob(String s, Reader reader) throws SQLException {
        setClob(getIndex(s), reader);
    }

    @Override
    public void setBlob(String s, InputStream inputStream) throws SQLException {
        setBlob(getIndex(s), inputStream);
    }

    @Override
    public void setNClob(String s, Reader reader) throws SQLException {
        setNClob(getIndex(s), reader);
    }

    @Override
    public <T> T getObject(int i, Class<T> aClass) throws SQLException {
        return null;
    }

    @Override
    public <T> T getObject(String s, Class<T> aClass) throws SQLException {
        return null;
    }
}
