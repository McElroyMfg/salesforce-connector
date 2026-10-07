// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

/** A scrollable, read-only result set for locally assembled JDBC metadata. */
public class SFListResultSet implements ResultSet {
    private final List<String> columns;
    private final List<Object[]> rows;
    private final List<Integer> columnTypes;
    private int currentRow = -1;
    private boolean closed;
    private boolean wasNull;
    private int fetchDirection = FETCH_FORWARD;
    private int fetchSize;

    public SFListResultSet(List<String> columns, List<Object[]> rows) {
        this(columns, rows, null);
    }

    /** Supplies stable JDBC column types, including when there are no rows. */
    public SFListResultSet(List<String> columns, List<Object[]> rows, List<Integer> columnTypes) {
        this.columns = new ArrayList<>(columns);
        this.rows = new ArrayList<>(rows.size());
        this.columnTypes = columnTypes == null ? null : new ArrayList<>(columnTypes);
        if (this.columnTypes != null) {
            if (this.columnTypes.size() != this.columns.size())
                throw new IllegalArgumentException("Column type count must match column count");
            for (Integer type : this.columnTypes) {
                if (type == null)
                    throw new IllegalArgumentException("Column types cannot be null");
                JDBCType.valueOf(type);
            }
        }
        for (String column : this.columns) {
            if (column == null)
                throw new IllegalArgumentException("Column names cannot be null");
        }
        for (Object[] row : rows) {
            if (row == null || row.length != columns.size())
                throw new IllegalArgumentException("Row length must match column count");
            this.rows.add(row.clone());
        }
    }

    private void checkOpen() throws SQLException {
        if (closed)
            throw new SQLException("ResultSet is closed");
    }

    private void checkColumn(int column) throws SQLException {
        if (column < 1 || column > columns.size())
            throw new SQLException("Invalid column index: " + column);
    }

    private Object value(int column) throws SQLException {
        checkOpen();
        checkColumn(column);
        if (currentRow < 0 || currentRow >= rows.size())
            throw new SQLException("Cursor is not on a row");
        Object value = rows.get(currentRow)[column - 1];
        wasNull = value == null;
        return value;
    }

    private SQLFeatureNotSupportedException unsupported() {
        return new SQLFeatureNotSupportedException("Read-only result set: operation not supported");
    }

    private SQLException conversion(Object value, String type, Exception cause) {
        return new SQLException("Cannot convert " + value + " to " + type, cause);
    }

    private BigDecimal decimal(int column) throws SQLException {
        Object value = value(column);
        if (value == null)
            return null;
        if (value instanceof BigDecimal)
            return (BigDecimal) value;
        if (value instanceof Boolean)
            return (Boolean) value ? BigDecimal.ONE : BigDecimal.ZERO;
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            throw conversion(value, "number", e);
        }
    }

    private long integral(int column, long minimum, long maximum) throws SQLException {
        BigDecimal value = decimal(column);
        if (value == null)
            return 0;
        BigDecimal truncated = value.setScale(0, RoundingMode.DOWN);
        if (truncated.compareTo(BigDecimal.valueOf(minimum)) < 0
                || truncated.compareTo(BigDecimal.valueOf(maximum)) > 0)
            throw new SQLException("Numeric value out of range: " + value);
        return truncated.longValue();
    }

    @Override public Object getObject(int column) throws SQLException { return value(column); }
    @Override public Object getObject(String column) throws SQLException { return getObject(findColumn(column)); }
    @Override public String getString(int column) throws SQLException {
        Object value = value(column);
        return value == null ? null : value.toString();
    }
    @Override public String getString(String column) throws SQLException { return getString(findColumn(column)); }
    @Override public boolean getBoolean(int column) throws SQLException {
        Object value = value(column);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        String text = value.toString().trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) return true;
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) return false;
        throw conversion(value, "boolean", null);
    }
    @Override public boolean getBoolean(String column) throws SQLException { return getBoolean(findColumn(column)); }
    @Override public byte getByte(int column) throws SQLException { return (byte) integral(column, Byte.MIN_VALUE, Byte.MAX_VALUE); }
    @Override public byte getByte(String column) throws SQLException { return getByte(findColumn(column)); }
    @Override public short getShort(int column) throws SQLException { return (short) integral(column, Short.MIN_VALUE, Short.MAX_VALUE); }
    @Override public short getShort(String column) throws SQLException { return getShort(findColumn(column)); }
    @Override public int getInt(int column) throws SQLException { return (int) integral(column, Integer.MIN_VALUE, Integer.MAX_VALUE); }
    @Override public int getInt(String column) throws SQLException { return getInt(findColumn(column)); }
    @Override public long getLong(int column) throws SQLException { return integral(column, Long.MIN_VALUE, Long.MAX_VALUE); }
    @Override public long getLong(String column) throws SQLException { return getLong(findColumn(column)); }
    @Override public float getFloat(int column) throws SQLException { BigDecimal v = decimal(column); return v == null ? 0 : v.floatValue(); }
    @Override public float getFloat(String column) throws SQLException { return getFloat(findColumn(column)); }
    @Override public double getDouble(int column) throws SQLException { BigDecimal v = decimal(column); return v == null ? 0 : v.doubleValue(); }
    @Override public double getDouble(String column) throws SQLException { return getDouble(findColumn(column)); }
    @Override public BigDecimal getBigDecimal(int column) throws SQLException { return decimal(column); }
    @Override public BigDecimal getBigDecimal(String column) throws SQLException { return getBigDecimal(findColumn(column)); }
    @Override public BigDecimal getBigDecimal(int column, int scale) throws SQLException {
        BigDecimal value = decimal(column);
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
    @Override public BigDecimal getBigDecimal(String column, int scale) throws SQLException { return getBigDecimal(findColumn(column), scale); }
    @Override public byte[] getBytes(int column) throws SQLException {
        Object value = value(column);
        if (value == null) return null;
        if (value instanceof byte[]) return ((byte[]) value).clone();
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
    @Override public byte[] getBytes(String column) throws SQLException { return getBytes(findColumn(column)); }

    private Timestamp timestamp(Object value) throws SQLException {
        if (value == null) return null;
        if (value instanceof Timestamp) {
            Timestamp result = new Timestamp(((Timestamp) value).getTime());
            result.setNanos(((Timestamp) value).getNanos());
            return result;
        }
        if (value instanceof java.util.Date) return new Timestamp(((java.util.Date) value).getTime());
        if (value instanceof LocalDateTime) return Timestamp.valueOf((LocalDateTime) value);
        if (value instanceof LocalDate) return Timestamp.valueOf(((LocalDate) value).atStartOfDay());
        String text = value.toString().trim();
        try {
            if (text.length() == 10) return Timestamp.valueOf(LocalDate.parse(text).atStartOfDay());
            try {
                return Timestamp.from(OffsetDateTime.parse(text,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
            } catch (java.time.format.DateTimeParseException e) {
                // Salesforce also returns numeric offsets without a colon.
                if (text.matches(".*[+-]\\d{4}$")) {
                    String normalized = text.substring(0, text.length() - 2) + ":" + text.substring(text.length() - 2);
                    return Timestamp.from(OffsetDateTime.parse(normalized).toInstant());
                }
                return Timestamp.valueOf(text.replace('T', ' '));
            }
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw conversion(value, "timestamp", e);
        }
    }

    @Override public Date getDate(int column) throws SQLException {
        Object value = value(column);
        if (value == null) return null;
        if (value instanceof java.util.Date) return new Date(((java.util.Date) value).getTime());
        if (value instanceof LocalDate) return Date.valueOf((LocalDate) value);
        try {
            return Date.valueOf(value.toString().trim());
        } catch (IllegalArgumentException e) {
            return new Date(timestamp(value).getTime());
        }
    }
    @Override public Date getDate(String column) throws SQLException { return getDate(findColumn(column)); }
    @Override public Time getTime(int column) throws SQLException {
        Object value = value(column);
        if (value == null) return null;
        if (value instanceof java.util.Date) return new Time(((java.util.Date) value).getTime());
        if (value instanceof LocalTime) return Time.valueOf((LocalTime) value);
        try {
            return Time.valueOf(LocalTime.parse(value.toString().trim()));
        } catch (java.time.DateTimeException e) {
            return new Time(timestamp(value).getTime());
        }
    }
    @Override public Time getTime(String column) throws SQLException { return getTime(findColumn(column)); }
    @Override public Timestamp getTimestamp(int column) throws SQLException { return timestamp(value(column)); }
    @Override public Timestamp getTimestamp(String column) throws SQLException { return getTimestamp(findColumn(column)); }

    private Timestamp calendarTimestamp(int column, Calendar calendar) throws SQLException {
        if (calendar == null) throw new SQLException("Calendar cannot be null");
        Object value = value(column);
        Timestamp result = timestamp(value);
        if (result == null || value instanceof java.util.Date) return result;
        String text = value.toString().trim();
        if (text.endsWith("Z") || text.matches(".*[+-]\\d{2}:?\\d{2}$")) return result;
        LocalDateTime local = result.toLocalDateTime();
        Calendar adjusted = (Calendar) calendar.clone();
        adjusted.clear();
        adjusted.set(local.getYear(), local.getMonthValue() - 1, local.getDayOfMonth(),
                local.getHour(), local.getMinute(), local.getSecond());
        result = new Timestamp(adjusted.getTimeInMillis());
        result.setNanos(local.getNano());
        return result;
    }
    @Override public Date getDate(int column, Calendar calendar) throws SQLException {
        Timestamp value = calendarTimestamp(column, calendar);
        return value == null ? null : new Date(value.getTime());
    }
    @Override public Date getDate(String column, Calendar calendar) throws SQLException { return getDate(findColumn(column), calendar); }
    @Override public Time getTime(int column, Calendar calendar) throws SQLException {
        if (calendar == null) throw new SQLException("Calendar cannot be null");
        Object value = value(column);
        if (value == null) return null;
        if (value instanceof java.util.Date) return new Time(((java.util.Date) value).getTime());
        try {
            LocalTime local = value instanceof LocalTime ? (LocalTime) value : LocalTime.parse(value.toString().trim());
            Calendar adjusted = (Calendar) calendar.clone();
            adjusted.clear();
            adjusted.set(1970, Calendar.JANUARY, 1, local.getHour(), local.getMinute(), local.getSecond());
            return new Time(adjusted.getTimeInMillis());
        } catch (java.time.DateTimeException e) {
            return new Time(calendarTimestamp(column, calendar).getTime());
        }
    }
    @Override public Time getTime(String column, Calendar calendar) throws SQLException { return getTime(findColumn(column), calendar); }
    @Override public Timestamp getTimestamp(int column, Calendar calendar) throws SQLException { return calendarTimestamp(column, calendar); }
    @Override public Timestamp getTimestamp(String column, Calendar calendar) throws SQLException { return getTimestamp(findColumn(column), calendar); }

    @Override public InputStream getAsciiStream(int column) throws SQLException {
        String value = getString(column);
        return value == null ? null : new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }
    @Override public InputStream getAsciiStream(String column) throws SQLException { return getAsciiStream(findColumn(column)); }
    @Override public InputStream getUnicodeStream(int column) throws SQLException {
        String value = getString(column);
        return value == null ? null : new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
    @Override public InputStream getUnicodeStream(String column) throws SQLException { return getUnicodeStream(findColumn(column)); }
    @Override public InputStream getBinaryStream(int column) throws SQLException {
        byte[] value = getBytes(column);
        return value == null ? null : new ByteArrayInputStream(value);
    }
    @Override public InputStream getBinaryStream(String column) throws SQLException { return getBinaryStream(findColumn(column)); }
    @Override public Reader getCharacterStream(int column) throws SQLException {
        String value = getString(column);
        return value == null ? null : new StringReader(value);
    }
    @Override public Reader getCharacterStream(String column) throws SQLException { return getCharacterStream(findColumn(column)); }
    @Override public String getNString(int column) throws SQLException { return getString(column); }
    @Override public String getNString(String column) throws SQLException { return getString(column); }
    @Override public Reader getNCharacterStream(int column) throws SQLException { return getCharacterStream(column); }
    @Override public Reader getNCharacterStream(String column) throws SQLException { return getCharacterStream(column); }
    @Override public URL getURL(int column) throws SQLException {
        Object value = value(column);
        if (value == null) return null;
        if (value instanceof URL) return (URL) value;
        try { return new URL(value.toString()); }
        catch (MalformedURLException e) { throw conversion(value, "URL", e); }
    }
    @Override public URL getURL(String column) throws SQLException { return getURL(findColumn(column)); }
    @Override public Blob getBlob(int column) throws SQLException {
        Object value = value(column);
        if (value == null || value instanceof Blob) return (Blob) value;
        return new SerialBlob(getBytes(column));
    }
    @Override public Blob getBlob(String column) throws SQLException { return getBlob(findColumn(column)); }
    @Override public Clob getClob(int column) throws SQLException {
        Object value = value(column);
        if (value == null || value instanceof Clob) return (Clob) value;
        return new SerialClob(getString(column).toCharArray());
    }
    @Override public Clob getClob(String column) throws SQLException { return getClob(findColumn(column)); }
    @Override public Ref getRef(int column) throws SQLException { return getObject(column, Ref.class); }
    @Override public Ref getRef(String column) throws SQLException { return getRef(findColumn(column)); }
    @Override public Array getArray(int column) throws SQLException { return getObject(column, Array.class); }
    @Override public Array getArray(String column) throws SQLException { return getArray(findColumn(column)); }
    @Override public RowId getRowId(int column) throws SQLException { return getObject(column, RowId.class); }
    @Override public RowId getRowId(String column) throws SQLException { return getRowId(findColumn(column)); }
    @Override public NClob getNClob(int column) throws SQLException { return getObject(column, NClob.class); }
    @Override public NClob getNClob(String column) throws SQLException { return getNClob(findColumn(column)); }
    @Override public SQLXML getSQLXML(int column) throws SQLException { return getObject(column, SQLXML.class); }
    @Override public SQLXML getSQLXML(String column) throws SQLException { return getSQLXML(findColumn(column)); }
    @Override public Object getObject(int column, Map<String, Class<?>> map) throws SQLException {
        if (map == null || !map.isEmpty()) throw unsupported();
        return getObject(column);
    }
    @Override public Object getObject(String column, Map<String, Class<?>> map) throws SQLException { return getObject(findColumn(column), map); }
    @Override public <T> T getObject(int column, Class<T> type) throws SQLException {
        if (type == null) throw new SQLException("Target type cannot be null");
        Object value = value(column);
        if (value == null) return null;
        if (type.isInstance(value)) return type.cast(value);
        Object converted;
        if (type == String.class) converted = getString(column);
        else if (type == Boolean.class || type == boolean.class) converted = getBoolean(column);
        else if (type == Byte.class || type == byte.class) converted = getByte(column);
        else if (type == Short.class || type == short.class) converted = getShort(column);
        else if (type == Integer.class || type == int.class) converted = getInt(column);
        else if (type == Long.class || type == long.class) converted = getLong(column);
        else if (type == Float.class || type == float.class) converted = getFloat(column);
        else if (type == Double.class || type == double.class) converted = getDouble(column);
        else if (type == BigDecimal.class) converted = getBigDecimal(column);
        else if (type == byte[].class) converted = getBytes(column);
        else if (type == Date.class) converted = getDate(column);
        else if (type == Time.class) converted = getTime(column);
        else if (type == Timestamp.class) converted = getTimestamp(column);
        else if (type == LocalDate.class) converted = getDate(column).toLocalDate();
        else if (type == LocalTime.class) converted = getTime(column).toLocalTime();
        else if (type == LocalDateTime.class) converted = getTimestamp(column).toLocalDateTime();
        else if (type == URL.class) converted = getURL(column);
        else throw conversion(value, type.getName(), null);
        @SuppressWarnings("unchecked") T result = (T) converted;
        return result;
    }
    @Override public <T> T getObject(String column, Class<T> type) throws SQLException { return getObject(findColumn(column), type); }
    @Override public int findColumn(String name) throws SQLException {
        checkOpen();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(name)) return i + 1;
        }
        throw new SQLException("Unknown column: " + name);
    }
    @Override public boolean wasNull() throws SQLException { checkOpen(); return wasNull; }

    private boolean move(long row) throws SQLException {
        checkOpen();
        currentRow = (int) Math.max(-1, Math.min(rows.size(), row));
        return currentRow >= 0 && currentRow < rows.size();
    }
    @Override public boolean next() throws SQLException { return move((long) currentRow + 1); }
    @Override public boolean previous() throws SQLException { return move((long) currentRow - 1); }
    @Override public boolean first() throws SQLException { return move(0); }
    @Override public boolean last() throws SQLException { return move(rows.size() - 1L); }
    @Override public void beforeFirst() throws SQLException { move(-1); }
    @Override public void afterLast() throws SQLException { move(rows.size()); }
    @Override public boolean absolute(int row) throws SQLException {
        return move(row > 0 ? row - 1L : row < 0 ? rows.size() + (long) row : -1);
    }
    @Override public boolean relative(int row) throws SQLException { return move((long) currentRow + row); }
    @Override public int getRow() throws SQLException {
        checkOpen();
        return currentRow >= 0 && currentRow < rows.size() ? currentRow + 1 : 0;
    }
    @Override public boolean isBeforeFirst() throws SQLException { checkOpen(); return !rows.isEmpty() && currentRow < 0; }
    @Override public boolean isAfterLast() throws SQLException { checkOpen(); return !rows.isEmpty() && currentRow >= rows.size(); }
    @Override public boolean isFirst() throws SQLException { checkOpen(); return !rows.isEmpty() && currentRow == 0; }
    @Override public boolean isLast() throws SQLException { checkOpen(); return !rows.isEmpty() && currentRow == rows.size() - 1; }
    @Override public void close() { closed = true; }
    @Override public boolean isClosed() { return closed; }
    @Override public int getType() throws SQLException { checkOpen(); return TYPE_SCROLL_INSENSITIVE; }
    @Override public int getConcurrency() throws SQLException { checkOpen(); return CONCUR_READ_ONLY; }
    @Override public int getHoldability() throws SQLException { checkOpen(); return HOLD_CURSORS_OVER_COMMIT; }
    @Override public Statement getStatement() throws SQLException { checkOpen(); return null; }
    @Override public SQLWarning getWarnings() throws SQLException { checkOpen(); return null; }
    @Override public void clearWarnings() throws SQLException { checkOpen(); }
    @Override public String getCursorName() throws SQLException { checkOpen(); return null; }
    @Override public void setFetchDirection(int direction) throws SQLException {
        checkOpen();
        if (direction != FETCH_FORWARD && direction != FETCH_REVERSE && direction != FETCH_UNKNOWN)
            throw new SQLException("Invalid fetch direction: " + direction);
        fetchDirection = direction;
    }
    @Override public int getFetchDirection() throws SQLException { checkOpen(); return fetchDirection; }
    @Override public void setFetchSize(int size) throws SQLException {
        checkOpen();
        if (size < 0) throw new SQLException("Fetch size cannot be negative");
        fetchSize = size;
    }
    @Override public int getFetchSize() throws SQLException { checkOpen(); return fetchSize; }
    @Override public boolean rowUpdated() throws SQLException { checkOpen(); return false; }
    @Override public boolean rowInserted() throws SQLException { checkOpen(); return false; }
    @Override public boolean rowDeleted() throws SQLException { checkOpen(); return false; }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if (type != null && type.isInstance(this)) return type.cast(this);
        throw new SQLException("Not a wrapper for " + type);
    }
    @Override public boolean isWrapperFor(Class<?> type) { return type != null && type.isInstance(this); }

    @Override public ResultSetMetaData getMetaData() throws SQLException { checkOpen(); return new ListMetaData(); }

    private class ListMetaData implements ResultSetMetaData {
        private Object sample(int column) throws SQLException {
            checkColumn(column);
            for (Object[] row : rows) {
                if (row[column - 1] != null) return row[column - 1];
            }
            return null;
        }
        @Override public int getColumnCount() { return columns.size(); }
        @Override public String getColumnName(int column) throws SQLException { checkColumn(column); return columns.get(column - 1); }
        @Override public String getColumnLabel(int column) throws SQLException { return getColumnName(column); }
        @Override public int getColumnType(int column) throws SQLException {
            checkColumn(column);
            if (columnTypes != null) return columnTypes.get(column - 1);
            Object value = sample(column);
            if (value == null || value instanceof String || value instanceof Character) return Types.VARCHAR;
            if (value instanceof Boolean) return Types.BOOLEAN;
            if (value instanceof Byte) return Types.TINYINT;
            if (value instanceof Short) return Types.SMALLINT;
            if (value instanceof Integer) return Types.INTEGER;
            if (value instanceof Long) return Types.BIGINT;
            if (value instanceof Float) return Types.REAL;
            if (value instanceof Double) return Types.DOUBLE;
            if (value instanceof BigDecimal) return Types.DECIMAL;
            if (value instanceof byte[]) return Types.VARBINARY;
            if (value instanceof Date || value instanceof LocalDate) return Types.DATE;
            if (value instanceof Time || value instanceof LocalTime) return Types.TIME;
            if (value instanceof java.util.Date || value instanceof LocalDateTime) return Types.TIMESTAMP;
            if (value instanceof Blob) return Types.BLOB;
            if (value instanceof NClob) return Types.NCLOB;
            if (value instanceof Clob) return Types.CLOB;
            if (value instanceof Array) return Types.ARRAY;
            if (value instanceof Ref) return Types.REF;
            if (value instanceof RowId) return Types.ROWID;
            if (value instanceof SQLXML) return Types.SQLXML;
            return Types.JAVA_OBJECT;
        }
        @Override public String getColumnTypeName(int column) throws SQLException {
            return JDBCType.valueOf(getColumnType(column)).getName();
        }
        @Override public String getColumnClassName(int column) throws SQLException {
            if (columnTypes != null) {
                switch (getColumnType(column)) {
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.NCHAR:
                    case Types.NVARCHAR:
                    case Types.LONGNVARCHAR: return String.class.getName();
                    case Types.BIT:
                    case Types.BOOLEAN: return Boolean.class.getName();
                    case Types.TINYINT: return Byte.class.getName();
                    case Types.SMALLINT: return Short.class.getName();
                    case Types.INTEGER: return Integer.class.getName();
                    case Types.BIGINT: return Long.class.getName();
                    case Types.NUMERIC:
                    case Types.DECIMAL: return BigDecimal.class.getName();
                    case Types.REAL: return Float.class.getName();
                    case Types.FLOAT:
                    case Types.DOUBLE: return Double.class.getName();
                    case Types.BINARY:
                    case Types.VARBINARY:
                    case Types.LONGVARBINARY: return byte[].class.getName();
                    case Types.DATE: return Date.class.getName();
                    case Types.TIME: return Time.class.getName();
                    case Types.TIMESTAMP: return Timestamp.class.getName();
                    case Types.BLOB: return Blob.class.getName();
                    case Types.CLOB: return Clob.class.getName();
                    case Types.NCLOB: return NClob.class.getName();
                    case Types.ARRAY: return Array.class.getName();
                    case Types.REF: return Ref.class.getName();
                    case Types.ROWID: return RowId.class.getName();
                    case Types.SQLXML: return SQLXML.class.getName();
                    default: return Object.class.getName();
                }
            }
            Object value = sample(column);
            if (value == null || value instanceof Character) return String.class.getName();
            if (value instanceof LocalDate) return Date.class.getName();
            if (value instanceof LocalTime) return Time.class.getName();
            if (value instanceof LocalDateTime) return Timestamp.class.getName();
            return value.getClass().getName();
        }
        @Override public boolean isAutoIncrement(int column) throws SQLException { checkColumn(column); return false; }
        @Override public boolean isCaseSensitive(int column) throws SQLException { return getColumnType(column) == Types.VARCHAR; }
        @Override public boolean isSearchable(int column) throws SQLException { checkColumn(column); return true; }
        @Override public boolean isCurrency(int column) throws SQLException { checkColumn(column); return false; }
        @Override public int isNullable(int column) throws SQLException { checkColumn(column); return columnNullableUnknown; }
        @Override public boolean isSigned(int column) throws SQLException {
            if (columnTypes == null) return sample(column) instanceof Number;
            switch (getColumnType(column)) {
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.INTEGER:
                case Types.BIGINT:
                case Types.REAL:
                case Types.FLOAT:
                case Types.DOUBLE:
                case Types.NUMERIC:
                case Types.DECIMAL: return true;
                default: return false;
            }
        }
        @Override public int getColumnDisplaySize(int column) throws SQLException {
            checkColumn(column);
            int size = 0;
            for (Object[] row : rows) {
                Object value = row[column - 1];
                if (value != null) size = Math.max(size, value instanceof byte[] ? ((byte[]) value).length : value.toString().length());
            }
            return size;
        }
        @Override public int getPrecision(int column) throws SQLException {
            Object value = sample(column);
            return value instanceof BigDecimal ? ((BigDecimal) value).precision() : getColumnDisplaySize(column);
        }
        @Override public int getScale(int column) throws SQLException {
            Object value = sample(column);
            return value instanceof BigDecimal ? ((BigDecimal) value).scale() : 0;
        }
        @Override public String getSchemaName(int column) throws SQLException { checkColumn(column); return ""; }
        @Override public String getTableName(int column) throws SQLException { checkColumn(column); return ""; }
        @Override public String getCatalogName(int column) throws SQLException { checkColumn(column); return ""; }
        @Override public boolean isReadOnly(int column) throws SQLException { checkColumn(column); return true; }
        @Override public boolean isWritable(int column) throws SQLException { checkColumn(column); return false; }
        @Override public boolean isDefinitelyWritable(int column) throws SQLException { checkColumn(column); return false; }
        @Override public <T> T unwrap(Class<T> type) throws SQLException {
            if (type != null && type.isInstance(this)) return type.cast(this);
            throw new SQLException("Not a wrapper for " + type);
        }
        @Override public boolean isWrapperFor(Class<?> type) { return type != null && type.isInstance(this); }
    }

    @Override public void updateNull(int column) throws SQLException { throw unsupported(); }
    @Override public void updateNull(String column) throws SQLException { throw unsupported(); }
    @Override public void updateBoolean(int column, boolean value) throws SQLException { throw unsupported(); }
    @Override public void updateBoolean(String column, boolean value) throws SQLException { throw unsupported(); }
    @Override public void updateByte(int column, byte value) throws SQLException { throw unsupported(); }
    @Override public void updateByte(String column, byte value) throws SQLException { throw unsupported(); }
    @Override public void updateShort(int column, short value) throws SQLException { throw unsupported(); }
    @Override public void updateShort(String column, short value) throws SQLException { throw unsupported(); }
    @Override public void updateInt(int column, int value) throws SQLException { throw unsupported(); }
    @Override public void updateInt(String column, int value) throws SQLException { throw unsupported(); }
    @Override public void updateLong(int column, long value) throws SQLException { throw unsupported(); }
    @Override public void updateLong(String column, long value) throws SQLException { throw unsupported(); }
    @Override public void updateFloat(int column, float value) throws SQLException { throw unsupported(); }
    @Override public void updateFloat(String column, float value) throws SQLException { throw unsupported(); }
    @Override public void updateDouble(int column, double value) throws SQLException { throw unsupported(); }
    @Override public void updateDouble(String column, double value) throws SQLException { throw unsupported(); }
    @Override public void updateBigDecimal(int column, BigDecimal value) throws SQLException { throw unsupported(); }
    @Override public void updateBigDecimal(String column, BigDecimal value) throws SQLException { throw unsupported(); }
    @Override public void updateString(int column, String value) throws SQLException { throw unsupported(); }
    @Override public void updateString(String column, String value) throws SQLException { throw unsupported(); }
    @Override public void updateBytes(int column, byte[] value) throws SQLException { throw unsupported(); }
    @Override public void updateBytes(String column, byte[] value) throws SQLException { throw unsupported(); }
    @Override public void updateDate(int column, Date value) throws SQLException { throw unsupported(); }
    @Override public void updateDate(String column, Date value) throws SQLException { throw unsupported(); }
    @Override public void updateTime(int column, Time value) throws SQLException { throw unsupported(); }
    @Override public void updateTime(String column, Time value) throws SQLException { throw unsupported(); }
    @Override public void updateTimestamp(int column, Timestamp value) throws SQLException { throw unsupported(); }
    @Override public void updateTimestamp(String column, Timestamp value) throws SQLException { throw unsupported(); }
    @Override public void updateAsciiStream(int column, InputStream value, int length) throws SQLException { throw unsupported(); }
    @Override public void updateAsciiStream(String column, InputStream value, int length) throws SQLException { throw unsupported(); }
    @Override public void updateBinaryStream(int column, InputStream value, int length) throws SQLException { throw unsupported(); }
    @Override public void updateBinaryStream(String column, InputStream value, int length) throws SQLException { throw unsupported(); }
    @Override public void updateCharacterStream(int column, Reader value, int length) throws SQLException { throw unsupported(); }
    @Override public void updateCharacterStream(String column, Reader value, int length) throws SQLException { throw unsupported(); }
    @Override public void updateObject(int column, Object value, int scale) throws SQLException { throw unsupported(); }
    @Override public void updateObject(String column, Object value, int scale) throws SQLException { throw unsupported(); }
    @Override public void updateObject(int column, Object value) throws SQLException { throw unsupported(); }
    @Override public void updateObject(String column, Object value) throws SQLException { throw unsupported(); }
    @Override public void insertRow() throws SQLException { throw unsupported(); }
    @Override public void updateRow() throws SQLException { throw unsupported(); }
    @Override public void deleteRow() throws SQLException { throw unsupported(); }
    @Override public void refreshRow() throws SQLException { throw unsupported(); }
    @Override public void cancelRowUpdates() throws SQLException { throw unsupported(); }
    @Override public void moveToInsertRow() throws SQLException { throw unsupported(); }
    @Override public void moveToCurrentRow() throws SQLException { throw unsupported(); }
    @Override public void updateRef(int column, Ref value) throws SQLException { throw unsupported(); }
    @Override public void updateRef(String column, Ref value) throws SQLException { throw unsupported(); }
    @Override public void updateBlob(int column, Blob value) throws SQLException { throw unsupported(); }
    @Override public void updateBlob(String column, Blob value) throws SQLException { throw unsupported(); }
    @Override public void updateClob(int column, Clob value) throws SQLException { throw unsupported(); }
    @Override public void updateClob(String column, Clob value) throws SQLException { throw unsupported(); }
    @Override public void updateArray(int column, Array value) throws SQLException { throw unsupported(); }
    @Override public void updateArray(String column, Array value) throws SQLException { throw unsupported(); }
    @Override public void updateRowId(int column, RowId value) throws SQLException { throw unsupported(); }
    @Override public void updateRowId(String column, RowId value) throws SQLException { throw unsupported(); }
    @Override public void updateNString(int column, String value) throws SQLException { throw unsupported(); }
    @Override public void updateNString(String column, String value) throws SQLException { throw unsupported(); }
    @Override public void updateNClob(int column, NClob value) throws SQLException { throw unsupported(); }
    @Override public void updateNClob(String column, NClob value) throws SQLException { throw unsupported(); }
    @Override public void updateSQLXML(int column, SQLXML value) throws SQLException { throw unsupported(); }
    @Override public void updateSQLXML(String column, SQLXML value) throws SQLException { throw unsupported(); }
    @Override public void updateNCharacterStream(int column, Reader value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateNCharacterStream(String column, Reader value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateAsciiStream(int column, InputStream value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateAsciiStream(String column, InputStream value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateBinaryStream(int column, InputStream value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateBinaryStream(String column, InputStream value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateCharacterStream(int column, Reader value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateCharacterStream(String column, Reader value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateBlob(int column, InputStream value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateBlob(String column, InputStream value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateClob(int column, Reader value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateClob(String column, Reader value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateNClob(int column, Reader value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateNClob(String column, Reader value, long length) throws SQLException { throw unsupported(); }
    @Override public void updateNCharacterStream(int column, Reader value) throws SQLException { throw unsupported(); }
    @Override public void updateNCharacterStream(String column, Reader value) throws SQLException { throw unsupported(); }
    @Override public void updateAsciiStream(int column, InputStream value) throws SQLException { throw unsupported(); }
    @Override public void updateAsciiStream(String column, InputStream value) throws SQLException { throw unsupported(); }
    @Override public void updateBinaryStream(int column, InputStream value) throws SQLException { throw unsupported(); }
    @Override public void updateBinaryStream(String column, InputStream value) throws SQLException { throw unsupported(); }
    @Override public void updateCharacterStream(int column, Reader value) throws SQLException { throw unsupported(); }
    @Override public void updateCharacterStream(String column, Reader value) throws SQLException { throw unsupported(); }
    @Override public void updateBlob(int column, InputStream value) throws SQLException { throw unsupported(); }
    @Override public void updateBlob(String column, InputStream value) throws SQLException { throw unsupported(); }
    @Override public void updateClob(int column, Reader value) throws SQLException { throw unsupported(); }
    @Override public void updateClob(String column, Reader value) throws SQLException { throw unsupported(); }
    @Override public void updateNClob(int column, Reader value) throws SQLException { throw unsupported(); }
    @Override public void updateNClob(String column, Reader value) throws SQLException { throw unsupported(); }
    @Override public void updateObject(int column, Object value, SQLType type, int scale) throws SQLException { throw unsupported(); }
    @Override public void updateObject(String column, Object value, SQLType type, int scale) throws SQLException { throw unsupported(); }
    @Override public void updateObject(int column, Object value, SQLType type) throws SQLException { throw unsupported(); }
    @Override public void updateObject(String column, Object value, SQLType type) throws SQLException { throw unsupported(); }
}
