package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClient;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SFPreparedStatementInsertTest {
    private static final String ID = "068000000000001AAA";
    private static final byte[] BYTES = {0, 1, 2, 127, -128, -1};
    private final SFClient client = mock(SFClient.class);
    private final SFClientConnection api = mock(SFClientConnection.class);
    private SFConnection connection;

    private interface Binding {
        void bind(PreparedStatement statement) throws Exception;
    }

    private interface Action {
        void run() throws Exception;
    }

    @Before
    public void setUp() {
        when(client.getConnection(isNull())).thenReturn(api);
        when(api.describe("ContentVersion")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(new JSONObject().put("name", "VersionData").put("type", "base64"))
                .put(new JSONObject().put("name", "Title").put("type", "string"))));
        when(api.insert(eq("ContentVersion"), anyString())).thenReturn(
                new JSONObject().put("id", ID).put("success", true));
        connection = new SFConnection(client);
    }

    private PreparedStatement prepare(String fields, String values) throws SQLException {
        return connection.prepareStatement("INSERT INTO ContentVersion (" + fields + ") VALUES (" + values + ")");
    }

    private JSONObject insertValue(String field, Binding binding) throws Exception {
        clearInvocations(api);
        PreparedStatement statement = prepare(field, "?");
        binding.bind(statement);
        assertEquals(1, statement.executeUpdate());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(api).insert(eq("ContentVersion"), body.capture());
        return new JSONObject(body.getValue());
    }

    private SQLException sqlException(Action action) throws Exception {
        try {
            action.run();
            fail("Expected SQLException");
            return null;
        } catch (SQLException expected) {
            return expected;
        }
    }

    private void assertKeys(PreparedStatement statement, String expectedId) throws Exception {
        ResultSet keys = statement.getGeneratedKeys();
        assertNotNull(keys);
        assertEquals(1, keys.getMetaData().getColumnCount());
        assertEquals("Id", keys.getMetaData().getColumnLabel(1));
        assertEquals(Types.VARCHAR, keys.getMetaData().getColumnType(1));
        if (expectedId == null) {
            assertFalse(keys.next());
        } else {
            assertTrue(keys.next());
            assertEquals(expectedId, keys.getString(1));
            assertEquals(expectedId, keys.getString("Id"));
            assertFalse(keys.next());
        }
    }

    @Test
    public void connectionInsertPreservesMixedLiteralsAndTypedParameters() throws Exception {
        PreparedStatement statement = prepare(
                "Title, PathOnClient, Origin, IsMajorVersion, Count__c, Size__c, Enabled__c, Empty__c, VersionData",
                "?, ?, 'S', true, ?, ?, ?, ?, ?");
        String name = "O'Reilly\\folder\\雪.heic";
        statement.setString(1, name);
        statement.setString(2, name);
        statement.setLong(3, 9007199254740993L);
        statement.setBigDecimal(4, new BigDecimal("123.4500"));
        statement.setBoolean(5, false);
        statement.setNull(6, Types.VARCHAR);
        statement.setBytes(7, BYTES);
        assertKeys(statement, null);
        assertFalse(statement.execute());
        assertEquals(1, statement.getUpdateCount());
        assertNull(statement.getResultSet());
        assertKeys(statement, ID);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(api).insert(eq("ContentVersion"), body.capture());
        JSONObject record = new JSONObject(body.getValue());
        assertEquals(9, record.length());
        assertEquals(name, record.get("Title"));
        assertEquals(name, record.get("PathOnClient"));
        assertEquals("S", record.get("Origin"));
        assertSame(Boolean.TRUE, record.get("IsMajorVersion"));
        assertTrue(record.get("Count__c") instanceof Number);
        assertEquals(9007199254740993L, record.getLong("Count__c"));
        assertTrue(record.get("Size__c") instanceof Number);
        assertEquals(0, new BigDecimal(record.get("Size__c").toString()).compareTo(new BigDecimal("123.45")));
        assertSame(Boolean.FALSE, record.get("Enabled__c"));
        assertSame(JSONObject.NULL, record.get("Empty__c"));
        assertEquals(Base64.getEncoder().encodeToString(BYTES), record.getString("VersionData"));
        verify(client, atLeastOnce()).getConnection(isNull());
    }

    @Test
    public void scalarSettersKeepNumbersAndBooleansAsJsonTypes() throws Exception {
        Binding[] bindings = {
                s -> s.setByte(1, (byte) -7),
                s -> s.setShort(1, (short) -12),
                s -> s.setInt(1, -123),
                s -> s.setLong(1, 9007199254740993L),
                s -> s.setFloat(1, 1.25f),
                s -> s.setDouble(1, 2.5),
                s -> s.setBigDecimal(1, new BigDecimal("123.4500"))
        };
        String[] expected = {"-7", "-12", "-123", "9007199254740993", "1.25", "2.5", "123.4500"};
        for (int i = 0; i < bindings.length; i++) {
            Object value = insertValue("Count__c", bindings[i]).get("Count__c");
            assertTrue(value instanceof Number);
            assertEquals(0, new BigDecimal(expected[i]).compareTo(new BigDecimal(value.toString())));
        }
        assertSame(Boolean.TRUE, insertValue("Enabled__c", s -> s.setBoolean(1, true)).get("Enabled__c"));
        assertSame(Boolean.FALSE, insertValue("Enabled__c", s -> s.setBoolean(1, false)).get("Enabled__c"));
        verify(api, never()).describe(anyString());
    }

    @Test
    public void binaryStreamAndBlobOverloadsEncodeTheSameBytes() throws Exception {
        Blob blob = mock(Blob.class);
        when(blob.getBinaryStream()).thenAnswer(invocation -> new ByteArrayInputStream(BYTES));
        when(blob.length()).thenReturn((long) BYTES.length);
        when(blob.getBytes(1L, BYTES.length)).thenReturn(BYTES);
        Binding[] bindings = {
                s -> s.setBytes(1, BYTES),
                s -> s.setBinaryStream(1, new ByteArrayInputStream(BYTES)),
                s -> s.setBinaryStream(1, new ByteArrayInputStream(BYTES), BYTES.length),
                s -> s.setBinaryStream(1, new ByteArrayInputStream(BYTES), (long) BYTES.length),
                s -> s.setBlob(1, blob),
                s -> s.setBlob(1, new ByteArrayInputStream(BYTES)),
                s -> s.setBlob(1, new ByteArrayInputStream(BYTES), (long) BYTES.length),
                s -> s.setObject(1, BYTES),
                s -> s.setObject(1, BYTES, Types.VARBINARY),
                s -> s.setObject(1, new ByteArrayInputStream(BYTES)),
                s -> s.setObject(1, blob)
        };
        for (Binding binding : bindings) {
            assertEquals(Base64.getEncoder().encodeToString(BYTES),
                    insertValue("VersionData", binding).getString("VersionData"));
        }
    }

    @Test
    public void lengthLimitedStreamsReadOnlySpecifiedBytesIncludingZero() throws Exception {
        for (int overload = 0; overload < 3; overload++) {
            final int selected = overload;
            for (int length : new int[]{0, 3}) {
                ByteArrayInputStream stream = new ByteArrayInputStream(BYTES);
                JSONObject record = insertValue("VersionData", s -> {
                    if (selected == 0) s.setBinaryStream(1, stream, length);
                    else if (selected == 1) s.setBinaryStream(1, stream, (long) length);
                    else s.setBlob(1, stream, (long) length);
                });
                byte[] prefix = java.util.Arrays.copyOf(BYTES, length);
                assertEquals(Base64.getEncoder().encodeToString(prefix), record.getString("VersionData"));
                assertEquals(BYTES.length - length, stream.available());
            }
        }
    }

    @Test
    public void shorterThanDeclaredStreamsAndIoFailuresBecomeSqlExceptions() throws Exception {
        for (int overload = 0; overload < 3; overload++) {
            final int selected = overload;
            PreparedStatement statement = prepare("VersionData", "?");
            sqlException(() -> {
                InputStream stream = new ByteArrayInputStream(BYTES);
                if (selected == 0) statement.setBinaryStream(1, stream, BYTES.length + 1);
                else if (selected == 1) statement.setBinaryStream(1, stream, (long) BYTES.length + 1);
                else statement.setBlob(1, stream, (long) BYTES.length + 1);
                statement.executeUpdate();
            });
        }
        PreparedStatement statement = prepare("VersionData", "?");
        SQLException failure = sqlException(() -> {
            statement.setBinaryStream(1, new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("read failed");
                }
            });
            statement.executeUpdate();
        });
        assertTrue(failure.getMessage().contains("read failed")
                || (failure.getCause() != null && failure.getCause().getMessage().contains("read failed")));
        verify(api, never()).insert(anyString(), anyString());
    }

    @Test
    public void nullBinaryBindingsAndBigDecimalProduceJsonNullWithoutDescribe() throws Exception {
        Binding[] bindings = {
                s -> s.setBytes(1, null),
                s -> s.setBinaryStream(1, null),
                s -> s.setBinaryStream(1, null, 3),
                s -> s.setBinaryStream(1, null, 3L),
                s -> s.setBlob(1, (Blob) null),
                s -> s.setBlob(1, (InputStream) null),
                s -> s.setBlob(1, null, 3L),
                s -> s.setBigDecimal(1, null),
                s -> s.setObject(1, null),
                s -> s.setObject(1, null, Types.BLOB),
                s -> s.setNull(1, Types.BLOB),
                s -> s.setNull(1, Types.BLOB, "base64")
        };
        for (Binding binding : bindings) {
            JSONObject record = insertValue("VersionData", binding);
            assertTrue(record.has("VersionData"));
            assertSame(JSONObject.NULL, record.get("VersionData"));
        }
        verify(api, never()).describe(anyString());
    }

    @Test
    public void objectScalarDispatchPreservesTypesAndUnescapedStrings() throws Exception {
        long instant = 1706618096123L;
        Object[][] cases = {
                {"O'Reilly\\雪", "O'Reilly\\雪"},
                {new BigDecimal("123.4500"), new BigDecimal("123.4500")},
                {Long.valueOf(9007199254740993L), Long.valueOf(9007199254740993L)},
                {Double.valueOf(2.5), Double.valueOf(2.5)},
                {Boolean.TRUE, Boolean.TRUE},
                {Short.valueOf((short) -12), Integer.valueOf(-12)},
                {Integer.valueOf(-123), Integer.valueOf(-123)},
                {Float.valueOf(1.25f), Double.valueOf(1.25)},
                {Byte.valueOf((byte) -7), Integer.valueOf(-7)},
                {new java.sql.Timestamp(instant),
                        SFParameterEncoder.formatDate(new java.sql.Timestamp(instant), "yyyy-MM-dd'T'HH:mm:ss.SSSZ")},
                {new java.sql.Time(instant),
                        SFParameterEncoder.formatDate(new java.sql.Time(instant), "yyyy-MM-dd'T'HH:mm:ss.SSSZ")},
                {new java.sql.Date(instant),
                        SFParameterEncoder.formatDate(new java.sql.Date(instant), "yyyy-MM-dd")},
                {new java.util.Date(instant),
                        SFParameterEncoder.formatDate(new java.util.Date(instant), "yyyy-MM-dd")},
                {new Object() {
                    @Override
                    public String toString() {
                        return "fallback'\\雪";
                    }
                }, "fallback'\\雪"}
        };
        for (Object[] testCase : cases) {
            for (boolean typed : new boolean[]{false, true}) {
                JSONObject record = insertValue("Value__c", s -> {
                    if (typed) s.setObject(1, testCase[0], Types.JAVA_OBJECT);
                    else s.setObject(1, testCase[0]);
                });
                Object actual = record.get("Value__c");
                if (testCase[1] instanceof Number) {
                    assertTrue("Expected JSON number for " + testCase[0].getClass().getSimpleName()
                            + ", got " + actual.getClass().getSimpleName(), actual instanceof Number);
                    assertEquals(0, new BigDecimal(testCase[1].toString())
                            .compareTo(new BigDecimal(actual.toString())));
                } else {
                    assertEquals(testCase[1], actual);
                }
            }
        }
        verify(api, never()).describe(anyString());
    }

    @Test
    public void binaryDescribeIsLazyCachedAndRejectsNonBase64Fields() throws Exception {
        insertValue("Title", s -> s.setString(1, "title"));
        verify(api, never()).describe(anyString());
        PreparedStatement statement = prepare("VersionData", "?");
        statement.setBytes(1, BYTES);
        statement.executeUpdate();
        statement.setBytes(1, new byte[]{3});
        statement.executeUpdate();
        PreparedStatement second = prepare("VersionData", "?");
        second.setBytes(1, BYTES);
        second.executeUpdate();
        verify(api, times(1)).describe("ContentVersion");
        clearInvocations(api);
        PreparedStatement invalid = prepare("Title", "?");
        SQLException failure = sqlException(() -> {
            invalid.setBytes(1, BYTES);
            invalid.executeUpdate();
        });
        assertTrue(failure.getMessage().contains("Title"));
        verify(api, never()).insert(anyString(), anyString());
    }

    @Test
    public void clearParametersRemovesBindingsAndGeneratedKeysAfterFailure() throws Exception {
        PreparedStatement statement = prepare("Title, Origin", "?, 'S'");
        statement.setString(1, "first");
        assertEquals(1, statement.executeUpdate());
        assertKeys(statement, ID);
        statement.clearParameters();
        clearInvocations(api);
        sqlException(statement::executeUpdate);
        assertKeys(statement, null);
        verify(api, never()).insert(anyString(), anyString());
        statement.setString(1, "replacement");
        assertEquals(1, statement.executeUpdate());
        assertKeys(statement, ID);
    }

    @Test
    public void restFailuresPreserveOriginalMessageAndClearPriorKeys() throws Exception {
        PreparedStatement statement = prepare("Title", "?");
        statement.setString(1, "title");
        statement.executeUpdate();
        assertKeys(statement, ID);
        RuntimeException original = new RuntimeException("INVALID_FIELD: original REST detail");
        when(api.insert(eq("ContentVersion"), anyString())).thenThrow(original);
        SQLException failure = sqlException(statement::executeUpdate);
        assertTrue(failure.getMessage().contains(original.getMessage()));
        assertKeys(statement, null);
    }

    @Test
    public void statementsUnwrapToJdbcInterfacesAndRejectIncompatibleTypes() throws Exception {
        PreparedStatement statement = prepare("Title", "?");
        assertTrue(statement.isWrapperFor(Statement.class));
        assertTrue(statement.isWrapperFor(PreparedStatement.class));
        assertSame(statement, statement.unwrap(Statement.class));
        assertSame(statement, statement.unwrap(PreparedStatement.class));
        assertFalse(statement.isWrapperFor(Connection.class));
        sqlException(() -> statement.unwrap(Connection.class));
        Statement ordinary = connection.createStatement();
        assertTrue(ordinary.isWrapperFor(Statement.class));
        assertSame(ordinary, ordinary.unwrap(Statement.class));
        assertFalse(ordinary.isWrapperFor(PreparedStatement.class));
        sqlException(() -> ordinary.unwrap(PreparedStatement.class));
    }

    @Test
    public void metadataAdvertisesGeneratedKeys() throws Exception {
        assertTrue(connection.getMetaData().supportsGetGeneratedKeys());
        assertTrue(connection.getMetaData().generatedKeyAlwaysReturned());
    }

    @Test
    public void moreResultsTerminatesUpdateCountWithoutDiscardingGeneratedKeys() throws Exception {
        PreparedStatement statement = prepare("Title", "?");
        statement.setString(1, "title");
        assertEquals(1, statement.executeUpdate());
        assertFalse(statement.getMoreResults());
        assertEquals(-1, statement.getUpdateCount());
        assertKeys(statement, ID);

        assertEquals(1, statement.executeUpdate());
        assertFalse(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT));
        assertEquals(-1, statement.getUpdateCount());
        assertKeys(statement, ID);
    }

    @Test
    public void dataSourcePropagatesConvertHeicTrueAndFalseProperties() throws Exception {
        final Properties captured = new Properties();
        Driver captureDriver = new Driver() {
            @Override
            public Connection connect(String url, Properties info) {
                if (!acceptsURL(url)) return null;
                captured.clear();
                captured.putAll(info);
                return connection;
            }

            @Override
            public boolean acceptsURL(String url) {
                return "jdbc:insert-test:properties".equals(url);
            }

            @Override
            public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
                return new DriverPropertyInfo[0];
            }

            @Override public int getMajorVersion() { return 1; }
            @Override public int getMinorVersion() { return 0; }
            @Override public boolean jdbcCompliant() { return false; }
            @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        };
        DriverManager.registerDriver(captureDriver);
        try {
            SFDataSource dataSource = new SFDataSource();
            dataSource.setUrl("jdbc:insert-test:properties");
            for (boolean enabled : new boolean[]{true, false}) {
                dataSource.setConvertHeic(enabled);
                assertEquals(enabled, dataSource.getConvertHeic());
                assertSame(connection, dataSource.getConnection());
                assertEquals(Boolean.toString(enabled), captured.getProperty("convertHeic"));
                assertSame(connection, dataSource.getConnection("override-user", "placeholder"));
                assertEquals(Boolean.toString(enabled), captured.getProperty("convertHeic"));
                assertEquals("override-user", captured.getProperty("user"));
            }
        } finally {
            DriverManager.deregisterDriver(captureDriver);
        }
    }
}
