package com.mcelroy.salesforceconnector;

import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.visitor.SOQL_Writer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import com.mcelroy.salesforceconnector.util.SFTestConnection;
import org.json.JSONObject;
import org.junit.Test;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JdbcTest {

    private String getQuery(String s){
        SQL_Statement sql_statement = SQL_Statement.parse(s);
        StringBuilder b = new StringBuilder();
        SQL_Visitor writer = new SOQL_Writer(b);
        sql_statement.accept(writer);
        return b.toString();
    }

    Date getDate(String s) throws ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return new Date(df.parse(s).getTime());
    }

    @Test
    public void SimpleQueryTest() throws Exception{

        SFClientConnection clientConnection = mock(SFClientConnection.class);
        SFTestConnection connection = new SFTestConnection(clientConnection);

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


}
