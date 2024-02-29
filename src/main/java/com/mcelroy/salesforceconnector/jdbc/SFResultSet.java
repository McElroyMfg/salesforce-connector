// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.node.SQL_Column;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Table;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SFResultSet implements ResultSet {
    private SFStatement statement;
    private List<String> selectColumnNames = new ArrayList<>();
    private Map<String, String> selectColumnAliasMap = new HashMap<>();
    private String tableName;
    private String nextResults;
    private List<JSONObject> rows;
    private int currentRow = -1;
    private boolean wasNull = true;
    private Map<String, Object> updateRow = new HashMap<>();

    public SFResultSet(SFStatement s, SQL_Statement sql_statement, JSONObject result) throws SQLException {
        this.statement = s;

        // set initial results
        updateResultSet(result);

        // Build select column info
        sql_statement.accept(new SQL_Visitor() {
            @Override
            public void visit(SQL_Node node) {
                if (node instanceof SQL_Column) {
                    SQL_Column c = (SQL_Column) node;
                    if (c.getColumnType() == SQL_Column.ColumnType.SELECT) {
                        selectColumnNames.add(c.getName());
                        if (c.getAlias() != null)
                            selectColumnAliasMap.put(c.getAlias(), c.getName());
                    }
                }
                if (node instanceof SQL_Table)
                    tableName = ((SQL_Table) node).getName();
            }

            @Override
            public void leave(SQL_Node node) {

            }
        });
    }

    public int columnCount() {
        return selectColumnNames.size();
    }

    private void updateResultSet(JSONObject r) throws SQLException {
        nextResults = r.optString("nextRecordsUrl");

        List<JSONObject> records = new ArrayList<>();
        JSONArray a = r.optJSONArray("records");
        if (a != null) {
            try {
                // for each record in the batch add the object to the results
                for (int i = 0; i < a.length(); i++)
                    records.add(a.getJSONObject(i));
            } catch (Exception e) {
                throw new SQLException("Could not get record from JSONArray", e);
            }
        }

        this.rows = records;
        this.currentRow = -1;
    }

    @Override
    public boolean next() throws SQLException {
        updateRow.clear();
        if (currentRow < rows.size())
            currentRow++;

        // check for next fetch if we are at the end of the current list
        if (currentRow >= rows.size() && nextResults != null && !nextResults.trim().equals("")) {
            updateResultSet(statement.getApiConnection().queryNext(nextResults));
            currentRow++; // updateResultSet sets current row to -1
        }

        return currentRow >= 0 && currentRow < rows.size();
    }

    @Override
    public void close() throws SQLException {

    }

    @Override
    public boolean wasNull() throws SQLException {
        return wasNull;
    }

    public String getColumnName(int i) throws SQLException {
        if (i > selectColumnNames.size())
            throw new SQLException("Invalid column index: " + i);
        else
            return selectColumnNames.get(i - 1);
    }

    @Override
    public String getString(int i) throws SQLException {
        return getString(getColumnName(i));
    }

    @Override
    public boolean getBoolean(int i) throws SQLException {
        return getBoolean(getColumnName(i));
    }

    @Override
    public byte getByte(int i) throws SQLException {
        return getByte(getColumnName(i));
    }

    @Override
    public short getShort(int i) throws SQLException {
        return getShort(getColumnName(i));
    }

    @Override
    public int getInt(int i) throws SQLException {
        return getInt(getColumnName(i));
    }

    @Override
    public long getLong(int i) throws SQLException {
        return getLong(getColumnName(i));
    }

    @Override
    public float getFloat(int i) throws SQLException {
        return getFloat(getColumnName(i));
    }

    @Override
    public double getDouble(int i) throws SQLException {
        return getDouble(getColumnName(i));
    }

    @Override
    public BigDecimal getBigDecimal(int i, int i1) throws SQLException {
        return getBigDecimal(getColumnName(i));
    }

    @Override
    public byte[] getBytes(int i) throws SQLException {
        return getBytes(getColumnName(i));
    }

    @Override
    public Date getDate(int i) throws SQLException {
        return getDate(getColumnName(i));
    }

    @Override
    public Time getTime(int i) throws SQLException {
        return getTime(getColumnName(i));
    }

    @Override
    public Timestamp getTimestamp(int i) throws SQLException {
        return getTimestamp(getColumnName(i));
    }

    @Override
    public InputStream getAsciiStream(int i) throws SQLException {
        return getAsciiStream(getColumnName(i));
    }

    @Override
    public InputStream getUnicodeStream(int i) throws SQLException {
        return getUnicodeStream(getColumnName(i));
    }

    @Override
    public InputStream getBinaryStream(int i) throws SQLException {
        return getBinaryStream(getColumnName(i));
    }

    @Override
    public String getString(String s) throws SQLException {
        // check if this is an alias for a column
        String c = selectColumnAliasMap.get(s);
        if (c != null)
            s = c;

        return getDottedString(rows.get(currentRow), s.split("\\."));
    }

    private String getDottedString(JSONObject object, String[] path) throws SQLException {
        for (int i = 0; i < path.length - 1; i++) {
            if (object != null)
                object = object.optJSONObject(getKey(object, path[i]));
        }

        if (object != null) {
            if (object.isNull(getKey(object, path[path.length - 1]))) {
                wasNull = true;
                return null;
            }
            String v = object.optString(getKey(object, path[path.length - 1]), null);
            return v;
        } else {
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
        return new SFResultSetMetaData(this);
    }

    @Override
    public Object getObject(int i) throws SQLException {
        return getObject(getColumnName(i));
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
        return getBigDecimal(getColumnName(i));
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
        updateRow.clear();
        currentRow = -1;
    }

    @Override
    public void afterLast() throws SQLException {
        updateRow.clear();
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
        updateRow.clear();
        if (i > 0 && i <= rows.size()) {
            currentRow = i + -1;
            return true;
        }
        throw new SQLException("Invalid row number");
    }

    @Override
    public boolean relative(int i) throws SQLException {
        updateRow.clear();
        return absolute(currentRow + i);
    }

    @Override
    public boolean previous() throws SQLException {
        updateRow.clear();
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
        updateNull(getColumnName(i));
    }

    @Override
    public void updateBoolean(int i, boolean b) throws SQLException {
        updateBoolean(getColumnName(i), b);
    }

    @Override
    public void updateByte(int i, byte b) throws SQLException {
        updateByte(getColumnName(i), b);
    }

    @Override
    public void updateShort(int i, short i1) throws SQLException {
        updateShort(getColumnName(i), i1);
    }

    @Override
    public void updateInt(int i, int i1) throws SQLException {
        updateInt(getColumnName(i), i1);
    }

    @Override
    public void updateLong(int i, long l) throws SQLException {
        updateLong(getColumnName(i), l);
    }

    @Override
    public void updateFloat(int i, float v) throws SQLException {
        updateFloat(getColumnName(i), v);
    }

    @Override
    public void updateDouble(int i, double v) throws SQLException {
        updateDouble(getColumnName(i), v);
    }

    @Override
    public void updateBigDecimal(int i, BigDecimal bigDecimal) throws SQLException {
        updateBigDecimal(getColumnName(i), bigDecimal);
    }

    @Override
    public void updateString(int i, String s) throws SQLException {
        updateString(getColumnName(i), s);
    }

    @Override
    public void updateBytes(int i, byte[] bytes) throws SQLException {
        updateBytes(getColumnName(i), bytes);
    }

    @Override
    public void updateDate(int i, Date date) throws SQLException {
        updateDate(getColumnName(i), date);
    }

    @Override
    public void updateTime(int i, Time time) throws SQLException {
        updateTime(getColumnName(i), time);
    }

    @Override
    public void updateTimestamp(int i, Timestamp timestamp) throws SQLException {
        updateTimestamp(getColumnName(i), timestamp);
    }

    @Override
    public void updateAsciiStream(int i, InputStream inputStream, int i1) throws SQLException {
        updateAsciiStream(getColumnName(i), inputStream, i1);
    }

    @Override
    public void updateBinaryStream(int i, InputStream inputStream, int i1) throws SQLException {
        updateBinaryStream(getColumnName(i), inputStream, i1);
    }

    @Override
    public void updateCharacterStream(int i, Reader reader, int i1) throws SQLException {
        updateCharacterStream(getColumnName(i), reader, i1);
    }

    @Override
    public void updateObject(int i, Object o, int i1) throws SQLException {
        updateObject(getColumnName(i), o, i1);
    }

    @Override
    public void updateObject(int i, Object o) throws SQLException {
        updateObject(getColumnName(i), o);
    }

    @Override
    public void updateNull(String s) throws SQLException {
        updateRow.put(s, null);
    }

    @Override
    public void updateBoolean(String s, boolean b) throws SQLException {
        updateRow.put(s, b ? "true" : "false");
    }

    @Override
    public void updateByte(String s, byte b) throws SQLException {
        updateRow.put(s, "" + b);
    }

    @Override
    public void updateShort(String s, short i) throws SQLException {
        updateLong(s, i);
    }

    @Override
    public void updateInt(String s, int i) throws SQLException {
        updateLong(s, i);
    }

    @Override
    public void updateLong(String s, long l) throws SQLException {
        updateRow.put(s, l);
    }

    @Override
    public void updateFloat(String s, float v) throws SQLException {
        updateDouble(s, v);
    }

    @Override
    public void updateDouble(String s, double v) throws SQLException {
        updateRow.put(s, v);
    }

    @Override
    public void updateBigDecimal(String s, BigDecimal bigDecimal) throws SQLException {
        if (bigDecimal == null)
            updateNull(s);
        updateRow.put(s, bigDecimal);
    }

    @Override
    public void updateString(String s, String s1) throws SQLException {
        updateRow.put(s, s1);
    }

    @Override
    public void updateBytes(String s, byte[] bytes) throws SQLException {
        updateRow.put(s, "" + bytes);
    }

    @Override
    public void updateDate(String s, Date date) throws SQLException {
        if (date == null)
            updateNull(s);
        else
            updateRow.put(s, SFPreparedStatement.formatDate(date, "yyyy-MM-dd"));
    }

    @Override
    public void updateTime(String s, Time time) throws SQLException {
        if (time == null)
            updateNull(s);
        else
            updateRow.put(s, SFPreparedStatement.formatDate(time, "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    }

    @Override
    public void updateTimestamp(String s, Timestamp timestamp) throws SQLException {
        if (timestamp == null)
            updateNull(s);
        else
            updateRow.put(s, SFPreparedStatement.formatDate(timestamp, "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
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
        updateObject(s, o);
    }

    @Override
    public void updateObject(String s, Object o) throws SQLException {
        if (o == null)
            updateNull(s);
        else if (o instanceof String)
            updateString(s, (String) o);
        else if (o instanceof BigDecimal)
            updateBigDecimal(s, (BigDecimal) o);
        else if (o instanceof Long)
            updateLong(s, (Long) o);
        else if (o instanceof Double)
            updateDouble(s, (Double) o);
        else if (o instanceof Boolean)
            updateBoolean(s, (Boolean) o);
        else if (o instanceof Timestamp)
            updateTimestamp(s, (Timestamp) o);
        else if (o instanceof Time)
            updateTime(s, (Time) o);
        else if (o instanceof Date)
            updateDate(s, (Date) o);
        else if (o instanceof java.util.Date)
            updateDate(s, new Date(((java.util.Date) o).getTime()));
        else if (o instanceof Short)
            updateShort(s, (Short) o);
        else if (o instanceof Integer)
            updateInt(s, (Integer) o);
        else if (o instanceof Float)
            updateFloat(s, (Float) o);
        else
            updateString(s, o.toString());
    }

    @Override
    public void insertRow() throws SQLException {
        JSONObject jo = new JSONObject(updateRow);
        statement.getApiConnection().insert(tableName, jo.toString());
        updateRow.clear();
    }

    @Override
    public void updateRow() throws SQLException {
        String id = getString("id");
        if (id == null)
            throw new SQLException("Can not update record. Selected columns do not include id field.");
        JSONObject jo = new JSONObject(updateRow);
        statement.getApiConnection().update(tableName + "/" + id, jo.toString());
        updateRow.clear();
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
        updateRow.clear();
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        afterLast();
        updateRow.clear();
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        updateRow.clear();
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
        return getDate(getColumnName(i), calendar);
    }

    @Override
    public Date getDate(String s, Calendar calendar) throws SQLException {
        return getDate(s);
    }

    @Override
    public Time getTime(int i, Calendar calendar) throws SQLException {
        return getTime(getColumnName(i), calendar);
    }

    @Override
    public Time getTime(String s, Calendar calendar) throws SQLException {
        return getTime(s);
    }

    @Override
    public Timestamp getTimestamp(int i, Calendar calendar) throws SQLException {
        return getTimestamp(getColumnName(i), calendar);
    }

    @Override
    public Timestamp getTimestamp(String s, Calendar calendar) throws SQLException {
        return getTimestamp(s);
    }

    @Override
    public URL getURL(int i) throws SQLException {
        return getURL(getColumnName(i));
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
        updateNString(getColumnName(i), s);
    }

    @Override
    public void updateNString(String s, String s1) throws SQLException {
        updateString(s, s1);
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
        return getNString(getColumnName(i));
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
