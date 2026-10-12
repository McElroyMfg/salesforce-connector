package com.mcelroy.salesforceconnector;

import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Upsert_Statement;
import org.json.JSONObject;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class UpsertParserTest {
    @Test
    public void bindsMixedLiteralsAndPositionalParametersWithCaseInsensitiveKey() throws SQLException {
        SQL_Upsert_Statement statement = (SQL_Upsert_Statement) SQL_Statement.parse(
                "upsert into Account (External_Id__c, Name, Amount__c, Active__c, Phone) "
                        + "values (?, 'O''Brien', -2.50, true, ?) on external_id__c;");
        assertEquals("Account", statement.getTable());
        assertEquals("External_Id__c", statement.getExternalIdField());
        Map<Integer, Object> parameters = new HashMap<>();
        parameters.put(1, "a/b +雪");
        parameters.put(2, null);
        Map<String, Object> row = statement.bind(parameters);
        assertEquals("a/b +雪", row.get("External_Id__c"));
        assertEquals("O'Brien", row.get("Name"));
        assertEquals(new BigDecimal("-2.50"), row.get("Amount__c"));
        assertEquals(Boolean.TRUE, row.get("Active__c"));
        assertSame(JSONObject.NULL, row.get("Phone"));
        parameters.put(1, "replacement");
        assertEquals("replacement", statement.bind(parameters).get("External_Id__c"));
        assertEquals("a/b +雪", row.get("External_Id__c"));
    }

    @Test
    public void supportsIdAndLiteralOnlyStatements() throws SQLException {
        SQL_Upsert_Statement statement = (SQL_Upsert_Statement) SQL_Statement.parse(
                "UPSERT INTO Account (Id, Name) VALUES ('001000000000001AAA', 'Acme') ON Id");
        assertEquals("Id", statement.getExternalIdField());
        assertEquals("Acme", statement.bind(null).get("Name"));
    }

    @Test
    public void rejectsMissingParameters() {
        SQL_Upsert_Statement statement = (SQL_Upsert_Statement) SQL_Statement.parse(
                "UPSERT INTO Account (Id, Name) VALUES (?, ?) ON Id");
        try {
            statement.bind(null);
            fail("Accepted missing parameters");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().contains("UPSERT parameter 1"));
        }
    }

    @Test
    public void rejectsMalformedUpserts() {
        String[] statements = {
                "UPSERT INTO Account (Id) VALUES (?)",
                "UPSERT INTO Account (Id) VALUES (?) ON",
                "UPSERT INTO Account (Id) VALUES (?) ON Name",
                "UPSERT INTO Account (Id, id) VALUES (?, ?) ON Id",
                "UPSERT INTO Account (Id, Name) VALUES (?) ON Id",
                "UPSERT INTO Account (Id) VALUES (?, ?) ON Id",
                "UPSERT INTO Account (Id) VALUES (?) ON Id.Name",
                "UPSERT INTO Account (Id) VALUES (?) ON 'Id'",
                "UPSERT INTO Account (Id) VALUES (?) ON Id; SELECT Id FROM Account",
                "UPSERT INTO Account (Id) VALUES (?),(?) ON Id",
                "UPSERT INTO Account (Id) VALUES (?) ON Id;;"
        };
        for (String sql : statements) {
            try {
                SQL_Statement.parse(sql);
                fail("Accepted malformed UPSERT: " + sql);
            } catch (RuntimeException expected) {
                // Reject invalid SQL before making a REST request.
            }
        }
    }
}
