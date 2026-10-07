// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SFCallableStatementTest {
    private final SFClientConnection client = mock(SFClientConnection.class);
    private final SFConnection connection = new SFConnection(null);

    private JSONObject description(String[] inputs, String[] outputs) {
        JSONObject result = new JSONObject();
        for (String direction : new String[]{"inputs", "outputs"}) {
            JSONArray names = new JSONArray();
            for (String name : direction.equals("inputs") ? inputs : outputs)
                names.put(new JSONObject().put("name", name));
            result.put(direction, names);
        }
        return result;
    }

    private SFCallableStatement statement(String sql, JSONObject outputs) {
        when(client.launchFlow(eq("MyFlow"), anyString())).thenReturn(
                new JSONObject().put("isSuccess", true).put("outputValues", outputs));
        return new SFCallableStatement(connection, client, sql);
    }

    private JSONObject launchedInputs() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).launchFlow(eq("MyFlow"), body.capture());
        return new JSONObject(body.getValue()).getJSONArray("inputs").getJSONObject(0);
    }

    @Test
    public void bareAndPlaceholderCallsUseDescribeOrderAndConnectionCache() throws Exception {
        when(client.describeFlow("MyFlow")).thenReturn(description(
                new String[]{"second", "first"}, new String[]{"answer"}));
        for (String sql : new String[]{"{call MyFlow}", "{call MyFlow(?, ?)}", "call MyFlow()"}) {
            SFCallableStatement statement = statement(sql, new JSONObject());
            statement.setString(1, "alpha");
            statement.setInt(2, 42);
            assertFalse(statement.execute());
            JSONObject inputs = launchedInputs();
            assertEquals("alpha", inputs.getString("second"));
            assertEquals(42, inputs.getInt("first"));
            assertFalse(inputs.has("?"));
        }
        verify(client, times(1)).describeFlow("MyFlow");
    }

    @Test
    public void explicitLegacyInputNamesDoNotRequireDescribe() throws Exception {
        SFCallableStatement statement = statement("call MyFlow(namedInput, other)", new JSONObject());
        statement.setString(1, "value");
        statement.setBoolean("other", true);
        statement.execute();
        assertEquals("value", launchedInputs().getString("namedInput"));
        assertTrue(launchedInputs().getBoolean("other"));
        verify(client, never()).describeFlow(anyString());
    }

    @Test
    public void parameterArrayAliasesAreSupported() throws Exception {
        JSONObject metadata = description(new String[]{"input"}, new String[]{"answer"});
        metadata.put("inputParameters", metadata.remove("inputs"));
        metadata.put("outputParameters", metadata.remove("outputs"));
        when(client.describeFlow("MyFlow")).thenReturn(metadata);
        SFCallableStatement statement = statement("{call MyFlow(?)}", new JSONObject().put("answer", 9));
        statement.setInt(1, 3);
        statement.execute();
        assertEquals(3, launchedInputs().getInt("input"));
        assertEquals(9, statement.getInt(1));
    }

    @Test
    public void bareNamedSettersDescribeInputsAndRetainNamedBindings() throws Exception {
        when(client.describeFlow("MyFlow")).thenReturn(
                description(new String[]{"other", "record"}, new String[0]));
        SFCallableStatement statement = statement("call MyFlow", new JSONObject());
        statement.setObject("record", Collections.singletonMap("Name", "Acme"));
        statement.execute();
        assertEquals("Acme", launchedInputs().getJSONObject("record").getString("Name"));
        assertEquals(java.util.Arrays.asList("other", "record"), statement.inputNames);
        verify(client, times(1)).describeFlow("MyFlow");
    }

    @Test
    public void missingDescribeAllowsBareCallWithoutInputs() throws Exception {
        SFCallableStatement statement = statement("{call MyFlow}", new JSONObject());
        statement.execute();
        assertEquals(0, launchedInputs().length());
        expectSqlException(() -> statement.getObject(1));
    }

    @Test
    public void indexedOutputsFollowDescribeNotInputOrJsonOrder() throws Exception {
        when(client.describeFlow("MyFlow")).thenReturn(description(
                new String[]{"input"}, new String[]{"zeta", "alpha"}));
        SFCallableStatement statement = statement("call MyFlow(input)",
                new JSONObject().put("alpha", "second").put("zeta", 12));
        statement.execute();
        assertEquals(12, statement.getInt(1));
        assertEquals("second", statement.getString(2));
        assertEquals(Integer.valueOf(12), statement.getObject(1, Integer.class));
        assertEquals(new BigDecimal("12.00"), statement.getBigDecimal(1, 2));
        verify(client, times(1)).describeFlow("MyFlow");
    }

    @Test
    public void namedTypedGettersAndNullTrackingReadOutputValues() throws Exception {
        JSONObject object = new JSONObject().put("id", "001");
        SFCallableStatement statement = statement("call MyFlow(input)", new JSONObject()
                .put("number", "123.75").put("bool", true).put("object", object)
                .put("text", "abc").put("empty", JSONObject.NULL));
        statement.execute();
        assertEquals(123, statement.getByte("number"));
        assertEquals(123, statement.getShort("number"));
        assertEquals(123, statement.getInt("number"));
        assertEquals(123L, statement.getLong("number"));
        assertEquals(123.75f, statement.getFloat("number"), 0);
        assertEquals(123.75, statement.getDouble("number"), 0);
        assertEquals(new BigDecimal("123.75"), statement.getBigDecimal("number"));
        assertTrue(statement.getBoolean("bool"));
        assertSame(object, statement.getObject("object"));
        assertEquals(Integer.valueOf(123), statement.getObject("number", Integer.class));
        assertEquals(Boolean.TRUE, statement.getObject("bool", Boolean.class));
        assertArrayEquals(new byte[]{97, 98, 99}, statement.getBytes("text"));
        assertEquals("abc", statement.getNString("text"));
        assertFalse(statement.wasNull());
        assertEquals(0, statement.getInt("empty"));
        assertTrue(statement.wasNull());
        assertNull(statement.getObject("empty", Integer.class));
        assertTrue(statement.wasNull());
        assertNull(statement.getBytes("empty"));
        assertTrue(statement.wasNull());
        assertNull(statement.getString("missing"));
        assertTrue(statement.wasNull());
        assertTrue(statement.getBoolean("bool"));
        assertFalse(statement.wasNull());
        verify(client, never()).describeFlow(anyString());
    }

    @Test
    public void temporalGettersHonorOffsetsAndCalendarWithoutChangingCalendar() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            SFCallableStatement statement = statement("call MyFlow(input)", new JSONObject()
                    .put("date", "2024-01-30")
                    .put("utc", "2024-01-30T12:34:56.123Z")
                    .put("offset", "2024-01-30T14:34:56.123+0200")
                    .put("local", "2024-01-30T12:34:56.123")
                    .put("time", "12:34:56.123Z")
                    .put("empty", JSONObject.NULL));
            statement.execute();
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            long original = utc.getTimeInMillis();
            assertEquals(Date.valueOf("2024-01-30"), statement.getDate("date"));
            assertEquals(Instant.parse("2024-01-30T00:00:00Z").toEpochMilli(),
                    statement.getDate("date", utc).getTime());
            Timestamp expected = Timestamp.from(Instant.parse("2024-01-30T12:34:56.123Z"));
            assertEquals(expected, statement.getTimestamp("utc"));
            assertEquals(expected, statement.getTimestamp("offset", utc));
            assertEquals(expected, statement.getTimestamp("local", utc));
            assertEquals(expected, statement.getObject("utc", Timestamp.class));
            assertEquals(new Time(expected.getTime()), statement.getTime("utc"));
            assertEquals(Instant.parse("1970-01-01T12:34:56.123Z").toEpochMilli(),
                    statement.getTime("time").getTime());
            assertEquals(original, utc.getTimeInMillis());
            assertNull(statement.getDate("empty"));
            assertTrue(statement.wasNull());
            assertNull(statement.getTimestamp("empty"));
            assertTrue(statement.wasNull());
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    public void invalidOutputIndexesAndConversionsThrowSqlException() throws Exception {
        when(client.describeFlow("MyFlow")).thenReturn(description(new String[0], new String[]{"bad"}));
        SFCallableStatement statement = statement("call MyFlow(input)",
                new JSONObject().put("bad", "not a number or date"));
        statement.execute();
        expectSqlException(() -> statement.getObject(0));
        expectSqlException(() -> statement.getString(-1));
        expectSqlException(() -> statement.getInt(2));
        expectSqlException(() -> statement.getBlob(2));
        expectSqlException(() -> statement.getDouble("bad"));
        expectSqlException(() -> statement.getTimestamp("bad"));
        expectSqlException(() -> statement.getDate("bad"));
        expectSqlException(() -> statement.getObject("bad", Calendar.class));
    }

    @Test
    public void invalidInputIndexThrowsSqlExceptionRatherThanDroppingValue() throws Exception {
        when(client.describeFlow("MyFlow")).thenReturn(description(new String[]{"input"}, new String[0]));
        SFCallableStatement statement = statement("{call MyFlow(?)}", new JSONObject());
        statement.setInt(2, 1);
        expectSqlException(statement::execute);
        verify(client, never()).launchFlow(anyString(), anyString());
    }

    @Test
    public void unsuccessfulLaunchEvictsDescribeAndRefreshesInputNames() throws Exception {
        SFCallableStatement statement = statement("{call MyFlow}", new JSONObject().put("answer", "ok"));
        when(client.describeFlow("MyFlow")).thenReturn(
                description(new String[]{"old"}, new String[0]),
                description(new String[]{"new"}, new String[0]));
        when(client.launchFlow(eq("MyFlow"), anyString())).thenReturn(
                new JSONObject().put("isSuccess", false).put("errors", "changed flow"),
                new JSONObject().put("isSuccess", true));
        statement.setString(1, "value");
        expectSqlException(statement::execute);
        assertNull(statement.getString("answer"));
        statement.execute();
        assertEquals("value", launchedInputs().getString("new"));
        verify(client, times(2)).describeFlow("MyFlow");
    }

    @Test
    public void runtimeLaunchFailureEvictsDescribeAndRetainsCause() throws Exception {
        SFCallableStatement statement = statement("{call MyFlow(?)}", new JSONObject());
        when(client.describeFlow("MyFlow")).thenReturn(description(new String[]{"input"}, new String[0]));
        IllegalStateException cause = new IllegalStateException("launch failed");
        when(client.launchFlow(eq("MyFlow"), anyString())).thenThrow(cause)
                .thenReturn(new JSONObject().put("isSuccess", true));
        statement.setInt(1, 7);
        try {
            statement.execute();
            fail("Expected SQLException");
        } catch (SQLException e) {
            assertSame(cause, e.getCause());
        }
        statement.execute();
        verify(client, times(2)).describeFlow("MyFlow");
    }

    @Test
    public void explicitCallFailureEvictsPreviouslyCachedDescribe() throws Exception {
        when(client.describeFlow("MyFlow")).thenReturn(description(new String[]{"input"}, new String[0]));
        SFCallableStatement positional = statement("{call MyFlow(?)}", new JSONObject());
        positional.setInt(1, 1);
        positional.execute();
        SFCallableStatement explicit = statement("call MyFlow(input)", new JSONObject());
        when(client.launchFlow(eq("MyFlow"), anyString())).thenReturn(
                new JSONObject().put("isSuccess", false),
                new JSONObject().put("isSuccess", true));
        expectSqlException(explicit::execute);
        positional.execute();
        verify(client, times(2)).describeFlow("MyFlow");
    }

    @Test
    public void namedBindingsSurviveUncachedDescribeReorderingAndMixWithIndexes() throws Exception {
        SFConnection uncached = new SFConnection(null, null, 0, 500);
        when(client.describeFlow("MyFlow")).thenReturn(
                description(new String[]{"a", "b"}, new String[0]),
                description(new String[]{"b", "a"}, new String[0]));
        when(client.launchFlow(eq("MyFlow"), anyString())).thenReturn(new JSONObject().put("isSuccess", true));
        SFCallableStatement statement = new SFCallableStatement(uncached, client, "{call MyFlow(?, ?)}");
        statement.setString(2, "indexed");
        statement.setString("a", "named");
        statement.execute();
        assertEquals("named", launchedInputs().getString("a"));
        assertEquals("indexed", launchedInputs().getString("b"));
        statement.setString(1, "first");
        statement.execute();
        assertEquals("named", launchedInputs().getString("a"));
        assertEquals("first", launchedInputs().getString("b"));
        verify(client, times(2)).describeFlow("MyFlow");
        statement.clearParameters();
        statement.execute();
        assertEquals(0, launchedInputs().length());
    }

    @Test
    public void namedBindingsSurviveFailureEvictionReordering() throws Exception {
        SFCallableStatement statement = statement("{call MyFlow(?, ?)}", new JSONObject());
        when(client.describeFlow("MyFlow")).thenReturn(
                description(new String[]{"a", "b"}, new String[0]),
                description(new String[]{"b", "a"}, new String[0]));
        when(client.launchFlow(eq("MyFlow"), anyString())).thenReturn(
                new JSONObject().put("isSuccess", false), new JSONObject().put("isSuccess", true));
        statement.setString("a", "named");
        expectSqlException(statement::execute);
        statement.execute();
        assertEquals("named", launchedInputs().getString("a"));
        assertFalse(launchedInputs().has("b"));
    }

    @Test
    public void bareCallsCanMixIndexedAndNamedInputs() throws Exception {
        when(client.describeFlow("MyFlow")).thenReturn(
                description(new String[]{"first", "other"}, new String[0]));
        SFCallableStatement statement = statement("call MyFlow", new JSONObject());
        statement.setString(1, "x");
        statement.setInt("other", 2);
        statement.execute();
        assertEquals("x", launchedInputs().getString("first"));
        assertEquals(2, launchedInputs().getInt("other"));
        verify(client, times(1)).describeFlow("MyFlow");
    }

    @Test
    public void explicitInputNamesKeepLastSetterWinsWhenMixingBindings() throws Exception {
        SFCallableStatement statement = statement("call MyFlow(input)", new JSONObject());
        statement.setString("input", "older named");
        statement.setString(1, "newer indexed");
        statement.execute();
        assertEquals("newer indexed", launchedInputs().getString("input"));
        statement.setString("input", "newest named");
        statement.execute();
        assertEquals("newest named", launchedInputs().getString("input"));
        statement.setInt(1, 42);
        statement.execute();
        assertEquals(42, launchedInputs().getInt("input"));
        verify(client, never()).describeFlow(anyString());
    }

    @Test
    public void integralGettersCheckBoundsAfterTruncatingFractions() throws Exception {
        SFCallableStatement statement = statement("call MyFlow(input)", new JSONObject()
                .put("byteMax", "127.99").put("byteMin", "-128.99")
                .put("byteOverflow", "128").put("byteUnderflow", "-129")
                .put("shortMax", "32767.99").put("shortMin", "-32768.99")
                .put("shortOverflow", "32768").put("shortUnderflow", "-32769")
                .put("intMax", "2147483647.99").put("intMin", "-2147483648.99")
                .put("intOverflow", "2147483648").put("intUnderflow", "-2147483649")
                .put("longMax", "9223372036854775807.99").put("longMin", "-9223372036854775808.99")
                .put("longOverflow", "9223372036854775808").put("longUnderflow", "-9223372036854775809"));
        statement.execute();
        assertEquals(Byte.MAX_VALUE, statement.getByte("byteMax"));
        assertEquals(Byte.MIN_VALUE, statement.getByte("byteMin"));
        assertEquals(Short.MAX_VALUE, statement.getShort("shortMax"));
        assertEquals(Short.MIN_VALUE, statement.getShort("shortMin"));
        assertEquals(Integer.MAX_VALUE, statement.getInt("intMax"));
        assertEquals(Integer.MIN_VALUE, statement.getInt("intMin"));
        assertEquals(Long.MAX_VALUE, statement.getLong("longMax"));
        assertEquals(Long.MIN_VALUE, statement.getLong("longMin"));
        for (String name : new String[]{"byteOverflow", "byteUnderflow"}) {
            expectSqlException(() -> statement.getByte(name));
            expectSqlException(() -> statement.getObject(name, Byte.class));
        }
        for (String name : new String[]{"shortOverflow", "shortUnderflow"}) {
            expectSqlException(() -> statement.getShort(name));
            expectSqlException(() -> statement.getObject(name, Short.class));
        }
        for (String name : new String[]{"intOverflow", "intUnderflow"}) {
            expectSqlException(() -> statement.getInt(name));
            expectSqlException(() -> statement.getObject(name, Integer.class));
        }
        for (String name : new String[]{"longOverflow", "longUnderflow"}) {
            expectSqlException(() -> statement.getLong(name));
            expectSqlException(() -> statement.getObject(name, Long.class));
        }
    }

    private interface SqlAction {
        void run() throws SQLException;
    }

    private void expectSqlException(SqlAction action) throws Exception {
        try {
            action.run();
            fail("Expected SQLException");
        } catch (SQLException expected) {
            // Expected JDBC validation failure.
        }
    }
}
