package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.rowset.serial.SerialBlob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SFResultSetBinaryUpdateTest {
    private final SFClientConnection client = mock(SFClientConnection.class);
    private final SFStatement statement = new SFStatement(new SFConnection(null), client);
    private final byte[] bytes = new byte[]{0, 1, (byte) 255};

    private SFResultSet result() throws Exception {
        when(client.describe("Document")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(new JSONObject().put("name", "Body").put("type", "base64"))
                .put(new JSONObject().put("name", "Name").put("type", "string"))));
        when(client.insert(eq("Document"), anyString())).thenReturn(new JSONObject().put("id", "created"));
        SFResultSet result = new SFResultSet(statement, SQL_Statement.parse("select Id, Body, Name from Document"),
                new JSONObject().put("records", new JSONArray().put(new JSONObject().put("Id", "existing"))));
        assertTrue(result.next());
        return result;
    }

    private JSONObject insert(SFResultSet result) throws Exception {
        result.insertRow();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).insert(eq("Document"), body.capture());
        return new JSONObject(body.getValue());
    }

    @Test
    public void explicitNullAndBigDecimalNullRemainPresent() throws Exception {
        SFResultSet result = result();
        result.updateNull("Name");
        result.updateBigDecimal(2, null);
        JSONObject body = insert(result);
        assertTrue(body.has("Name"));
        assertTrue(body.isNull("Name"));
        assertTrue(body.has("Body"));
        assertTrue(body.isNull("Body"));
        result.updateBytes("Body", null);
        body = insert(result);
        assertTrue(body.has("Body"));
        assertTrue(body.isNull("Body"));
    }

    @Test
    public void byteAndObjectBinaryBindingsEncodeOnlyAtWrite() throws Exception {
        SFResultSet result = result();
        result.updateBytes(2, bytes);
        assertEquals("AAH/", insert(result).getString("Body"));
        for (Object value : new Object[]{bytes, new ByteArrayInputStream(bytes), new SerialBlob(bytes)}) {
            result.updateObject("Body", value);
            assertEquals("AAH/", insert(result).getString("Body"));
        }
        result.updateObject(2, bytes, 0);
        assertEquals("AAH/", insert(result).getString("Body"));
        result.updateObject("Body", null);
        assertTrue(insert(result).isNull("Body"));
    }

    @Test
    public void everyBinaryStreamAndBlobOverloadRoutesThroughBytes() throws Exception {
        SFResultSet result = result();
        result.updateBinaryStream(2, new ByteArrayInputStream(bytes), 2);
        assertEquals("AAE=", insert(result).getString("Body"));
        result.updateBinaryStream("Body", new ByteArrayInputStream(bytes), 2);
        assertEquals("AAE=", insert(result).getString("Body"));
        result.updateBinaryStream(2, new ByteArrayInputStream(bytes), 2L);
        assertEquals("AAE=", insert(result).getString("Body"));
        result.updateBinaryStream("Body", new ByteArrayInputStream(bytes), 2L);
        assertEquals("AAE=", insert(result).getString("Body"));
        result.updateBinaryStream(2, new ByteArrayInputStream(bytes));
        assertEquals("AAH/", insert(result).getString("Body"));
        result.updateBinaryStream("Body", new ByteArrayInputStream(bytes));
        assertEquals("AAH/", insert(result).getString("Body"));
        result.updateBlob(2, new SerialBlob(bytes));
        assertEquals("AAH/", insert(result).getString("Body"));
        result.updateBlob("Body", new SerialBlob(bytes));
        assertEquals("AAH/", insert(result).getString("Body"));
        result.updateBlob(2, new ByteArrayInputStream(bytes), 2L);
        assertEquals("AAE=", insert(result).getString("Body"));
        result.updateBlob("Body", new ByteArrayInputStream(bytes), 2L);
        assertEquals("AAE=", insert(result).getString("Body"));
        result.updateBlob(2, new ByteArrayInputStream(bytes));
        assertEquals("AAH/", insert(result).getString("Body"));
        result.updateBlob("Body", new ByteArrayInputStream(bytes));
        assertEquals("AAH/", insert(result).getString("Body"));
    }

    @Test
    public void updateWritesBase64AndInsertCapturesGeneratedId() throws Exception {
        SFResultSet result = result();
        result.updateBytes("Body", bytes);
        result.updateRow();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(client).update(eq("Document/existing"), body.capture());
        assertEquals("AAH/", new JSONObject(body.getValue()).getString("Body"));
        result.updateBytes("Body", bytes);
        insert(result);
        assertEquals("created", statement.generatedId);
    }

    @Test
    public void binaryOnNonBase64FieldFailsBeforeRestAndRetainsBinding() throws Exception {
        SFResultSet result = result();
        result.updateBytes("Name", bytes);
        fails(result::insertRow);
        fails(result::updateRow);
        verify(client, never()).insert(anyString(), anyString());
        verify(client, never()).update(anyString(), anyString());
        result.updateString("Name", "ordinary");
        assertEquals("ordinary", insert(result).getString("Name"));
    }

    @Test
    public void restFailuresPreserveMessageCauseAndPendingBindings() throws Exception {
        SFResultSet result = result();
        insert(result);
        assertEquals("created", statement.generatedId);
        RuntimeException failure = new RuntimeException("REST unavailable");
        when(client.insert(eq("Document"), anyString())).thenThrow(failure);
        result.updateBytes("Body", bytes);
        SQLException error = fails(result::insertRow);
        assertEquals(failure.getMessage(), error.getMessage());
        assertSame(failure, error.getCause());
        assertNull(statement.generatedId);
        doThrow(failure).when(client).update(eq("Document/existing"), anyString());
        error = fails(result::updateRow);
        assertEquals(failure.getMessage(), error.getMessage());
        assertSame(failure, error.getCause());
        when(client.insert(eq("Document"), anyString())).thenReturn(new JSONObject().put("id", "retry"));
        assertEquals("AAH/", insert(result).getString("Body"));
    }

    @Test
    public void wrapperMethodsSupportCompatibleTypesOnly() throws Exception {
        SFResultSet result = result();
        assertTrue(result.isWrapperFor(ResultSet.class));
        assertTrue(result.isWrapperFor(SFResultSet.class));
        assertSame(result, result.unwrap(ResultSet.class));
        assertFalse(result.isWrapperFor(Statement.class));
        fails(() -> result.unwrap(Statement.class));
    }

    @Test
    public void callerStreamsStayOpenAndInvalidLengthsFail() throws Exception {
        SFResultSet result = result();
        TrackingInputStream stream = new TrackingInputStream(bytes);
        result.updateBinaryStream("Body", stream, 2L);
        assertEquals(255, stream.read());
        assertFalse(stream.closed);
        assertEquals("AAE=", insert(result).getString("Body"));
        fails(() -> result.updateBinaryStream(2, new ByteArrayInputStream(bytes), -1L));
        fails(() -> result.updateBlob("Body", new ByteArrayInputStream(bytes), 4L));
        result.updateBinaryStream("Body", null);
        assertTrue(insert(result).isNull("Body"));
        result.updateBlob(2, (Blob) null);
        assertTrue(insert(result).isNull("Body"));
    }

    private SQLException fails(SqlAction action) throws Exception {
        try {
            action.run();
            fail("Expected SQLException");
        } catch (SQLException error) {
            return error;
        }
        throw new AssertionError();
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
