package com.mcelroy.salesforceconnector;

import com.mcelroy.salesforceconnector.jdbc.SFConnection;
import com.mcelroy.salesforceconnector.jdbc.SFDriver;
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
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class JdbcTest {

    private String getQuery(String s) {
        SQL_Statement sql_statement = SQL_Statement.parse(s);
        StringBuilder b = new StringBuilder();
        SQL_Visitor writer = new SOQL_Writer(b);
        sql_statement.accept(writer);
        return b.toString();
    }

    private String getQuery(String s, Map<Integer, String> values) {
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
    public void StatementQueryTest() throws Exception {

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFConnection connection = spy(new SFConnection(null));
        doReturn(clientConnection).when(connection).getClientConnection();

        JSONObject response = new JSONObject("{records: [{id: \"ID123\", name: \"Acme\", repId: 4, since: \"2024-01-30\", level: \"gold\"}, {id: \"ID456\", name: \"Stuff Inc.\", repId: 5, since: \"2023-08-04\", level: null}]}");
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

        assertEquals("gold", rs.getString("level"));
        assertEquals(false, rs.wasNull());
        rs.next();
        assertNull(rs.getString("level"));
        assertEquals(true, rs.wasNull());
    }

    @Test
    public void PreparedStatementQueryTest() throws Exception {

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
    public void CallableStatementTest() throws Exception {

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFConnection connection = spy(new SFConnection(null));
        doReturn(clientConnection).when(connection).getClientConnection();

        Map<String, Object> accountVariable = new HashMap<>();
        accountVariable.put("name", "Acme");
        accountVariable.put("repId", 5);

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

    @Test
    public void CountQueryTest() throws Exception {

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFConnection connection = spy(new SFConnection(null));
        doReturn(clientConnection).when(connection).getClientConnection();

        JSONObject response = new JSONObject("{totalSize: 10, records: []}");
        String sql = "select count(*) as tot from Account";

        when(clientConnection.query(getQuery(sql))).thenReturn(response);

        Statement statement = connection.createStatement();
        statement.executeQuery(sql);
        ResultSet rs = statement.getResultSet();

        assertEquals(true, rs.next());
        assertEquals(10, rs.getInt("tot"));
    }

    @Test
    public void CatalogSetTest() throws Exception {

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFConnection connection = spy(new SFConnection(null));
        doReturn(clientConnection).when(connection).getClientConnection();

        PreparedStatement statement = connection.prepareStatement("catalog demo");
        statement.executeUpdate();

        verify(connection, times(1)).setCatalog("demo");
    }

    @Test
    public void CatalogSetNullTest() throws Exception {

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFConnection connection = spy(new SFConnection(null));
        doReturn(clientConnection).when(connection).getClientConnection();

        PreparedStatement statement = connection.prepareStatement("catalog null");
        statement.executeUpdate();

        verify(connection, times(1)).setCatalog(null);
    }

    @Test
    public void PasswordPropertyTest() {
        String key = "-----BEGIN PRIVATE KEY-----\nMIIEvQ...\n-----END PRIVATE KEY-----";

        // blank or real password is ignored
        assertNull(SFDriver.resolvePrivateKey(null, null));
        assertNull(SFDriver.resolvePrivateKey(null, ""));
        assertNull(SFDriver.resolvePrivateKey(null, "   "));
        assertNull(SFDriver.resolvePrivateKey(null, "realpassword"));

        // PEM key in the password field is used as a backup way to set the JWT key
        assertEquals(key, SFDriver.resolvePrivateKey(null, key));

        // explicit privateKey wins over password
        assertEquals(key, SFDriver.resolvePrivateKey(key, "ignored"));
        assertEquals(key, SFDriver.resolvePrivateKey(key, null));
    }

    @Test
    public void GetPropertyInfoTest() throws Exception {
        SFDriver driver = new SFDriver();
        String url = "jdbc:sf:https://mycompany.my.salesforce.com";

        // no properties: client credentials is the default flow
        DriverPropertyInfo[] info = driver.getPropertyInfo(url, new Properties());
        assertEquals(8, info.length);
        assertEquals("clientId", info[0].name);
        assertEquals(true, info[0].required);
        assertEquals("clientSecret", info[1].name);
        assertEquals(true, info[1].required);
        assertEquals("user", info[2].name);
        assertEquals(false, info[2].required);
        assertEquals("privateKey", info[3].name);
        assertEquals("password", info[4].name);

        // with a private key the JWT flow is selected
        Properties p = new Properties();
        p.setProperty("privateKey", "-----BEGIN PRIVATE KEY-----");
        info = driver.getPropertyInfo(url, p);
        assertEquals(false, info[1].required); // clientSecret no longer required
        assertEquals(true, info[2].required);  // user now required

        // a PEM key in the password field also selects the JWT flow
        p = new Properties();
        p.setProperty("password", "-----BEGIN PRIVATE KEY-----");
        info = driver.getPropertyInfo(url, p);
        assertEquals(false, info[1].required);
        assertEquals(true, info[2].required);

        // sensitive values are never echoed back; non-secret values are
        p = new Properties();
        p.setProperty("clientId", "cid");
        p.setProperty("clientSecret", "secret");
        p.setProperty("user", "u@x.com");
        p.setProperty("privateKey", "-----BEGIN PRIVATE KEY-----");
        p.setProperty("password", "hunter2");
        info = driver.getPropertyInfo(url, p);
        assertEquals("cid", info[0].value);
        assertEquals("u@x.com", info[2].value);
        assertNull(info[1].value); // clientSecret
        assertNull(info[3].value); // privateKey
        assertNull(info[4].value); // password

        // non jdbc:sf: url returns no properties
        assertEquals(0, driver.getPropertyInfo("jdbc:other:url", null).length);
    }
}
