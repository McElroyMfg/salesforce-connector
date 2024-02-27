package com.mcelroy.salesforceconnector;

import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.visitor.SOQL_Writer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Placeholder_Replacer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Writer;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PlaceholderTest {

    private String sql(SQL_Statement s, Map<Integer, String> values){
        StringBuilder b = new StringBuilder();
        SQL_Placeholder_Replacer w = new SQL_Placeholder_Replacer(new SQL_Writer(b), values);
        s.accept(w);
        return b.toString();
    }

    private String soql(SQL_Statement s, Map<Integer, String> values){
        StringBuilder b = new StringBuilder();
        SQL_Placeholder_Replacer w = new SQL_Placeholder_Replacer(new SOQL_Writer(b), values);
        s.accept(w);
        return b.toString();
    }

    @Test
    public void SimpleQueryTest(){
        Map<Integer, String> values = new HashMap<>();
        values.put(1, "123");
        values.put(2, "'hello world'");
        SQL_Statement s = SQL_Statement.parse("select cola from tab where cola < ? and colb = ?");
        assertEquals("SELECT cola FROM tab WHERE cola < 123 AND colb = 'hello world'", sql(s, values));
        assertEquals("SELECT cola FROM tab WHERE cola < 123 AND colb = 'hello world'", soql(s, values));
    }


}
