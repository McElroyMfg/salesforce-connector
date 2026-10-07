// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import org.junit.Test;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Date;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class SFListResultSetTest {
    private SFListResultSet result(String[] columns, Object[]... rows) {
        return new SFListResultSet(Arrays.asList(columns), Arrays.asList(rows));
    }

    private void fails(Class<? extends Exception> type, SqlAction action) throws Exception {
        try {
            action.run();
            fail("Expected " + type.getSimpleName());
        } catch (Exception e) {
            if (!type.isInstance(e)) throw e;
        }
    }

    private interface SqlAction {
        void run() throws Exception;
    }

    @Test
    public void readsByIndexAndCaseInsensitiveNameAndTracksNulls() throws Exception {
        try (SFListResultSet rs = result(new String[]{"Name", "Count", "Empty"},
                new Object[]{"Account", 17, null})) {
            fails(SQLException.class, () -> rs.getString(1));
            assertEquals(1, rs.findColumn("NAME"));
            assertTrue(rs.next());
            assertEquals("Account", rs.getString("nAmE"));
            assertEquals(Integer.valueOf(17), rs.getObject("count"));
            assertFalse(rs.wasNull());
            assertEquals(0, rs.getInt("empty"));
            assertTrue(rs.wasNull());
            assertNull(rs.getString(3));
            assertNull(rs.getObject(3, Integer.class));
            assertNull(rs.getBigDecimal(3));
            assertNull(rs.getBytes(3));
            assertNull(rs.getAsciiStream(3));
            assertNull(rs.getDate(3));
            assertFalse(rs.getBoolean(3));
            assertTrue(rs.wasNull());
            assertEquals(17, rs.getInt(2));
            assertFalse(rs.wasNull());
            fails(SQLException.class, () -> rs.getObject(0));
            fails(SQLException.class, () -> rs.getObject(4));
            fails(SQLException.class, () -> rs.findColumn("missing"));
            fails(SQLException.class, () -> rs.findColumn(null));
            assertFalse(rs.next());
            fails(SQLException.class, () -> rs.getObject(1));
        }
    }

    @Test
    public void convertsNumericBooleanAndTypedObjects() throws Exception {
        try (SFListResultSet rs = result(new String[]{"Number", "Boolean", "Bad"},
                new Object[]{" 42.75 ", true, "not numeric"})) {
            assertTrue(rs.next());
            assertEquals(42, rs.getByte("number"));
            assertEquals(42, rs.getShort(1));
            assertEquals(42, rs.getInt("number"));
            assertEquals(42L, rs.getLong(1));
            assertEquals(42.75f, rs.getFloat("number"), 0);
            assertEquals(42.75, rs.getDouble(1), 0);
            assertEquals(new BigDecimal("42.75"), rs.getBigDecimal("number"));
            assertEquals(new BigDecimal("42.8"), rs.getBigDecimal(1, 1));
            assertEquals(new BigDecimal("42.8"), rs.getBigDecimal("number", 1));
            assertEquals(Integer.valueOf(42), rs.getObject("number", Integer.class));
            assertEquals(Boolean.TRUE, rs.getObject(2, Boolean.class));
            assertEquals(1, rs.getInt(2));
            assertEquals("true", rs.getObject(2, String.class));
            assertEquals(" 42.75 ", rs.getObject(1, Collections.<String, Class<?>>emptyMap()));
            fails(SQLException.class, () -> rs.getInt(3));
            fails(SQLException.class, () -> rs.getBoolean(3));
            fails(SQLException.class, () -> rs.getObject(1, URL.class));
            fails(SQLException.class, () -> rs.getObject(1, (Class<?>) null));
            fails(SQLException.class, () -> rs.getObject(1, Thread.class));
        }
        try (SFListResultSet rs = result(new String[]{"A", "B", "C", "D"},
                new Object[]{" TRUE ", "0", 1, 0})) {
            rs.next();
            assertTrue(rs.getBoolean(1));
            assertFalse(rs.getBoolean(2));
            assertTrue(rs.getBoolean(3));
            assertFalse(rs.getBoolean(4));
        }
    }

    private void assertIntegralBounds(long minimum, long maximum, Class<?> boxedType,
                                      Class<?> primitiveType, IntegralGetter getter) throws Exception {
        BigDecimal lower = BigDecimal.valueOf(minimum);
        BigDecimal upper = BigDecimal.valueOf(maximum);
        BigDecimal fraction = new BigDecimal("0.99");
        try (SFListResultSet rs = result(new String[]{"Minimum", "Maximum", "Underflow", "Overflow", "Null"},
                new Object[]{lower.subtract(fraction), upper.add(fraction),
                        lower.subtract(BigDecimal.ONE), upper.add(BigDecimal.ONE), null})) {
            rs.next();
            assertEquals(minimum, getter.read(rs, 1).longValue());
            assertEquals(maximum, getter.read(rs, 2).longValue());
            assertEquals(minimum, ((Number) rs.getObject("minimum", boxedType)).longValue());
            assertEquals(maximum, ((Number) rs.getObject("maximum", primitiveType)).longValue());
            fails(SQLException.class, () -> getter.read(rs, 3));
            fails(SQLException.class, () -> getter.read(rs, 4));
            fails(SQLException.class, () -> rs.getObject(3, boxedType));
            fails(SQLException.class, () -> rs.getObject(4, boxedType));
            fails(SQLException.class, () -> rs.getObject("underflow", primitiveType));
            fails(SQLException.class, () -> rs.getObject("overflow", primitiveType));
            assertEquals(0, getter.read(rs, 5).longValue());
            assertTrue(rs.wasNull());
            assertNull(rs.getObject(5, boxedType));
            assertTrue(rs.wasNull());
        }
    }

    private interface IntegralGetter {
        Number read(SFListResultSet rs, int column) throws SQLException;
    }

    @Test
    public void checksEveryIntegralRangeAfterTruncatingFractionalParts() throws Exception {
        assertIntegralBounds(Byte.MIN_VALUE, Byte.MAX_VALUE, Byte.class, byte.class, SFListResultSet::getByte);
        assertIntegralBounds(Short.MIN_VALUE, Short.MAX_VALUE, Short.class, short.class, SFListResultSet::getShort);
        assertIntegralBounds(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.class, int.class, SFListResultSet::getInt);
        assertIntegralBounds(Long.MIN_VALUE, Long.MAX_VALUE, Long.class, long.class, SFListResultSet::getLong);
        try (SFListResultSet rs = result(new String[]{"Byte", "Short", "Integer", "Long"},
                new Object[]{"128.99", "-32769.99", "2147483648.99", "-9223372036854775809.99"})) {
            rs.next();
            fails(SQLException.class, () -> rs.getByte("byte"));
            fails(SQLException.class, () -> rs.getShort("short"));
            fails(SQLException.class, () -> rs.getInt("integer"));
            fails(SQLException.class, () -> rs.getLong("long"));
        }
    }

    @Test
    public void convertsTemporalValuesIncludingSalesforceOffsetsAndCalendars() throws Exception {
        Timestamp precise = Timestamp.valueOf("2024-02-03 04:05:06.123456789");
        try (SFListResultSet rs = result(new String[]{"Date", "Time", "Timestamp", "Salesforce", "Precise", "Bad"},
                new Object[]{"2024-02-03", "04:05:06", "2024-02-03T04:05:06.123Z",
                        "2024-02-03T04:05:06.123+0000", precise, "bad date"})) {
            rs.next();
            assertEquals(Date.valueOf("2024-02-03"), rs.getDate("DATE"));
            assertEquals(Time.valueOf("04:05:06"), rs.getTime("time"));
            Timestamp expected = Timestamp.from(Instant.parse("2024-02-03T04:05:06.123Z"));
            assertEquals(expected, rs.getTimestamp(3));
            assertEquals(expected, rs.getTimestamp("salesforce"));
            assertEquals(precise, rs.getTimestamp("precise"));
            assertNotSame(precise, rs.getTimestamp(5));
            assertEquals(LocalDate.of(2024, 2, 3), rs.getObject(1, LocalDate.class));
            assertEquals(LocalTime.of(4, 5, 6), rs.getObject(2, LocalTime.class));
            assertEquals(precise.toLocalDateTime(), rs.getObject(5, LocalDateTime.class));
            Calendar zone = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:00"));
            assertEquals(Instant.parse("2024-02-02T21:00:00Z").toEpochMilli(), rs.getDate("date", zone).getTime());
            assertEquals(Instant.parse("1970-01-01T01:05:06Z").toEpochMilli(), rs.getTime("time", zone).getTime());
            assertEquals(expected, rs.getTimestamp("timestamp", zone));
            fails(SQLException.class, () -> rs.getDate(6));
            fails(SQLException.class, () -> rs.getTime(6));
            fails(SQLException.class, () -> rs.getTimestamp(6));
            fails(SQLException.class, () -> rs.getTimestamp(1, null));
        }
        try (SFListResultSet rs = result(new String[]{"Local", "Null"},
                new Object[]{LocalDateTime.of(2024, 2, 3, 4, 5), null})) {
            rs.next();
            Calendar zone = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:00"));
            assertEquals(Instant.parse("2024-02-03T01:05:00Z").toEpochMilli(), rs.getTimestamp(1, zone).getTime());
            assertNull(rs.getTimestamp(2, zone));
            assertTrue(rs.wasNull());
        }
    }

    @Test
    public void readsStreamsBinaryLobsAndUrls() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        try (SFListResultSet rs = result(new String[]{"Text", "Binary", "URL"},
                new Object[]{"hello", bytes, "https://example.com"})) {
            rs.next();
            assertArrayEquals(bytes, rs.getBytes("binary"));
            assertNotSame(bytes, rs.getBytes(2));
            try (InputStream stream = rs.getBinaryStream("binary")) {
                assertEquals(1, stream.read());
                assertEquals(2, stream.read());
                assertEquals(3, stream.read());
                assertEquals(-1, stream.read());
            }
            try (InputStream stream = rs.getAsciiStream(1)) { assertEquals('h', stream.read()); }
            try (InputStream stream = rs.getUnicodeStream("text")) { assertEquals('h', stream.read()); }
            try (Reader reader = rs.getCharacterStream("text")) { assertEquals('h', reader.read()); }
            try (Reader reader = rs.getNCharacterStream(1)) { assertEquals('h', reader.read()); }
            assertEquals("hello", rs.getNString("text"));
            assertArrayEquals(bytes, rs.getBlob("binary").getBytes(1, 3));
            assertEquals("hello", rs.getClob("text").getSubString(1, 5));
            assertEquals(new URL("https://example.com"), rs.getURL("url"));
            assertEquals(rs.getURL(3), rs.getObject(3, URL.class));
        }
    }

    @Test
    public void scrollsAndClampsOutsideBounds() throws Exception {
        try (SFListResultSet rs = result(new String[]{"Id"}, new Object[]{1}, new Object[]{2}, new Object[]{3})) {
            assertTrue(rs.isBeforeFirst());
            assertEquals(0, rs.getRow());
            assertTrue(rs.next());
            assertTrue(rs.isFirst());
            assertTrue(rs.relative(1));
            assertEquals(2, rs.getRow());
            assertTrue(rs.previous());
            assertEquals(1, rs.getRow());
            assertTrue(rs.absolute(-1));
            assertTrue(rs.isLast());
            assertFalse(rs.next());
            assertTrue(rs.isAfterLast());
            assertEquals(0, rs.getRow());
            assertFalse(rs.next());
            assertTrue(rs.previous());
            assertEquals(3, rs.getInt(1));
            assertTrue(rs.absolute(-2));
            assertEquals(2, rs.getRow());
            assertFalse(rs.absolute(0));
            assertTrue(rs.isBeforeFirst());
            assertFalse(rs.previous());
            assertTrue(rs.first());
            assertTrue(rs.last());
            assertFalse(rs.relative(Integer.MAX_VALUE));
            assertTrue(rs.isAfterLast());
            assertFalse(rs.absolute(Integer.MIN_VALUE));
            assertTrue(rs.isBeforeFirst());
            rs.afterLast();
            assertTrue(rs.isAfterLast());
            rs.beforeFirst();
            assertTrue(rs.isBeforeFirst());
        }
        try (SFListResultSet rs = result(new String[]{"Id"})) {
            assertFalse(rs.next());
            assertFalse(rs.first());
            assertFalse(rs.last());
            assertFalse(rs.previous());
            assertFalse(rs.absolute(1));
            assertFalse(rs.isBeforeFirst());
            assertFalse(rs.isAfterLast());
            assertFalse(rs.isFirst());
            assertFalse(rs.isLast());
            assertEquals(0, rs.getRow());
        }
    }

    @Test
    public void providesReadOnlyMetadataWithoutMovingCursorOrChangingWasNull() throws Exception {
        try (SFListResultSet rs = result(new String[]{"Text", "Integer", "Decimal", "Null", "Date", "Time", "Timestamp", "Binary", "Flag"},
                new Object[]{null, null, null, null, null, null, null, null, null},
                new Object[]{"abc", 12, new BigDecimal("1.23"), null, Date.valueOf("2024-02-03"),
                        Time.valueOf("04:05:06"), Timestamp.valueOf("2024-02-03 04:05:06"), new byte[]{1}, true})) {
            rs.next();
            rs.getObject(1);
            ResultSetMetaData meta = rs.getMetaData();
            assertEquals(9, meta.getColumnCount());
            assertEquals("Integer", meta.getColumnName(2));
            assertEquals("Integer", meta.getColumnLabel(2));
            assertEquals(Types.VARCHAR, meta.getColumnType(1));
            assertEquals(Types.INTEGER, meta.getColumnType(2));
            assertEquals("INTEGER", meta.getColumnTypeName(2));
            assertEquals(Integer.class.getName(), meta.getColumnClassName(2));
            assertEquals(Types.DECIMAL, meta.getColumnType(3));
            assertEquals(3, meta.getPrecision(3));
            assertEquals(2, meta.getScale(3));
            assertEquals(Types.VARCHAR, meta.getColumnType(4));
            assertEquals(Types.DATE, meta.getColumnType(5));
            assertEquals(Types.TIME, meta.getColumnType(6));
            assertEquals(Types.TIMESTAMP, meta.getColumnType(7));
            assertEquals(Types.VARBINARY, meta.getColumnType(8));
            assertEquals(Types.BOOLEAN, meta.getColumnType(9));
            assertTrue(meta.isReadOnly(1));
            assertFalse(meta.isWritable(1));
            assertFalse(meta.isDefinitelyWritable(1));
            assertTrue(meta.isSigned(2));
            assertEquals(3, meta.getColumnDisplaySize(1));
            assertTrue(rs.wasNull());
            assertEquals(1, rs.getRow());
            assertSame(meta, meta.unwrap(ResultSetMetaData.class));
            fails(SQLException.class, () -> meta.getColumnName(0));
            fails(SQLException.class, () -> meta.getColumnType(10));
        }
        try (SFListResultSet empty = result(new String[]{"Id"})) {
            assertEquals(1, empty.getMetaData().getColumnCount());
            assertEquals(Types.VARCHAR, empty.getMetaData().getColumnType(1));
        }
    }

    @Test
    public void preservesExplicitColumnTypesForEmptyAndPopulatedResults() throws Exception {
        List<String> names = Arrays.asList("Text", "Flag", "Integer", "Short", "Long");
        List<Integer> types = new ArrayList<>(Arrays.asList(
                Types.VARCHAR, Types.BOOLEAN, Types.INTEGER, Types.SMALLINT, Types.BIGINT));
        String[] classes = {String.class.getName(), Boolean.class.getName(), Integer.class.getName(),
                Short.class.getName(), Long.class.getName()};
        SFListResultSet empty = new SFListResultSet(names, Collections.<Object[]>emptyList(), types);
        SFListResultSet populated = new SFListResultSet(names,
                Collections.singletonList(new Object[]{"value", true, 1L, 2, 3}), types);
        types.set(2, Types.VARCHAR);
        try {
            ResultSetMetaData emptyMetadata = empty.getMetaData();
            ResultSetMetaData populatedMetadata = populated.getMetaData();
            assertEquals(5, emptyMetadata.getColumnCount());
            for (int column = 1; column <= names.size(); column++) {
                assertEquals(emptyMetadata.getColumnType(column), populatedMetadata.getColumnType(column));
                assertEquals(emptyMetadata.getColumnTypeName(column), populatedMetadata.getColumnTypeName(column));
                assertEquals(classes[column - 1], emptyMetadata.getColumnClassName(column));
                assertEquals(classes[column - 1], populatedMetadata.getColumnClassName(column));
            }
            assertEquals(Types.INTEGER, emptyMetadata.getColumnType(3));
            assertEquals(Types.SMALLINT, emptyMetadata.getColumnType(4));
            assertEquals(Types.BIGINT, emptyMetadata.getColumnType(5));
            assertEquals("BOOLEAN", emptyMetadata.getColumnTypeName(2));
            assertTrue(emptyMetadata.isSigned(3));
            assertFalse(emptyMetadata.isSigned(2));
            fails(SQLException.class, () -> emptyMetadata.getColumnType(0));
            fails(SQLException.class, () -> emptyMetadata.getColumnClassName(6));
        } finally {
            empty.close();
            populated.close();
        }
        fails(IllegalArgumentException.class, () -> new SFListResultSet(names,
                Collections.<Object[]>emptyList(), Collections.singletonList(Types.INTEGER)));
        fails(IllegalArgumentException.class, () -> new SFListResultSet(
                Collections.singletonList("Invalid"), Collections.<Object[]>emptyList(),
                Collections.singletonList((Integer) null)));
        fails(IllegalArgumentException.class, () -> new SFListResultSet(
                Collections.singletonList("Invalid"), Collections.<Object[]>emptyList(),
                Collections.singletonList(Integer.MAX_VALUE)));
    }

    @Test
    public void rejectsEveryUpdateOverloadAndRowMutation() throws Exception {
        SFListResultSet rs = result(new String[]{"Value"}, new Object[]{"value"});
        rs.next();
        for (Method method : ResultSet.class.getMethods()) {
            if (!method.getName().startsWith("update")) continue;
            Object[] args = new Object[method.getParameterCount()];
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == int.class) args[i] = 1;
                else if (types[i] == long.class) args[i] = 1L;
                else if (types[i] == byte.class) args[i] = (byte) 1;
                else if (types[i] == short.class) args[i] = (short) 1;
                else if (types[i] == float.class) args[i] = 1f;
                else if (types[i] == double.class) args[i] = 1d;
                else if (types[i] == boolean.class) args[i] = true;
                else if (types[i] == String.class) args[i] = "Value";
            }
            try {
                method.invoke(rs, args);
                fail("Expected unsupported operation: " + method);
            } catch (InvocationTargetException e) {
                assertTrue(method.toString(), e.getCause() instanceof SQLFeatureNotSupportedException);
            }
        }
        fails(SQLFeatureNotSupportedException.class, rs::insertRow);
        fails(SQLFeatureNotSupportedException.class, rs::deleteRow);
        fails(SQLFeatureNotSupportedException.class, rs::refreshRow);
        fails(SQLFeatureNotSupportedException.class, rs::cancelRowUpdates);
        fails(SQLFeatureNotSupportedException.class, rs::moveToInsertRow);
        fails(SQLFeatureNotSupportedException.class, rs::moveToCurrentRow);
        assertEquals("value", rs.getString(1));
        rs.close();
    }

    @Test
    public void supportsLifecycleAndFetchHintsAndSnapshotsInputContainers() throws Exception {
        List<String> columns = new ArrayList<>(Collections.singletonList("Value"));
        Object[] row = new Object[]{"original"};
        List<Object[]> rows = new ArrayList<>(Collections.singletonList(row));
        SFListResultSet rs = new SFListResultSet(columns, rows);
        columns.set(0, "changed");
        row[0] = "changed";
        rows.clear();
        assertTrue(rs.next());
        assertEquals("original", rs.getString("Value"));
        assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE, rs.getType());
        assertEquals(ResultSet.CONCUR_READ_ONLY, rs.getConcurrency());
        assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, rs.getHoldability());
        assertNull(rs.getStatement());
        assertNull(rs.getWarnings());
        rs.clearWarnings();
        assertFalse(rs.rowUpdated());
        assertFalse(rs.rowInserted());
        assertFalse(rs.rowDeleted());
        rs.setFetchSize(10);
        rs.setFetchDirection(ResultSet.FETCH_REVERSE);
        assertEquals(10, rs.getFetchSize());
        assertEquals(ResultSet.FETCH_REVERSE, rs.getFetchDirection());
        fails(SQLException.class, () -> rs.setFetchSize(-1));
        fails(SQLException.class, () -> rs.setFetchDirection(-1));
        assertSame(rs, rs.unwrap(ResultSet.class));
        assertTrue(rs.isWrapperFor(SFListResultSet.class));
        assertFalse(rs.isWrapperFor(Statement.class));
        fails(SQLException.class, () -> rs.unwrap(Statement.class));
        assertFalse(rs.isClosed());
        rs.close();
        rs.close();
        assertTrue(rs.isClosed());
        fails(SQLException.class, rs::next);
        fails(SQLException.class, rs::wasNull);
        fails(SQLException.class, rs::getMetaData);
        fails(SQLException.class, () -> rs.getString(1));
        fails(IllegalArgumentException.class, () -> result(new String[]{"Value"}, new Object[]{}));
    }
}
