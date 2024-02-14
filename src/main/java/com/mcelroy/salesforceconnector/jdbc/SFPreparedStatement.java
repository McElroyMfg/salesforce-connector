// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClientConnection;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class SFPreparedStatement extends SFStatement implements PreparedStatement {
    String sql;
    Map<Integer, String> params = new HashMap<>();

    public SFPreparedStatement(SFConnection sfConnection, SFClientConnection apiConnection, String sql) {
        super(sfConnection, apiConnection);
        this.sql = sql;
    }

    private String buildSQL() throws SQLException {
        StringBuilder b = new StringBuilder(sql.length() * 2);
        String[] parts = sql.split("\\?");
        for (int i = 0; i < parts.length - 1; i++) {
            b.append(parts[i]);
            String p = params.get(i);
            if (p == null)
                throw new SQLException("Missing parameter: " + i + 1);
            b.append(p);
        }
        b.append(parts[parts.length - 1]);
        if (sql.charAt(sql.length() - 1) == '?') {
            String p = params.get(parts.length - 1);
            if (p == null)
                throw new SQLException("Missing parameter: " + parts.length);
            b.append(p);
        }
        return b.toString();
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        execute();
        return getResultSet();
    }

    @Override
    public int executeUpdate() throws SQLException {
        execute();
        return 0;
    }

    @Override
    public void setNull(int i, int i1) throws SQLException {
        params.put(i - 1, "null");
    }

    @Override
    public void setBoolean(int i, boolean b) throws SQLException {
        params.put(i - 1, b ? "true" : "false");
    }

    @Override
    public void setByte(int i, byte b) throws SQLException {
        setString(i, "" + b);
    }

    @Override
    public void setShort(int i, short s) throws SQLException {
        setLong(i, s);
    }

    @Override
    public void setInt(int i, int i1) throws SQLException {
        setLong(i, i1);
    }

    @Override
    public void setLong(int i, long l) throws SQLException {
        params.put(i - 1, ((Long) l).toString());
    }

    @Override
    public void setFloat(int i, float v) throws SQLException {
        setDouble(i, v);
    }

    @Override
    public void setDouble(int i, double v) throws SQLException {
        params.put(i - 1, ((Double) v).toString());
    }

    @Override
    public void setBigDecimal(int i, BigDecimal bigDecimal) throws SQLException {
        if (bigDecimal == null)
            setNull(i, 0);
        params.put(i - 1, bigDecimal.toString());
    }

    @Override
    public void setString(int i, String s) throws SQLException {
        s = s.replace("\\", "\\\\").replace("'", "\\'");
        params.put(i - 1, "'" + s + "'");
    }

    @Override
    public void setBytes(int i, byte[] bytes) throws SQLException {
        setString(i, "" + bytes);
    }

    public static String formatDate(java.util.Date date, String fmt) {
        SimpleDateFormat df = new SimpleDateFormat(fmt);
        return df.format(date);
    }

    @Override
    public void setDate(int i, Date date) throws SQLException {
        if (date == null)
            setNull(i, 0);
        else
            params.put(i - 1, formatDate(date, "yyyy-MM-dd"));
    }

    @Override
    public void setTime(int i, Time time) throws SQLException {
        if (time == null)
            setNull(i, 0);
        else
            params.put(i - 1, formatDate(time, "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    }

    @Override
    public void setTimestamp(int i, Timestamp timestamp) throws SQLException {
        if (timestamp == null)
            setNull(i, 0);
        else
            params.put(i - 1, formatDate(timestamp, "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    }

    @Override
    public void setAsciiStream(int i, InputStream inputStream, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setUnicodeStream(int i, InputStream inputStream, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setBinaryStream(int i, InputStream inputStream, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void clearParameters() throws SQLException {
        params.clear();
    }

    @Override
    public void setObject(int i, Object o, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setObject(int i, Object o) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public boolean execute() throws SQLException {
        return execute(buildSQL());
    }

    @Override
    public void addBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setCharacterStream(int i, Reader reader, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setRef(int i, Ref ref) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setBlob(int i, Blob blob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setClob(int i, Clob clob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setArray(int i, Array array) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setDate(int i, Date date, Calendar calendar) throws SQLException {
        setDate(i, date);
    }

    @Override
    public void setTime(int i, Time time, Calendar calendar) throws SQLException {
        setTime(i, time);
    }

    @Override
    public void setTimestamp(int i, Timestamp timestamp, Calendar calendar) throws SQLException {
        setTimestamp(i, timestamp);
    }

    @Override
    public void setNull(int i, int i1, String s) throws SQLException {
        setNull(i, 0);
    }

    @Override
    public void setURL(int i, URL url) throws SQLException {
        setString(i, url.toString());
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return null;
    }

    @Override
    public void setRowId(int i, RowId rowId) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setNString(int i, String s) throws SQLException {
        setString(i, s);
    }

    @Override
    public void setNCharacterStream(int i, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setNClob(int i, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setClob(int i, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setBlob(int i, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setNClob(int i, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setSQLXML(int i, SQLXML sqlxml) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setObject(int i, Object o, int i1, int i2) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setAsciiStream(int i, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setBinaryStream(int i, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setCharacterStream(int i, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setAsciiStream(int i, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setBinaryStream(int i, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setCharacterStream(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setNCharacterStream(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setClob(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setBlob(int i, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setNClob(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }
}
