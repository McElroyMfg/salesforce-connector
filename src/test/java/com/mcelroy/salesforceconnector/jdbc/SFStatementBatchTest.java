package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClient;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;

import java.sql.BatchUpdateException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SFStatementBatchTest {
    private final SFClient client = mock(SFClient.class);
    private final SFClientConnection api = mock(SFClientConnection.class);
    private SFConnection connection;
    private int nextId;

    private final Answer<JSONArray> succeed = invocation -> {
        JSONArray records = invocation.getArgument(invocation.getArguments().length - 2);
        JSONArray results = new JSONArray();
        for (int i = 0; i < records.length(); i++)
            results.put(new JSONObject().put("id", "ID" + nextId++).put("success", true)
                    .put("errors", new JSONArray()));
        return results;
    };

    @Before
    public void setUp() {
        when(client.getConnection(isNull())).thenReturn(api);
        when(api.insertCollection(anyString(), any(JSONArray.class), anyBoolean())).thenAnswer(succeed);
        when(api.upsertCollection(anyString(), anyString(), any(JSONArray.class), anyBoolean())).thenAnswer(succeed);
        when(api.insert(anyString(), anyString())).thenAnswer(i -> new JSONObject().put("id", "SINGLE" + nextId++));
        connection = new SFConnection(client);
    }

    private static List<String> keys(Statement statement) throws SQLException {
        List<String> ids = new ArrayList<>();
        ResultSet keys = statement.getGeneratedKeys();
        while (keys.next())
            ids.add(keys.getString("Id"));
        return ids;
    }

    @Test
    public void preparedInsertBatchIsChunkedAtCollectionLimit() throws Exception {
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO Account (Name, Active__c) VALUES (?, true)");
        for (int i = 0; i < 450; i++) {
            statement.setString(1, "Acme " + i);
            statement.addBatch();
        }
        int[] counts = statement.executeBatch();
        assertEquals(450, counts.length);
        for (int count : counts)
            assertEquals(1, count);

        ArgumentCaptor<JSONArray> records = ArgumentCaptor.forClass(JSONArray.class);
        verify(api, times(3)).insertCollection(eq("Account"), records.capture(), eq(false));
        assertEquals(200, records.getAllValues().get(0).length());
        assertEquals(200, records.getAllValues().get(1).length());
        assertEquals(50, records.getAllValues().get(2).length());
        JSONObject last = records.getAllValues().get(2).getJSONObject(49);
        assertEquals("Acme 449", last.getString("Name"));
        assertTrue(last.getBoolean("Active__c"));

        List<String> ids = keys(statement);
        assertEquals(450, ids.size());
        assertEquals("ID0", ids.get(0));
        assertEquals("ID449", ids.get(449));
        verify(api, never()).insert(anyString(), anyString());

        // The batch is cleared after execution.
        assertEquals(0, statement.executeBatch().length);
    }

    @Test
    public void preparedUpsertBatchKeepsKeyInBodyAndSplitsDuplicateKeys() throws Exception {
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO Account (External_Id__c, Name) VALUES (?, ?) ON external_id__c");
        String[][] rows = {{"A", "one"}, {"B", "two"}, {"a", "three"}, {"C", "four"}};
        for (String[] row : rows) {
            statement.setString(1, row[0]);
            statement.setString(2, row[1]);
            statement.addBatch();
        }
        assertArrayEquals(new int[]{1, 1, 1, 1}, statement.executeBatch());

        ArgumentCaptor<JSONArray> records = ArgumentCaptor.forClass(JSONArray.class);
        verify(api, times(2)).upsertCollection(eq("Account"), eq("External_Id__c"), records.capture(), eq(false));
        assertEquals(2, records.getAllValues().get(0).length());
        assertEquals("A", records.getAllValues().get(0).getJSONObject(0).getString("External_Id__c"));
        assertEquals(2, records.getAllValues().get(1).length());
        assertEquals("a", records.getAllValues().get(1).getJSONObject(0).getString("External_Id__c"));
        verify(api, never()).upsert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void partialFailuresContinueAndRaiseBatchUpdateException() throws Exception {
        when(api.upsertCollection(anyString(), anyString(), any(JSONArray.class), anyBoolean())).thenReturn(new JSONArray()
                .put(new JSONObject().put("id", "ID1").put("success", true).put("created", true))
                .put(new JSONObject().put("success", false).put("errors", new JSONArray()
                        .put(new JSONObject().put("statusCode", "INVALID_FIELD").put("message", "bad phone")
                                .put("fields", new JSONArray().put("Phone")))))
                .put(new JSONObject().put("id", "ID3").put("success", true).put("created", false)));
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO Account (External_Id__c, Phone) VALUES (?, ?) ON External_Id__c");
        String[] keys = {"k1", "k2", null, "k3"};
        for (String key : keys) {
            statement.setString(1, key);
            statement.setString(2, "555");
            statement.addBatch();
        }
        try {
            statement.executeBatch();
            fail("Expected BatchUpdateException");
        } catch (BatchUpdateException e) {
            assertArrayEquals(new int[]{1, Statement.EXECUTE_FAILED, Statement.EXECUTE_FAILED, 1},
                    e.getUpdateCounts());
            assertTrue(e.getMessage(), e.getMessage().contains("Batch entry 1: INVALID_FIELD: bad phone"));
            assertTrue(e.getMessage(), e.getMessage().contains("Batch entry 2: UPSERT external ID must not be null"));
        }
        assertEquals(java.util.Arrays.asList("ID1", "ID3"), keys(statement));
        ArgumentCaptor<JSONArray> records = ArgumentCaptor.forClass(JSONArray.class);
        verify(api).upsertCollection(eq("Account"), eq("External_Id__c"), records.capture(), eq(false));
        assertEquals(3, records.getValue().length());
    }

    @Test
    public void requestFailureMarksWholeChunkFailed() throws Exception {
        RuntimeException failure = new RuntimeException("HTTP 400: INVALID_TYPE");
        when(api.insertCollection(anyString(), any(JSONArray.class), anyBoolean())).thenThrow(failure);
        PreparedStatement statement = connection.prepareStatement("INSERT INTO Nope__c (Name) VALUES (?)");
        statement.setString(1, "a");
        statement.addBatch();
        statement.setString(1, "b");
        statement.addBatch();
        try {
            statement.executeBatch();
            fail("Expected BatchUpdateException");
        } catch (BatchUpdateException e) {
            assertArrayEquals(new int[]{Statement.EXECUTE_FAILED, Statement.EXECUTE_FAILED}, e.getUpdateCounts());
            assertSame(failure, e.getCause());
        }
        assertTrue(keys(statement).isEmpty());
    }

    @Test
    public void binaryRowsAreSentIndividuallyInOrder() throws Exception {
        when(api.describe("ContentVersion")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(new JSONObject().put("name", "VersionData").put("type", "base64"))));
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ContentVersion (Title, VersionData) VALUES (?, ?)");
        statement.setString(1, "first");
        statement.setString(2, "text");
        statement.addBatch();
        byte[] bytes = {0, 1, -1};
        statement.setString(1, "binary");
        statement.setBytes(2, bytes);
        statement.addBatch();
        statement.setString(1, "last");
        statement.setString(2, "text");
        statement.addBatch();

        assertArrayEquals(new int[]{1, 1, 1}, statement.executeBatch());
        InOrder order = inOrder(api);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        order.verify(api).insertCollection(eq("ContentVersion"), any(JSONArray.class), eq(false));
        order.verify(api).insert(eq("ContentVersion"), body.capture());
        order.verify(api).insertCollection(eq("ContentVersion"), any(JSONArray.class), eq(false));
        assertEquals(Base64.getEncoder().encodeToString(bytes), new JSONObject(body.getValue()).getString("VersionData"));
        assertEquals(java.util.Arrays.asList("ID0", "SINGLE1", "ID2"), keys(statement));
    }

    @Test
    public void statementBatchGroupsCompatibleConsecutiveWrites() throws Exception {
        Statement statement = connection.createStatement();
        statement.addBatch("INSERT INTO Account (Name) VALUES ('a')");
        statement.addBatch("insert into account (Name, Phone) VALUES ('b', null)");
        statement.addBatch("UPSERT INTO Account (External_Id__c, Name) VALUES ('x', 'c') ON External_Id__c");
        statement.addBatch("UPSERT INTO Account (External_Id__c, Name) VALUES ('y', 'd') ON External_Id__c");
        statement.addBatch("INSERT INTO Contact (LastName) VALUES ('e')");
        statement.addBatch("catalog null");

        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 0}, statement.executeBatch());
        ArgumentCaptor<JSONArray> records = ArgumentCaptor.forClass(JSONArray.class);
        verify(api).insertCollection(eq("Account"), records.capture(), eq(false));
        assertEquals(2, records.getValue().length());
        verify(api).upsertCollection(eq("Account"), eq("External_Id__c"), any(JSONArray.class), eq(false));
        verify(api).insertCollection(eq("Contact"), any(JSONArray.class), eq(false));
        assertEquals(5, keys(statement).size());
    }

    @Test
    public void statementBatchReportsInvalidAndQueryEntries() throws Exception {
        Statement statement = connection.createStatement();
        statement.addBatch("INSERT INTO Account (Name) VALUES ('a')");
        statement.addBatch("INSERT INTO Account (Name, Name) VALUES ('a', 'b')");
        statement.addBatch("SELECT Id FROM Account");
        statement.addBatch("INSERT INTO Account (Name) VALUES ('c')");
        try {
            statement.executeBatch();
            fail("Expected BatchUpdateException");
        } catch (BatchUpdateException e) {
            assertArrayEquals(new int[]{1, Statement.EXECUTE_FAILED, Statement.EXECUTE_FAILED, 1},
                    e.getUpdateCounts());
            assertTrue(e.getMessage(), e.getMessage().contains("Duplicate INSERT column"));
        }
        verify(api, times(2)).insertCollection(eq("Account"), any(JSONArray.class), eq(false));
        verify(api, never()).query(anyString());
    }

    @Test
    public void clearBatchAndUnsupportedPreparedStatements() throws Exception {
        PreparedStatement insert = connection.prepareStatement("INSERT INTO Account (Name) VALUES (?)");
        insert.setString(1, "a");
        insert.addBatch();
        insert.clearBatch();
        assertEquals(0, insert.executeBatch().length);
        verify(api, never()).insertCollection(anyString(), any(JSONArray.class), anyBoolean());

        PreparedStatement select = connection.prepareStatement("SELECT Id FROM Account");
        try {
            select.addBatch();
            fail("Accepted SELECT batch");
        } catch (SQLFeatureNotSupportedException expected) {
            assertTrue(expected.getMessage().contains("INSERT and UPSERT"));
        }
    }

    @Test
    public void singleExecutionAfterBatchReportsOnlyItsOwnKey() throws Exception {
        when(api.insert(anyString(), anyString())).thenReturn(new JSONObject().put("id", "ONE"));
        PreparedStatement statement = connection.prepareStatement("INSERT INTO Account (Name) VALUES (?)");
        statement.setString(1, "a");
        statement.addBatch();
        statement.addBatch();
        statement.executeBatch();
        assertEquals(2, keys(statement).size());
        assertEquals(1, statement.executeUpdate());
        assertEquals(java.util.Collections.singletonList("ONE"), keys(statement));
    }

    @Test
    public void multiRowInsertIsChunkedAutomatically() throws Exception {
        StringBuilder sql = new StringBuilder("INSERT INTO Account (Name) VALUES ");
        for (int i = 0; i < 250; i++)
            sql.append(i == 0 ? "" : ", ").append("('n").append(i).append("')");
        Statement statement = connection.createStatement();
        assertEquals(250, statement.executeUpdate(sql.toString()));
        assertEquals(250, statement.getUpdateCount());
        ArgumentCaptor<JSONArray> records = ArgumentCaptor.forClass(JSONArray.class);
        verify(api, times(2)).insertCollection(eq("Account"), records.capture(), eq(false));
        assertEquals(200, records.getAllValues().get(0).length());
        assertEquals("n249", records.getAllValues().get(1).getJSONObject(49).getString("Name"));
        assertEquals(250, keys(statement).size());
        verify(api, never()).insert(anyString(), anyString());
    }

    @Test
    public void multiRowPartialFailureSavesValidRowsAndThrows() throws Exception {
        when(api.upsertCollection(anyString(), anyString(), any(JSONArray.class), anyBoolean())).thenReturn(new JSONArray()
                .put(new JSONObject().put("id", "ID1").put("success", true))
                .put(new JSONObject().put("success", false).put("errors", new JSONArray()
                        .put(new JSONObject().put("statusCode", "DUPLICATE_VALUE").put("message", "dup"))))
                .put(new JSONObject().put("id", "ID3").put("success", true)));
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO Account (External_Id__c, Name) VALUES (?, 'a'), (?, 'b'), (?, 'c') ON External_Id__c");
        statement.setString(1, "k1");
        statement.setString(2, "k2");
        statement.setString(3, "k3");
        try {
            statement.executeUpdate();
            fail("Expected SQLException");
        } catch (BatchUpdateException e) {
            fail("Single statement should not raise BatchUpdateException");
        } catch (SQLException e) {
            assertEquals("Row 2: DUPLICATE_VALUE: dup", e.getMessage());
        }
        assertEquals(2, statement.getUpdateCount());
        assertEquals(java.util.Arrays.asList("ID1", "ID3"), keys(statement));
        ArgumentCaptor<JSONArray> records = ArgumentCaptor.forClass(JSONArray.class);
        verify(api).upsertCollection(eq("Account"), eq("External_Id__c"), records.capture(), eq(false));
        assertEquals("k2", records.getValue().getJSONObject(1).getString("External_Id__c"));
    }

    @Test
    public void multiRowValidationErrorsSendNothing() throws Exception {
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO Account (External_Id__c) VALUES (?), (?) ON External_Id__c");
        statement.setString(1, "k1");
        statement.setString(2, "");
        try {
            statement.executeUpdate();
            fail("Accepted empty key");
        } catch (SQLException e) {
            assertEquals("Row 2: UPSERT external ID must not be null or empty", e.getMessage());
        }
        assertEquals(-1, statement.getUpdateCount());
        verifyZeroInteractions(api);
    }

    @Test
    public void multiRowBinaryRowsAreSentIndividually() throws Exception {
        when(api.describe("ContentVersion")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(new JSONObject().put("name", "VersionData").put("type", "base64"))));
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ContentVersion (Title, VersionData) VALUES ('a', 'x'), ('b', ?), ('c', 'y')");
        statement.setBytes(1, new byte[]{1, 2});
        assertEquals(3, statement.executeUpdate());
        InOrder order = inOrder(api);
        order.verify(api).insertCollection(eq("ContentVersion"), any(JSONArray.class), eq(false));
        order.verify(api).insert(eq("ContentVersion"), anyString());
        order.verify(api).insertCollection(eq("ContentVersion"), any(JSONArray.class), eq(false));
        assertEquals(java.util.Arrays.asList("ID0", "SINGLE1", "ID2"), keys(statement));
    }

    @Test
    public void multiRowEntriesInBatchReportRowCountsAndShareRequests() throws Exception {
        when(api.insertCollection(anyString(), any(JSONArray.class), anyBoolean())).thenReturn(new JSONArray()
                .put(new JSONObject().put("id", "ID1").put("success", true))
                .put(new JSONObject().put("id", "ID2").put("success", true))
                .put(new JSONObject().put("id", "ID3").put("success", true))
                .put(new JSONObject().put("success", false).put("errors", new JSONArray()
                        .put(new JSONObject().put("statusCode", "ERR").put("message", "bad"))))
                .put(new JSONObject().put("id", "ID5").put("success", true)));
        Statement statement = connection.createStatement();
        statement.addBatch("INSERT INTO Account (Name) VALUES ('a'), ('b'), ('c')");
        statement.addBatch("INSERT INTO Account (Name) VALUES ('d'), ('e')");
        try {
            statement.executeBatch();
            fail("Expected BatchUpdateException");
        } catch (BatchUpdateException e) {
            assertArrayEquals(new int[]{3, Statement.EXECUTE_FAILED}, e.getUpdateCounts());
            assertEquals("Batch entry 1 row 1: ERR: bad", e.getMessage());
        }
        ArgumentCaptor<JSONArray> records = ArgumentCaptor.forClass(JSONArray.class);
        verify(api).insertCollection(eq("Account"), records.capture(), eq(false));
        assertEquals(5, records.getValue().length());
        assertEquals(java.util.Arrays.asList("ID1", "ID2", "ID3", "ID5"), keys(statement));
    }
}
