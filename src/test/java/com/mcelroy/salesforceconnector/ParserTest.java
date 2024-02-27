package com.mcelroy.salesforceconnector;

import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.visitor.SOQL_Writer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Writer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ParserTest {

    private String sql(SQL_Statement s){
        StringBuilder b = new StringBuilder();
        SQL_Writer w = new SQL_Writer(b);
        s.accept(w);
        return b.toString();
    }

    private String soql(SQL_Statement s){
        StringBuilder b = new StringBuilder();
        SQL_Writer w = new SOQL_Writer(b);
        s.accept(w);
        return b.toString();
    }

    @Test
    public void SimpleQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select cola from tab");
        assertEquals("SELECT cola FROM tab", sql(s));
        assertEquals("SELECT cola FROM tab", soql(s));
    }

    @Test
    public void SimpleQueryColumnAliasTest(){
        SQL_Statement s = SQL_Statement.parse("select cola as ca from tab");
        assertEquals("SELECT cola AS ca FROM tab", sql(s));
        assertEquals("SELECT cola FROM tab", soql(s));
    }

    @Test
    public void SimpleQueryColumnAliasTableAliasTest(){
        SQL_Statement s = SQL_Statement.parse("select t.cola as ca from tab t where ca = 1");
        assertEquals("SELECT t.cola AS ca FROM tab t WHERE t.ca = 1", sql(s));
        assertEquals("SELECT t.cola FROM tab t WHERE t.cola = 1", soql(s));
    }

    @Test
    public void BasicQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select t.cola as ca, t.colb as cb, t.colc from tab t where ((ca + 1) = 2 and (t.coly > t.colz - 2.5)) or (t.coly = 'test')");
        assertEquals("SELECT t.cola AS ca, t.colb AS cb, t.colc FROM tab t WHERE ( ( t.ca + 1 ) = 2 AND ( t.coly > t.colz - 2.5 ) ) OR ( t.coly = 'test' )", sql(s));
        assertEquals("SELECT t.cola, t.colb, t.colc FROM tab t WHERE ( ( t.cola + 1 ) = 2 AND ( t.coly > t.colz - 2.5 ) ) OR ( t.coly = 'test' )", soql(s));
    }

    @Test
    public void OrderQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select t.cola as ca, t.colb from tab t where ca = 1 order by t.cola, t.colb DESC, t.colc ASC");
        assertEquals("SELECT t.cola AS ca, t.colb FROM tab t WHERE t.ca = 1 ORDER BY t.ca, t.colb DESC, t.colc ASC", sql(s));
        assertEquals("SELECT t.cola, t.colb FROM tab t WHERE t.cola = 1 ORDER BY t.cola, t.colb DESC, t.colc ASC", soql(s));
    }

    @Test
    public void SimpleLimitQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select t.cola from tab t limit 10");
        assertEquals("SELECT t.cola FROM tab t LIMIT 10", sql(s));
        assertEquals("SELECT t.cola FROM tab t LIMIT 10", soql(s));
    }

    @Test
    public void BasicLimitOffsetQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select t.cola from tab t where cola = 1 limit 10 offset 20");
        assertEquals("SELECT t.cola FROM tab t WHERE t.cola = 1 LIMIT 10 OFFSET 20", sql(s));
        assertEquals("SELECT t.cola FROM tab t WHERE t.cola = 1 LIMIT 10 OFFSET 20", soql(s));
    }

    @Test
    public void LikeQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select cola from tab where colb like '%hello'");
        assertEquals("SELECT cola FROM tab WHERE colb LIKE '%hello'", sql(s));
        assertEquals("SELECT cola FROM tab WHERE colb LIKE '%hello'", soql(s));
    }

    @Test
    public void IsQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select cola from tab where colb is null");
        assertEquals("SELECT cola FROM tab WHERE colb IS NULL", sql(s));
        assertEquals("SELECT cola FROM tab WHERE colb IS NULL", soql(s));
    }

    @Test
    public void IsNotQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select cola from tab where colb is not null");
        assertEquals("SELECT cola FROM tab WHERE colb IS NOT NULL", sql(s));
        assertEquals("SELECT cola FROM tab WHERE colb IS NOT NULL", soql(s));
    }

    @Test
    public void InQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select cola from tab where colb in (1,2, 3)");
        assertEquals("SELECT cola FROM tab WHERE colb IN ( 1, 2, 3 )", sql(s));
        assertEquals("SELECT cola FROM tab WHERE colb IN ( 1, 2, 3 )", soql(s));
    }

    @Test
    public void ComplexQueryTest(){
        SQL_Statement s = SQL_Statement.parse("select cola from tab where colb in (?, ?) and colc is not null or cold < -3.4");
        assertEquals("SELECT cola FROM tab WHERE colb IN ( ?, ? ) AND colc IS NOT NULL OR cold < -3.4", sql(s));
        assertEquals("SELECT cola FROM tab WHERE colb IN ( ?, ? ) AND colc IS NOT NULL OR cold < -3.4", soql(s));
    }
}
