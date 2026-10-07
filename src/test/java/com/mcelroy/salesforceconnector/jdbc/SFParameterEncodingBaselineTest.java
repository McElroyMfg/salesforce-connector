package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SFParameterEncodingBaselineTest {
    private TimeZone originalTimeZone;
    private final SFClientConnection client = mock(SFClientConnection.class);
    private final SFConnection connection = new SFConnection(null);
    private static final long INSTANT = Instant.parse("2024-01-30T12:34:56.123Z").toEpochMilli();
    private static final String TEMPORAL = "2024-01-30T12:34:56.123+0000";

    private interface Binding {
        void bind(PreparedStatement statement) throws Exception;
    }

    @Before
    public void useUtc() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        when(client.query(anyString())).thenReturn(new JSONObject().put("records", new JSONArray()));
        when(client.launchFlow(eq("MyFlow"), anyString()))
                .thenReturn(new JSONObject().put("isSuccess", true));
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    private void assertSelect(String literal, Binding binding) throws Exception {
        clearInvocations(client);
        PreparedStatement statement = new SFPreparedStatement(connection, client,
                "select Id from Account where Value = ?");
        binding.bind(statement);
        statement.executeQuery();
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(client).query(query.capture());
        String expected = "SELECT Id FROM Account WHERE Value = " + literal;
        assertEquals(expected, query.getValue());
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8),
                query.getValue().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void selectNumericAndBooleanSettersRetainExactLiterals() throws Exception {
        assertSelect("true", s -> s.setBoolean(1, true));
        assertSelect("false", s -> s.setBoolean(1, false));
        // The existing byte setter delegates to setString, unlike the other numeric setters.
        assertSelect("'-128'", s -> s.setByte(1, (byte) -128));
        assertSelect("-32768", s -> s.setShort(1, Short.MIN_VALUE));
        assertSelect("-2147483648", s -> s.setInt(1, Integer.MIN_VALUE));
        assertSelect("9223372036854775807", s -> s.setLong(1, Long.MAX_VALUE));
        assertSelect("0.10000000149011612", s -> s.setFloat(1, 0.1f));
        assertSelect("-123.125", s -> s.setDouble(1, -123.125));
        assertSelect("1.25E20", s -> s.setDouble(1, 1.25e20));
        assertSelect("123.4500", s -> s.setBigDecimal(1, new BigDecimal("123.4500")));
        assertSelect("1E+20", s -> s.setBigDecimal(1, new BigDecimal("1E+20")));
    }

    @Test
    public void selectStringAliasesEscapingUrlAndBytesRetainExactLiterals() throws Exception {
        String text = "O'Reilly\\folder\n雪";
        String escaped = "'O\\'Reilly\\\\folder\n雪'";
        assertSelect(escaped, s -> s.setString(1, text));
        assertSelect(escaped, s -> s.setNString(1, text));
        assertSelect("''", s -> s.setString(1, ""));
        assertSelect("'https://example.com/a?x=O\\'Reilly'",
                s -> s.setURL(1, new URL("https://example.com/a?x=O'Reilly")));
        byte[] bytes = {0, 1, -1};
        // Pin the current identity-string encoding rather than introduce a binary representation.
        assertSelect("'" + bytes.toString() + "'", s -> s.setBytes(1, bytes));
        assertSelect("'null'", s -> s.setBytes(1, null));
    }

    @Test
    public void selectTemporalSettersAndCalendarOverloadsRetainExactLiterals() throws Exception {
        Date date = new Date(INSTANT);
        Time time = new Time(INSTANT);
        Timestamp timestamp = new Timestamp(INSTANT);
        timestamp.setNanos(123456789);
        Calendar otherZone = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Honolulu"));
        long calendarBefore = otherZone.getTimeInMillis();
        assertSelect("2024-01-30", s -> s.setDate(1, date));
        assertSelect(TEMPORAL, s -> s.setTime(1, time));
        assertSelect(TEMPORAL, s -> s.setTimestamp(1, timestamp));
        assertSelect("2024-01-30", s -> s.setDate(1, date, otherZone));
        assertSelect(TEMPORAL, s -> s.setTime(1, time, otherZone));
        assertSelect(TEMPORAL, s -> s.setTimestamp(1, timestamp, otherZone));
        assertEquals(calendarBefore, otherZone.getTimeInMillis());
    }

    @Test
    public void selectSupportedNullSettersRetainUnquotedNull() throws Exception {
        assertSelect("null", s -> s.setNull(1, Types.VARCHAR));
        assertSelect("null", s -> s.setNull(1, Types.OTHER, "ignored"));
        assertSelect("null", s -> s.setDate(1, null));
        assertSelect("null", s -> s.setTime(1, null));
        assertSelect("null", s -> s.setTimestamp(1, null));
        Calendar calendar = Calendar.getInstance();
        assertSelect("null", s -> s.setDate(1, null, calendar));
        assertSelect("null", s -> s.setTime(1, null, calendar));
        assertSelect("null", s -> s.setTimestamp(1, null, calendar));
        assertSelect("null", s -> s.setObject(1, null));
        assertSelect("null", s -> s.setObject(1, null, Types.VARCHAR));
    }

    @Test
    public void selectEveryExistingObjectDispatchRetainsExactLiterals() throws Exception {
        Object[][] cases = {
                {"O'Reilly\\雪", "'O\\'Reilly\\\\雪'"},
                {new BigDecimal("123.4500"), "123.4500"},
                {Long.valueOf(9007199254740993L), "9007199254740993"},
                {Double.valueOf(123.125), "123.125"},
                {Boolean.TRUE, "true"},
                {new Timestamp(INSTANT), TEMPORAL},
                {new Time(INSTANT), TEMPORAL},
                {new Date(INSTANT), "2024-01-30"},
                {new java.util.Date(INSTANT), "2024-01-30"},
                {Short.valueOf((short) -12), "-12"},
                {Integer.valueOf(-123), "-123"},
                {Float.valueOf(0.1f), "0.10000000149011612"},
                {Byte.valueOf((byte) 7), "'7'"},
                {new Object() {
                    @Override
                    public String toString() {
                        return "fallback'\\雪";
                    }
                }, "'fallback\\'\\\\雪'"}
        };
        for (Object[] testCase : cases) {
            assertSelect((String) testCase[1], s -> s.setObject(1, testCase[0]));
            assertSelect((String) testCase[1], s -> s.setObject(1, testCase[0], Types.VARCHAR));
        }
    }

    @Test
    public void selectMultipleParametersRetainOrderAndLastSetterWins() throws Exception {
        PreparedStatement statement = new SFPreparedStatement(connection, client,
                "select Id from Account where Name = ? and Active = ? and Amount > ? limit ? offset ?");
        statement.setLong(3, 99);
        statement.setInt(5, 2);
        statement.setInt(4, 10);
        statement.setBoolean(2, false);
        statement.setString(1, "old");
        statement.setString(1, "O'Reilly");
        statement.setBigDecimal(3, new BigDecimal("12.3400"));
        statement.executeQuery();
        verify(client).query("SELECT Id FROM Account WHERE Name = 'O\\'Reilly' "
                + "AND Active = false AND Amount > 12.3400 LIMIT 10 OFFSET 2");
    }

    private String launch(SFCallableStatement statement) throws Exception {
        clearInvocations(client);
        assertFalse(statement.execute());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(client).launchFlow(eq("MyFlow"), body.capture());
        return body.getValue();
    }

    @Test
    public void flowNamedAndIndexedScalarSettersRetainExactJson() throws Exception {
        for (boolean named : new boolean[]{false, true}) {
            SFCallableStatement statement = new SFCallableStatement(connection, client, "call MyFlow(value)");
            String text = "O'Reilly\\folder\n\"雪\"";
            if (named) statement.setString("value", text);
            else statement.setString(1, text);
            assertEquals("{\"inputs\":[{\"value\":\"O'Reilly\\\\folder\\n\\\"雪\\\"\"}]}", launch(statement));
            if (named) statement.setLong("value", 9007199254740993L);
            else statement.setLong(1, 9007199254740993L);
            assertEquals("{\"inputs\":[{\"value\":9007199254740993}]}", launch(statement));
            if (named) statement.setBoolean("value", true);
            else statement.setBoolean(1, true);
            assertEquals("{\"inputs\":[{\"value\":true}]}", launch(statement));
            if (named) statement.setBoolean("value", false);
            else statement.setBoolean(1, false);
            assertEquals("{\"inputs\":[{\"value\":false}]}", launch(statement));
            if (named) statement.setNull("value", Types.OTHER);
            else statement.setNull(1, Types.OTHER);
            assertEquals("{\"inputs\":[{\"value\":null}]}", launch(statement));
            if (named) statement.setNull("value", Types.OTHER, "ignored");
            else statement.setNull(1, Types.OTHER, "ignored");
            assertEquals("{\"inputs\":[{\"value\":null}]}", launch(statement));
        }
        verify(client, never()).describeFlow(anyString());
    }

    public static class FlowBean {
        public String getName() {
            return "O'Reilly\\雪";
        }

        public long getCount() {
            return 9007199254740993L;
        }

        public boolean isActive() {
            return true;
        }
    }

    @Test
    public void flowNamedAndIndexedObjectsRetainMapListAndBeanJsonTypes() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "O'Reilly\\雪");
        map.put("count", 9007199254740993L);
        map.put("active", true);
        map.put("empty", JSONObject.NULL);
        Object list = Arrays.asList("text", 9007199254740993L, true, null, map);
        for (boolean named : new boolean[]{false, true}) {
            for (boolean typed : new boolean[]{false, true}) {
                SFCallableStatement statement = new SFCallableStatement(connection, client,
                        "call MyFlow(record, items, bean)");
                Object[] objects = {map, list, new FlowBean()};
                String[] names = {"record", "items", "bean"};
                for (int i = 0; i < objects.length; i++) {
                    if (named && typed) statement.setObject(names[i], objects[i], Types.JAVA_OBJECT);
                    else if (named) statement.setObject(names[i], objects[i]);
                    else if (typed) statement.setObject(i + 1, objects[i], Types.JAVA_OBJECT);
                    else statement.setObject(i + 1, objects[i]);
                }
                JSONObject inputs = new JSONObject(launch(statement)).getJSONArray("inputs").getJSONObject(0);
                assertEquals(3, inputs.length());
                JSONObject record = inputs.getJSONObject("record");
                assertEquals("O'Reilly\\雪", record.get("name"));
                assertEquals(Long.valueOf(9007199254740993L), record.get("count"));
                assertEquals(Boolean.TRUE, record.get("active"));
                assertTrue(record.has("empty"));
                assertSame(JSONObject.NULL, record.get("empty"));
                JSONArray items = inputs.getJSONArray("items");
                assertEquals(5, items.length());
                assertEquals("text", items.get(0));
                assertEquals(Long.valueOf(9007199254740993L), items.get(1));
                assertEquals(Boolean.TRUE, items.get(2));
                assertSame(JSONObject.NULL, items.get(3));
                assertTrue(record.similar(items.getJSONObject(4)));
                JSONObject bean = inputs.getJSONObject("bean");
                assertEquals(3, bean.length());
                assertEquals("O'Reilly\\雪", bean.get("name"));
                assertEquals(Long.valueOf(9007199254740993L), bean.get("count"));
                assertEquals(Boolean.TRUE, bean.get("active"));
            }
        }
    }

    @Test
    public void flowMixedBindingsRetainLastSetterPrecedenceAndUnrelatedInputs() throws Exception {
        SFCallableStatement statement = new SFCallableStatement(connection, client,
                "call MyFlow(value, other)");
        statement.setLong(2, 42);
        statement.setString(1, "indexed old");
        statement.setString("value", "named newer");
        statement.setBoolean("extra", true);
        JSONObject inputs = new JSONObject(launch(statement)).getJSONArray("inputs").getJSONObject(0);
        assertEquals(3, inputs.length());
        assertEquals("named newer", inputs.get("value"));
        assertEquals(Integer.valueOf(42), inputs.get("other"));
        assertEquals(Boolean.TRUE, inputs.get("extra"));

        statement.setString(1, "indexed newest");
        inputs = new JSONObject(launch(statement)).getJSONArray("inputs").getJSONObject(0);
        assertEquals("indexed newest", inputs.get("value"));
        assertEquals(Integer.valueOf(42), inputs.get("other"));
        assertEquals(Boolean.TRUE, inputs.get("extra"));

        statement.setNull("value", Types.VARCHAR);
        inputs = new JSONObject(launch(statement)).getJSONArray("inputs").getJSONObject(0);
        assertTrue(inputs.has("value"));
        assertSame(JSONObject.NULL, inputs.get("value"));
        statement.setBoolean(1, false);
        inputs = new JSONObject(launch(statement)).getJSONArray("inputs").getJSONObject(0);
        assertEquals(Boolean.FALSE, inputs.get("value"));

        statement.clearParameters();
        assertEquals("{\"inputs\":[{}]}", launch(statement));
        statement.setString("value", "fresh named");
        statement.setLong(1, 7);
        assertEquals("{\"inputs\":[{\"value\":7}]}", launch(statement));
        statement.setString("value", "freshest named");
        assertEquals("{\"inputs\":[{\"value\":\"freshest named\"}]}", launch(statement));
    }
}
