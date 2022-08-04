// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.SQL_Column;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SFResultSet implements ResultSet {
    SFStatement statement;
    private List<JSONObject> rows;
    private int currentRow = -1;
    private boolean wasNull = true;

    public SFResultSet(SFStatement s, List<JSONObject> result) {
        this.statement = s;
        this.rows = result;
    }

    @Override
    public boolean next() throws SQLException {
        if (currentRow < rows.size())
            currentRow++;
        return currentRow >= 0 && currentRow < rows.size();
    }

    @Override
    public void close() throws SQLException {

    }

    @Override
    public boolean wasNull() throws SQLException {
        return wasNull;
    }

    @Override
    public String getString(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public boolean getBoolean(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public byte getByte(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public short getShort(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public int getInt(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public long getLong(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public float getFloat(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public double getDouble(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public BigDecimal getBigDecimal(int i, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public byte[] getBytes(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Date getDate(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Time getTime(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Timestamp getTimestamp(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public InputStream getAsciiStream(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public InputStream getUnicodeStream(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public InputStream getBinaryStream(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public String getString(String s) throws SQLException {
        // check if this is an alias for a column
        if (statement.sql_statement != null) {
            SQL_Column c = statement.sql_statement.getColumn(s);
            if (c != null)
                s = c.name;
        }

        return getDottedString(rows.get(currentRow), s.split("\\."));
    }

    private String getDottedString(JSONObject object, String[] path) throws SQLException {
        for (int i = 0; i < path.length - 1; i++) {
            if(object != null)
                object = object.optJSONObject(getKey(object, path[i]));
        }

        if(object != null) {
            String v = object.optString(getKey(object, path[path.length - 1]), null);
            wasNull = v == null || v.trim().toLowerCase().equals("null");
            return v;
        }else{
            wasNull = true;
            return null;
        }
    }

    private String getKey(JSONObject object, String s) throws SQLException {
        String sl = s.toLowerCase();

        // SF is case insensitive so check all against lower case
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (key.toLowerCase().equals(sl)) {
                return key;
            }
        }
        throw new SQLException("Unknown field in ResultSet: " + s);
    }

    private String getTrimmedLower(String s) throws SQLException {
        String v = getString(s);
        return v == null ? null : v.trim().toLowerCase();
    }

    @Override
    public boolean getBoolean(String s) throws SQLException {
        String v = getTrimmedLower(s);
        return v != null && v.equals("true");
    }

    @Override
    public byte getByte(String s) throws SQLException {
        String v = getString(s);
        return v != null && v.length() > 0 ? v.getBytes()[0] : 0;
    }

    @Override
    public short getShort(String s) throws SQLException {
        return ((Long) getLong(s)).shortValue();
    }

    @Override
    public int getInt(String s) throws SQLException {
        return ((Long) getLong(s)).intValue();
    }

    @Override
    public long getLong(String s) throws SQLException {
        String v = getTrimmedLower(s);

        try {
            return v == null || v.trim().toLowerCase().equals("null") ? 0 : Long.parseLong(v);
        } catch (Exception e) {
            try {
                return ((Double) Double.parseDouble(v)).longValue();
            } catch (Exception e2) {
                throw new SQLException(e);
            }
        }
    }

    @Override
    public float getFloat(String s) throws SQLException {
        return ((Double) getDouble(s)).floatValue();
    }

    @Override
    public double getDouble(String s) throws SQLException {
        String v = getTrimmedLower(s);

        try {
            return v == null ? 0 : Double.parseDouble(v);
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public BigDecimal getBigDecimal(String s, int i) throws SQLException {
        String v = getTrimmedLower(s);

        try {
            return v == null ? BigDecimal.ZERO : new BigDecimal(v);
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public byte[] getBytes(String s) throws SQLException {
        String v = getString(s);
        return v != null ? v.getBytes() : new byte[0];
    }

    public java.util.Date parseDate(String s, String fmt) throws SQLException {
        SimpleDateFormat df = new SimpleDateFormat(fmt);
        try {
            return df.parse(s);
        } catch (ParseException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public Date getDate(String s) throws SQLException {
        String v = getString(s);
        return v == null ? null : new Date(parseDate(v, "yyyy-MM-dd").getTime());
    }

    @Override
    public Time getTime(String s) throws SQLException {
        String v = getString(s);
        return v == null ? null : new Time(parseDate(v, "yyyy-MM-dd'T'HH:mm:ss.SSSZ").getTime());
    }

    @Override
    public Timestamp getTimestamp(String s) throws SQLException {
        String v = getString(s);
        return v == null ? null : new Timestamp(parseDate(v, "yyyy-MM-dd'T'HH:mm:ss.SSSZ").getTime());
    }

    @Override
    public InputStream getAsciiStream(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public InputStream getUnicodeStream(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public InputStream getBinaryStream(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {

    }

    @Override
    public String getCursorName() throws SQLException {
        return null;
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return null;
    }

    @Override
    public Object getObject(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Object getObject(String s) throws SQLException {
        return getString(s);
    }

    @Override
    public int findColumn(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Reader getCharacterStream(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Reader getCharacterStream(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public BigDecimal getBigDecimal(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public BigDecimal getBigDecimal(String s) throws SQLException {
        return getBigDecimal(s, 0);
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        return currentRow < 0;
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return currentRow >= rows.size();
    }

    @Override
    public boolean isFirst() throws SQLException {
        return currentRow == 0;
    }

    @Override
    public boolean isLast() throws SQLException {
        return currentRow == rows.size() - 1;
    }

    @Override
    public void beforeFirst() throws SQLException {
        currentRow = -1;
    }

    @Override
    public void afterLast() throws SQLException {
        currentRow = rows.size();
    }

    @Override
    public boolean first() throws SQLException {
        if (rows.size() > 0) {
            currentRow = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean last() throws SQLException {
        if (rows.size() > 0) {
            currentRow = rows.size() - 1;
            return true;
        }
        return false;
    }

    @Override
    public int getRow() throws SQLException {
        return currentRow + 1;
    }

    @Override
    public boolean absolute(int i) throws SQLException {
        if (i > 0 && i <= rows.size()) {
            currentRow = i + -1;
            return true;
        }
        throw new SQLException("Invalid row number");
    }

    @Override
    public boolean relative(int i) throws SQLException {
        return absolute(currentRow + i);
    }

    @Override
    public boolean previous() throws SQLException {
        if (currentRow >= 0)
            currentRow--;
        return currentRow >= 0 && currentRow < rows.size();
    }

    @Override
    public void setFetchDirection(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return 0;
    }

    @Override
    public void setFetchSize(int i) throws SQLException {

    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public int getType() throws SQLException {
        return 0;
    }

    @Override
    public int getConcurrency() throws SQLException {
        return 0;
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        return false;
    }

    @Override
    public boolean rowInserted() throws SQLException {
        return false;
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        return false;
    }

    @Override
    public void updateNull(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBoolean(int i, boolean b) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateByte(int i, byte b) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateShort(int i, short i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateInt(int i, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateLong(int i, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateFloat(int i, float v) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateDouble(int i, double v) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBigDecimal(int i, BigDecimal bigDecimal) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateString(int i, String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBytes(int i, byte[] bytes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateDate(int i, Date date) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateTime(int i, Time time) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateTimestamp(int i, Timestamp timestamp) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateCharacterStream(int i, Reader reader, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateObject(int i, Object o, int i1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateObject(int i, Object o) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNull(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBoolean(String s, boolean b) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateByte(String s, byte b) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateShort(String s, short i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateInt(String s, int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateLong(String s, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateFloat(String s, float v) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateDouble(String s, double v) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBigDecimal(String s, BigDecimal bigDecimal) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateString(String s, String s1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBytes(String s, byte[] bytes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateDate(String s, Date date) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateTime(String s, Time time) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateTimestamp(String s, Timestamp timestamp) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream, int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream, int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateCharacterStream(String s, Reader reader, int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateObject(String s, Object o, int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateObject(String s, Object o) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void insertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void deleteRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void refreshRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void moveToCurrentRow() throws SQLException {

    }

    @Override
    public Statement getStatement() throws SQLException {
        return statement;
    }

    @Override
    public Object getObject(int i, Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Ref getRef(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Blob getBlob(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Clob getClob(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Array getArray(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Object getObject(String s, Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Ref getRef(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Blob getBlob(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Clob getClob(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Array getArray(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Date getDate(int i, Calendar calendar) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Date getDate(String s, Calendar calendar) throws SQLException {
        return getDate(s);
    }

    @Override
    public Time getTime(int i, Calendar calendar) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Time getTime(String s, Calendar calendar) throws SQLException {
        return getTime(s);
    }

    @Override
    public Timestamp getTimestamp(int i, Calendar calendar) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Timestamp getTimestamp(String s, Calendar calendar) throws SQLException {
        return getTimestamp(s);
    }

    @Override
    public URL getURL(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public URL getURL(String s) throws SQLException {
        try {
            return new URL(getString(s));
        } catch (MalformedURLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void updateRef(int i, Ref ref) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateRef(String s, Ref ref) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBlob(int i, Blob blob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBlob(String s, Blob blob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateClob(int i, Clob clob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateClob(String s, Clob clob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateArray(int i, Array array) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateArray(String s, Array array) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
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
    public void updateRowId(int i, RowId rowId) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateRowId(String s, RowId rowId) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public int getHoldability() throws SQLException {
        return 0;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public void updateNString(int i, String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNString(String s, String s1) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNClob(int i, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNClob(String s, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public NClob getNClob(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public NClob getNClob(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public SQLXML getSQLXML(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public SQLXML getSQLXML(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateSQLXML(int i, SQLXML sqlxml) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateSQLXML(String s, SQLXML sqlxml) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public String getNString(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public String getNString(String s) throws SQLException {
        return getString(s);
    }

    @Override
    public Reader getNCharacterStream(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Reader getNCharacterStream(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNCharacterStream(int i, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNCharacterStream(String s, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateCharacterStream(int i, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateCharacterStream(String s, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBlob(int i, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBlob(String s, InputStream inputStream, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateClob(int i, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateClob(String s, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNClob(int i, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNClob(String s, Reader reader, long l) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNCharacterStream(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNCharacterStream(String s, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateCharacterStream(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateAsciiStream(String s, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBinaryStream(String s, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateCharacterStream(String s, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBlob(int i, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateBlob(String s, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateClob(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateClob(String s, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNClob(int i, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void updateNClob(String s, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public <T> T getObject(int i, Class<T> aClass) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public <T> T getObject(String s, Class<T> aClass) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public <T> T unwrap(Class<T> aClass) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }
}
