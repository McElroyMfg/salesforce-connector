package com.mcelroy.salesforceconnector;

import com.mcelroy.salesforceconnector.jdbc.SFConnection;
import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.visitor.SOQL_Writer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Placeholder_Replacer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class JdbcTest {

    private String getQuery(String s){
        SQL_Statement sql_statement = SQL_Statement.parse(s);
        StringBuilder b = new StringBuilder();
        SQL_Visitor writer = new SOQL_Writer(b);
        sql_statement.accept(writer);
        return b.toString();
    }

    private String getQuery(String s, Map<Integer, String> values){
        SQL_Statement sql_statement = SQL_Statement.parse(s);
        StringBuilder b = new StringBuilder();
        SQL_Visitor writer = new SQL_Placeholder_Replacer(new SOQL_Writer(b), values);
        sql_statement.accept(writer);
        return b.toString();
    }

    Date getDate(String s) throws ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return new Date(df.parse(s).getTime());
    }

    @Test
    public void StatementQueryTest() throws Exception{

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFConnection connection = spy(new SFConnection(null));
        doReturn(clientConnection).when(connection).getClientConnection();

        JSONObject response = new JSONObject("{records: [{id: \"ID123\", name: \"Acme\", repId: 4, since: \"2024-01-30\"}, {id: \"ID456\", name: \"Stuff Inc.\", repId: 5, since: \"2023-08-04\"}]}");
        String sql = "select id, name, repId, since from Account";

        when(clientConnection.query(getQuery(sql))).thenReturn(response);

        Statement statement = connection.createStatement();
        statement.executeQuery(sql);
        ResultSet rs = statement.getResultSet();

        assertEquals(true, rs.next());
        assertEquals("ID123", rs.getString("id"));
        assertEquals(4, rs.getInt("repId"));
        assertEquals("Acme", rs.getString("name"));
        assertEquals(getDate("2024-01-30"), rs.getDate("since"));

        assertEquals("ID123", rs.getString(1));
        assertEquals(4, rs.getInt(3));
        assertEquals("Acme", rs.getString(2));
        assertEquals(getDate("2024-01-30"), rs.getDate(4));
    }

    @Test
    public void PreparedStatementQueryTest() throws Exception{

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFConnection connection = spy(new SFConnection(null));
        doReturn(clientConnection).when(connection).getClientConnection();

        JSONObject response = new JSONObject("{records: [{id: \"ID123\", name: \"Acme\", repId: 4, since: \"2024-01-30\"}, {id: \"ID456\", name: \"Stuff Inc.\", repId: 5, since: \"2023-08-04\"}]}");
        String sql = "select id, name, repId, since from Account where repId > ? and since > ?";
        Map<Integer, String> values = new HashMap<>();
        values.put(1, "3");
        values.put(2, "2022-01-01");

        when(clientConnection.query(getQuery(sql, values))).thenReturn(response);

        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setInt(1, 3);
        statement.setDate(2, getDate("2022-01-01"));
        statement.execute();
        ResultSet rs = statement.getResultSet();

        assertEquals(true, rs.next());
        assertEquals("ID123", rs.getString("id"));
        assertEquals(4, rs.getInt("repId"));
        assertEquals("Acme", rs.getString("name"));
        assertEquals(getDate("2024-01-30"), rs.getDate("since"));

        assertEquals("ID123", rs.getString(1));
        assertEquals(4, rs.getInt(3));
        assertEquals("Acme", rs.getString(2));
        assertEquals(getDate("2024-01-30"), rs.getDate(4));
    }

    @Test
    public void CallableStatementTest() throws Exception{

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFConnection connection = spy(new SFConnection(null));
        doReturn(clientConnection).when(connection).getClientConnection();

        Map<String,Object> accountVariable = new HashMap<>();
        accountVariable.put("name", "Acme");
        accountVariable.put( "repId", 5 );

        JSONObject body = new JSONObject();
        JSONArray inputs = new JSONArray();
        body.put("inputs", inputs);
        JSONObject values = new JSONObject();
        inputs.put(values);
        values.put("accountInput", accountVariable);

        JSONObject response = new JSONObject("{isSuccess: true}");

        when(clientConnection.launchFlow("My_SF_Flow", body.toString())).thenReturn(response);

        CallableStatement statement = connection.prepareCall("call My_SF_Flow");
        statement.setObject("accountInput", accountVariable);
        boolean error = statement.execute();

        assertEquals(false, error);
    }
}
