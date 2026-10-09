package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClient;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Base64;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SFStatementUpsertTest {
    private static final String ID = "001000000000001AAA";
    private final SFClient client = mock(SFClient.class);
    private final SFClientConnection api = mock(SFClientConnection.class);
    private SFConnection connection;

    @Before
    public void setUp() {
        when(client.getConnection(isNull())).thenReturn(api);
        when(api.upsert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new JSONObject().put("id", ID).put("created", true));
        when(api.insert(anyString(), anyString())).thenReturn(new JSONObject().put("id", ID));
        connection = new SFConnection(client);
    }

    private void assertKeys(Statement statement, String id) throws SQLException {
        ResultSet keys = statement.getGeneratedKeys();
        if (id == null) {
            assertFalse(keys.next());
        } else {
            assertTrue(keys.next());
            assertEquals(id, keys.getString("Id"));
            assertFalse(keys.next());
        }
    }

    @Test
    public void preparedCreateExcludesKeyAndPreservesTypedBindings() throws Exception {
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO Account (Name, External_Id__c, Active__c, Phone) "
                        + "VALUES (?, ?, true, ?) ON external_id__c");
        statement.setString(1, "O'Brien");
        statement.setString(2, "a/b +雪");
        statement.setNull(3, Types.VARCHAR);
        assertFalse(statement.execute());
        assertEquals(1, statement.getUpdateCount());
        assertNull(statement.getResultSet());
        assertKeys(statement, ID);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(api).upsert(eq("Account"), eq("External_Id__c"), eq("a/b +雪"), body.capture());
        JSONObject row = new JSONObject(body.getValue());
        assertEquals(3, row.length());
        assertFalse(row.has("External_Id__c"));
        assertEquals("O'Brien", row.getString("Name"));
        assertTrue(row.getBoolean("Active__c"));
        assertTrue(row.isNull("Phone"));
    }

    @Test
    public void updatesReturnOneAndClearPreviousGeneratedKeys() throws Exception {
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO Account (Id, Name) VALUES (?, 'Acme') ON Id");
        statement.setString(1, ID);
        assertEquals(1, statement.executeUpdate());
        assertKeys(statement, ID);
        when(api.upsert(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new JSONObject(), null);
        for (int i = 0; i < 2; i++) {
            assertEquals(1, statement.executeUpdate());
            assertKeys(statement, null);
        }
    }

    @Test
    public void ordinaryStatementsExecuteInsertAndUpsertWithLiterals() throws Exception {
        Statement statement = connection.createStatement();
        String insert = "INSERT INTO Account (Name) VALUES ('Acme')";
        assertEquals(1, statement.executeUpdate(insert, Statement.RETURN_GENERATED_KEYS));
        assertKeys(statement, ID);
        assertFalse(statement.execute(insert));
        assertEquals(1, statement.getUpdateCount());
        String upsert = "UPSERT INTO Account (External_Id__c, Name) VALUES ('key', 'Acme') ON External_Id__c;";
        assertEquals(1, statement.executeUpdate(upsert));
        assertFalse(statement.execute(upsert));
        assertEquals(1, statement.getUpdateCount());
        assertKeys(statement, ID);
        verify(api, times(2)).upsert(eq("Account"), eq("External_Id__c"), eq("key"), anyString());
    }

    @Test
    public void rejectsNullEmptyAndMissingKeysWithoutCallingApi() throws Exception {
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO Account (Id, Name) VALUES (?, 'Acme') ON Id");
        for (String key : new String[]{null, ""}) {
            statement.setString(1, key);
            try {
                statement.executeUpdate();
                fail("Accepted invalid key");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage().contains("null or empty"));
            }
            assertEquals(-1, statement.getUpdateCount());
            assertKeys(statement, null);
        }
        statement.clearParameters();
        try {
            statement.executeUpdate();
            fail("Accepted missing binding");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().contains("parameter 1"));
        }
        verify(api, never()).upsert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void restFailuresPreserveDetailsAndClearPriorState() throws Exception {
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO Account (Id) VALUES (?) ON Id");
        statement.setString(1, ID);
        statement.executeUpdate();
        for (String message : new String[]{
                "HTTP 300: Multiple records matched the external ID.",
                "HTTP 404: Invalid external ID field"}) {
            RuntimeException original = new RuntimeException(message);
            when(api.upsert(anyString(), anyString(), anyString(), anyString())).thenThrow(original);
            try {
                statement.executeUpdate();
                fail("Accepted REST failure");
            } catch (SQLException expected) {
                assertEquals(message, expected.getMessage());
                assertSame(original, expected.getCause());
            }
            assertKeys(statement, null);
            assertEquals(-1, statement.getUpdateCount());
        }
    }

    @Test
    public void upsertUsesExistingBinaryFieldPreparation() throws Exception {
        when(api.describe("ContentVersion")).thenReturn(new JSONObject().put("fields", new JSONArray()
                .put(new JSONObject().put("name", "VersionData").put("type", "base64"))));
        PreparedStatement statement = connection.prepareStatement(
                "UPSERT INTO ContentVersion (Id, VersionData) VALUES (?, ?) ON Id");
        byte[] bytes = {0, 1, -1};
        statement.setString(1, "068000000000001AAA");
        statement.setBytes(2, bytes);
        assertEquals(1, statement.executeUpdate());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(api).upsert(eq("ContentVersion"), eq("Id"), eq("068000000000001AAA"), body.capture());
        assertEquals(Base64.getEncoder().encodeToString(bytes),
                new JSONObject(body.getValue()).getString("VersionData"));
    }
}
