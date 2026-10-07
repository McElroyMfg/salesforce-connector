package com.mcelroy.salesforceconnector;

import com.mcelroy.salesforceconnector.parser.node.SQL_Column;
import com.mcelroy.salesforceconnector.parser.node.SQL_Insert_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.node.SQL_Placeholder;
import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Value;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;
import org.json.JSONObject;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class InsertParserTest {
    private SQL_Insert_Statement parse(String sql) {
        return (SQL_Insert_Statement) SQL_Statement.parse(sql);
    }

    @Test
    public void bindsOrderedFieldsAndNumbersOnlyPlaceholders() throws SQLException {
        SQL_Insert_Statement statement = parse(
                "insert into Custom__c (Name, Amount__c, Active__c, Other__c) values (?, 2.50, ?, ?);");
        Map<Integer, Object> parameters = new HashMap<>();
        parameters.put(1, "O'Brien");
        parameters.put(2, true);
        parameters.put(3, null);
        LinkedHashMap<String, Object> record = statement.bind(parameters);
        assertEquals("Custom__c", statement.getTable());
        assertEquals(Arrays.asList("Name", "Amount__c", "Active__c", "Other__c"),
                new ArrayList<>(record.keySet()));
        assertEquals("O'Brien", record.get("Name"));
        assertEquals(new BigDecimal("2.50"), record.get("Amount__c"));
        assertEquals(Boolean.TRUE, record.get("Active__c"));
        assertSame(JSONObject.NULL, record.get("Other__c"));
        parameters.put(1, "second");
        assertEquals("second", statement.bind(parameters).get("Name"));
        assertEquals("O'Brien", record.get("Name"));
    }

    @Test
    public void bindsTypedLiteralsAndEscapedStrings() throws SQLException {
        Map<String, Object> record = parse(
                "INSERT INTO Account (A,B,C,D,E,F,G,H,I,J) VALUES "
                        + "('O''Brien', 'O\\'Brien', 'a\\\\b\\nc', TRUE, false, NULL, "
                        + "-12.50, +.5, 1.25e-3, '') ; \n").bind(Collections.emptyMap());
        assertEquals("O'Brien", record.get("A"));
        assertEquals("O'Brien", record.get("B"));
        assertEquals("a\\b\nc", record.get("C"));
        assertEquals(Boolean.TRUE, record.get("D"));
        assertEquals(Boolean.FALSE, record.get("E"));
        assertSame(JSONObject.NULL, record.get("F"));
        assertEquals(new BigDecimal("-12.50"), record.get("G"));
        assertEquals(new BigDecimal("+.5"), record.get("H"));
        assertEquals(new BigDecimal("1.25e-3"), record.get("I"));
        assertEquals("", record.get("J"));
    }

    @Test
    public void quotedSeparatorsAndKeywordsRemainLiteralText() throws SQLException {
        Map<String, Object> record = parse(
                "INSERT INTO Account (Name) VALUES ('?,); VALUES (SELECT; ''quoted''')").bind(null);
        assertEquals("?,); VALUES (SELECT; 'quoted'", record.get("Name"));
    }

    @Test
    public void boundObjectsRemainUnencoded() throws SQLException {
        Object value = Arrays.asList("a", 1);
        assertSame(value, parse("INSERT INTO Account (Name) VALUES (?)")
                .bind(Collections.singletonMap(1, value)).get("Name"));
    }

    @Test
    public void missingBindingsThrowSqlExceptionButExplicitNullIsPresent() throws SQLException {
        SQL_Insert_Statement statement = parse("INSERT INTO Account (Name,Phone) VALUES (?,?)");
        for (Map<Integer, Object> parameters : Arrays.asList(null, Collections.<Integer, Object>emptyMap(),
                Collections.<Integer, Object>singletonMap(2, "only second"))) {
            try {
                statement.bind(parameters);
                fail("Missing parameter accepted");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage().contains("1"));
            }
        }
        try {
            statement.bind(Collections.<Integer, Object>singletonMap(1, null));
            fail("Missing second parameter accepted");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().contains("2"));
        }
        assertSame(JSONObject.NULL, parse("INSERT INTO Account (Name) VALUES (?)")
                .bind(Collections.singletonMap(1, null)).get("Name"));
    }

    @Test
    public void reusesConditionColumnsValueAndPlaceholderNodes() {
        List<SQL_Node> nodes = new ArrayList<>();
        parse("INSERT INTO Account (Name,Phone) VALUES (?, '123')").accept(new SQL_Visitor() {
            @Override
            public void visit(SQL_Node node) {
                nodes.add(node);
            }

            @Override
            public void leave(SQL_Node node) {
            }
        });
        assertEquals(2, nodes.stream().filter(node -> node instanceof SQL_Column).count());
        for (SQL_Node node : nodes) {
            if (node instanceof SQL_Column)
                assertEquals(SQL_Column.ColumnType.CONDITION, ((SQL_Column) node).getColumnType());
        }
        assertEquals(1, nodes.stream().filter(node -> node instanceof SQL_Placeholder).count());
        assertEquals(1, nodes.stream().filter(node -> node instanceof SQL_Value).count());
    }

    @Test
    public void rejectsMalformedAndUnsupportedStatements() {
        String[] statements = {
                "INSERT Account (Name) VALUES (?)",
                "INSERT INTO Account VALUES (?)",
                "INSERT INTO Account () VALUES ()",
                "INSERT INTO Account (Name,) VALUES (?)",
                "INSERT INTO Account (,Name) VALUES (?)",
                "INSERT INTO Account (Name Phone) VALUES (?)",
                "INSERT INTO Account (Name AS N) VALUES (?)",
                "INSERT INTO Account (Name.Name) VALUES (?)",
                "INSERT INTO Account (Name, name) VALUES (?,?)",
                "INSERT INTO Account alias (Name) VALUES (?)",
                "INSERT INTO Account (Name) VALUE (?)",
                "INSERT INTO Account (Name) VALUES ()",
                "INSERT INTO Account (Name) VALUES (?,)",
                "INSERT INTO Account (Name) VALUES (?,?)",
                "INSERT INTO Account (Name,Phone) VALUES (?)",
                "INSERT INTO Account (Name) VALUES (identifier)",
                "INSERT INTO Account (Name) VALUES (1+2)",
                "INSERT INTO Account (Name) VALUES (1 2)",
                "INSERT INTO Account (Name) VALUES (func())",
                "INSERT INTO Account (Name) VALUES ((1))",
                "INSERT INTO Account (Name) VALUES (DEFAULT)",
                "INSERT INTO Account (Name) VALUES (1e)",
                "INSERT INTO Account (Name) VALUES (NaN)",
                "INSERT INTO Account (Name) VALUES ('unterminated)",
                "INSERT INTO Account (Name) VALUES ('escaped\\')",
                "INSERT INTO Account (Name) VALUES ('a' 'b')",
                "INSERT INTO Account (Name) VALUES (x'a')",
                "INSERT INTO Account (Name) VALUES (?) RETURNING Id",
                "INSERT INTO Account (Name) VALUES (?),(?)",
                "INSERT INTO Account (Name) VALUES (?); SELECT Id FROM Account",
                "INSERT INTO Account (Name) VALUES (?);;",
                "INSERT INTO Account (Name) VALUES (?) -- comment",
                "INSERT INTO Account (Name) VALUES (?"
        };
        for (String sql : statements) {
            try {
                parse(sql);
                fail("Accepted malformed INSERT: " + sql);
            } catch (RuntimeException expected) {
                // Parser failures must happen before record creation.
            }
        }
    }
}
