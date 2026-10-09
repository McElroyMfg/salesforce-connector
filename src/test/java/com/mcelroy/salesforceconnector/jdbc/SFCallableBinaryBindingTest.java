package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.rowset.serial.SerialBlob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SFCallableBinaryBindingTest {
    private final SFClientConnection client = mock(SFClientConnection.class);
    private final byte[] bytes = new byte[]{0, 1, (byte) 255};

    private SFCallableStatement statement() {
        when(client.launchFlow(eq("BinaryFlow"), anyString())).thenReturn(new JSONObject());
        return new SFCallableStatement(new SFConnection(null), client,
                "call BinaryFlow(value, date, time, stamp)");
    }

    private JSONObject inputs(SFCallableStatement statement) throws Exception {
        statement.execute();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).launchFlow(eq("BinaryFlow"), body.capture());
        return new JSONObject(body.getValue()).getJSONArray("inputs").getJSONObject(0);
    }

    @Test
    public void bytesAreBase64JsonAndNamedIndexedBindingsKeepLastWrite() throws Exception {
        SFCallableStatement statement = statement();
        statement.setBytes(1, bytes);
        assertEquals("AAH/", inputs(statement).getString("value"));
        statement.setBytes("value", new byte[]{2});
        assertEquals("Ag==", inputs(statement).getString("value"));
        statement.setBytes(1, bytes);
        assertEquals("AAH/", inputs(statement).getString("value"));
        statement.setBytes("value", null);
        assertTrue(inputs(statement).isNull("value"));
        statement.setBytes(1, new byte[0]);
        assertEquals("", inputs(statement).getString("value"));
    }

    @Test
    public void temporalBindingsAreJsonStringsAndCalendarRoutesRemainUnchanged() throws Exception {
        SFCallableStatement statement = statement();
        Date date = Date.valueOf("2025-03-04");
        Time time = Time.valueOf("12:34:56");
        Timestamp stamp = Timestamp.valueOf("2025-03-04 12:34:56.123");
        Calendar calendar = Calendar.getInstance();
        long original = calendar.getTimeInMillis();
        statement.setDate(2, date);
        statement.setTime(3, time);
        statement.setTimestamp(4, stamp);
        assertDates(inputs(statement), date, time, stamp);
        statement.setDate("date", date, calendar);
        statement.setTime("time", time, calendar);
        statement.setTimestamp("stamp", stamp, calendar);
        assertDates(inputs(statement), date, time, stamp);
        statement.setDate(2, date, calendar);
        statement.setTime(3, time, calendar);
        statement.setTimestamp(4, stamp, calendar);
        assertDates(inputs(statement), date, time, stamp);
        statement.setDate("date", null);
        statement.setTime("time", null);
        statement.setTimestamp("stamp", null);
        JSONObject values = inputs(statement);
        assertTrue(values.isNull("date"));
        assertTrue(values.isNull("time"));
        assertTrue(values.isNull("stamp"));
        assertEquals(original, calendar.getTimeInMillis());
    }

    private void assertDates(JSONObject values, Date date, Time time, Timestamp stamp) {
        assertEquals(SFParameterEncoder.formatDate(date, "yyyy-MM-dd"), values.getString("date"));
        assertEquals(SFParameterEncoder.formatDate(time, "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                values.getString("time"));
        assertEquals(SFParameterEncoder.formatDate(stamp, "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                values.getString("stamp"));
    }

    @Test
    public void allStreamAndBlobRoutesProduceBase64AndRespectLength() throws Exception {
        SFCallableStatement statement = statement();
        statement.setBinaryStream(1, new ByteArrayInputStream(bytes), 2);
        assertEquals("AAE=", inputs(statement).getString("value"));
        statement.setBinaryStream("value", new ByteArrayInputStream(bytes), 2L);
        assertEquals("AAE=", inputs(statement).getString("value"));
        statement.setBinaryStream(1, new ByteArrayInputStream(bytes));
        assertEquals("AAH/", inputs(statement).getString("value"));
        statement.setBinaryStream("value", new ByteArrayInputStream(bytes), 2);
        assertEquals("AAE=", inputs(statement).getString("value"));
        statement.setBinaryStream(1, new ByteArrayInputStream(bytes), 2L);
        assertEquals("AAE=", inputs(statement).getString("value"));
        statement.setBinaryStream("value", new ByteArrayInputStream(bytes));
        assertEquals("AAH/", inputs(statement).getString("value"));
        statement.setBlob(1, new SerialBlob(bytes));
        assertEquals("AAH/", inputs(statement).getString("value"));
        statement.setBlob("value", new SerialBlob(bytes));
        assertEquals("AAH/", inputs(statement).getString("value"));
        statement.setBlob(1, new ByteArrayInputStream(bytes), 2L);
        assertEquals("AAE=", inputs(statement).getString("value"));
        statement.setBlob("value", new ByteArrayInputStream(bytes), 2L);
        assertEquals("AAE=", inputs(statement).getString("value"));
        statement.setBlob(1, new ByteArrayInputStream(bytes));
        assertEquals("AAH/", inputs(statement).getString("value"));
        statement.setBlob("value", new ByteArrayInputStream(bytes));
        assertEquals("AAH/", inputs(statement).getString("value"));
    }

    @Test
    public void objectBinaryRoutesReuseByteEncoding() throws Exception {
        SFCallableStatement statement = statement();
        for (Object value : new Object[]{bytes, new ByteArrayInputStream(bytes), new SerialBlob(bytes)}) {
            statement.setObject(1, value);
            assertEquals("AAH/", inputs(statement).getString("value"));
        }
        for (Object value : new Object[]{bytes, new ByteArrayInputStream(bytes), new SerialBlob(bytes)}) {
            statement.setObject("value", value);
            assertEquals("AAH/", inputs(statement).getString("value"));
        }
    }

    @Test
    public void streamOwnershipNullsAndFailuresFollowJdbcContract() throws Exception {
        SFCallableStatement statement = statement();
        TrackingInputStream stream = new TrackingInputStream(bytes);
        statement.setBinaryStream("value", stream, 2L);
        assertEquals(255, stream.read());
        assertFalse(stream.closed);
        Blob blob = mock(Blob.class);
        TrackingInputStream owned = new TrackingInputStream(bytes);
        when(blob.getBinaryStream()).thenReturn(owned);
        when(blob.length()).thenReturn(3L);
        statement.setBlob(1, blob);
        assertTrue(owned.closed);
        statement.setBlob("value", (Blob) null);
        assertTrue(inputs(statement).isNull("value"));
        statement.setBinaryStream(1, null);
        assertTrue(inputs(statement).isNull("value"));
        statement.setBlob("value", (InputStream) null, 0L);
        assertTrue(inputs(statement).isNull("value"));
        fails(() -> statement.setBinaryStream(1, new ByteArrayInputStream(bytes), -1L));
        fails(() -> statement.setBlob("value", new ByteArrayInputStream(bytes), 4L));
        InputStream broken = mock(InputStream.class);
        when(broken.read()).thenThrow(new IOException("broken"));
        when(broken.read(any(byte[].class))).thenThrow(new IOException("broken"));
        when(broken.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException("broken"));
        fails(() -> statement.setBinaryStream("value", broken));
        verify(broken, never()).close();
    }

    private void fails(SqlAction action) throws Exception {
        try {
            action.run();
            fail("Expected SQLException");
        } catch (SQLException expected) {
            // JDBC setters must not leak stream exceptions.
        }
    }

    private interface SqlAction {
        void run() throws Exception;
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
