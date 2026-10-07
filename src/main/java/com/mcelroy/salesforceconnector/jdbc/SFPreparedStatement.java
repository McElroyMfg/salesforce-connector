// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Insert_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Select_Statement;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class SFPreparedStatement extends SFStatement implements PreparedStatement {
    private SQL_Statement sql_statement;
    protected Map<Integer, String> params = new HashMap<>();
    protected Map<Integer, Object> rawParams = new HashMap<>();

    protected void bind(int i, String literal, Object raw) {
        params.put(i, literal);
        rawParams.put(i, raw);
    }

    public SFPreparedStatement(SFConnection sfConnection, SFClientConnection apiConnection, String sql) {
        super(sfConnection, apiConnection);
        if (!sql.startsWith("call"))
            this.sql_statement = SQL_Statement.parse(sql);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        execute();
        return getResultSet();
    }

    @Override
    public int executeUpdate() throws SQLException {
        execute();
        return getUpdateCount() < 0 ? 0 : getUpdateCount();
    }

    @Override
    public void setNull(int i, int i1) throws SQLException {
        bind(i, "null", JSONObject.NULL);
    }

    @Override
    public void setBoolean(int i, boolean b) throws SQLException {
        bind(i, b ? "true" : "false", b);
    }

    @Override
    public void setByte(int i, byte b) throws SQLException {
        setString(i, "" + b);
        rawParams.put(i, b);
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
        bind(i, ((Long) l).toString(), l);
    }

    @Override
    public void setFloat(int i, float v) throws SQLException {
        setDouble(i, v);
    }

    @Override
    public void setDouble(int i, double v) throws SQLException {
        bind(i, ((Double) v).toString(), v);
    }

    @Override
    public void setBigDecimal(int i, BigDecimal bigDecimal) throws SQLException {
        if (bigDecimal == null) {
            setNull(i, 0);
            return;
        }
        bind(i, bigDecimal.toString(), bigDecimal);
    }

    @Override
    public void setString(int i, String s) throws SQLException {
        String literal = s.replace("\\", "\\\\").replace("'", "\\'");
        bind(i, "'" + literal + "'", s);
    }

    @Override
    public void setBytes(int i, byte[] bytes) throws SQLException {
        if (sql_statement instanceof SQL_Select_Statement) {
            // Preserve legacy SELECT literals; INSERT and Flow bindings use Base64.
            setString(i, "" + bytes);
            rawParams.put(i, bytes == null ? JSONObject.NULL : bytes);
        } else if (bytes == null)
            setNull(i, 0);
        else
            bind(i, "'" + Base64.getEncoder().encodeToString(bytes) + "'", bytes);
    }

    public static String formatDate(java.util.Date date, String fmt) {
        SimpleDateFormat df = new SimpleDateFormat(fmt);
        return df.format(date);
    }

    @Override
    public void setDate(int i, Date date) throws SQLException {
        if (date == null)
            setNull(i, 0);
        else {
            String formatted = formatDate(date, "yyyy-MM-dd");
            bind(i, formatted, formatted);
        }
    }

    @Override
    public void setTime(int i, Time time) throws SQLException {
        if (time == null)
            setNull(i, 0);
        else {
            String formatted = formatDate(time, "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            bind(i, formatted, formatted);
        }
    }

    @Override
    public void setTimestamp(int i, Timestamp timestamp) throws SQLException {
        if (timestamp == null)
            setNull(i, 0);
        else {
            String formatted = formatDate(timestamp, "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            bind(i, formatted, formatted);
        }
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
        setBytes(i, SFBinaryFields.read(inputStream, i1));
    }

    @Override
    public void clearParameters() throws SQLException {
        params.clear();
        rawParams.clear();
    }

    @Override
    public void setObject(int i, Object o, int i1) throws SQLException {
        setObject(i, o);
    }

    @Override
    public void setObject(int i, Object o) throws SQLException {
        if (o == null)
            setNull(i, 0);
        else if (o instanceof String)
            setString(i, (String) o);
        else if (o instanceof BigDecimal)
            setBigDecimal(i, (BigDecimal) o);
        else if (o instanceof Long)
            setLong(i, (Long) o);
        else if (o instanceof Double)
            setDouble(i, (Double) o);
        else if (o instanceof Boolean)
            setBoolean(i, (Boolean) o);
        else if (o instanceof Timestamp)
            setTimestamp(i, (Timestamp) o);
        else if (o instanceof Time)
            setTime(i, (Time) o);
        else if (o instanceof Date)
            setDate(i, (Date) o);
        else if (o instanceof java.util.Date)
            setDate(i, new Date(((java.util.Date) o).getTime()));
        else if (o instanceof Short)
            setShort(i, (Short) o);
        else if (o instanceof Integer)
            setInt(i, (Integer) o);
        else if (o instanceof Float)
            setFloat(i, (Float) o);
        else if (o instanceof Byte)
            setByte(i, (Byte) o);
        else if (o instanceof byte[])
            setBytes(i, (byte[]) o);
        else if (o instanceof InputStream)
            setBinaryStream(i, (InputStream) o);
        else if (o instanceof Blob)
            setBlob(i, (Blob) o);
        else
            setString(i, o.toString());
    }

    @Override
    public boolean execute() throws SQLException {
        resetExecution();
        if (sql_statement instanceof SQL_Insert_Statement) {
            SQL_Insert_Statement insert = (SQL_Insert_Statement) sql_statement;
            Map<String, Object> row = SFBinaryFields.prepare((SFConnection) getConnection(),
                    getApiConnection(), insert.getTable(), insert.bind(rawParams));
            try {
                JSONObject response = getApiConnection().insert(insert.getTable(), new JSONObject(row).toString());
                generatedId = response.optString("id", null);
                updateCount = 1;
                return false;
            } catch (RuntimeException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }
        execute(sql_statement, params);
        return true;
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
        setBytes(i, SFBinaryFields.read(blob));
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
        setBinaryStream(i, inputStream, l);
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
        setBytes(i, SFBinaryFields.read(inputStream, l));
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
        setBytes(i, SFBinaryFields.read(inputStream));
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
        setBinaryStream(i, inputStream);
    }

    @Override
    public void setNClob(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }
}
