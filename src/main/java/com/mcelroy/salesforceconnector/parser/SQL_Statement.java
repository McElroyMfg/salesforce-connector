// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser;

import java.util.ArrayList;
import java.util.List;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.SELECT;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.KEY_WORD;


public abstract class SQL_Statement {
    public enum StatementType {SELECT, UPDATE, INSERT, DELETE}

    protected StatementType type;
    protected String tableName;
    protected String tableAlias;
    protected List<SQL_Column> columns = new ArrayList<>();


    public static SQL_Statement parse(String statement) {
        SQL_Token.SQL_TokenIterator tokenIterator = SQL_Token.tokenize(statement);
        SQL_Statement s = parseStatement(tokenIterator);

        // update columns with table alias
        if (s.tableAlias != null) {
            String prefix = s.tableAlias + ".";
            for (SQL_Column c : s.columns) {
                if (c.name.startsWith(prefix)) {
                    c.tableAlias = s.tableAlias;
                    c.name = c.name.substring(prefix.length());
                }
            }
        }
        return s;
    }

    private static SQL_Statement parseStatement(SQL_Token.SQL_TokenIterator tokenIterator) {
        if (!tokenIterator.hasNext())
            throw new RuntimeException("Empty SQL statement");

        SQL_Token t = tokenIterator.next();
        if (t.is(KEY_WORD)) {
            if (t.is(SELECT)) {
                return new SQL_SelectStatement(tokenIterator);
            } else {
                throw new RuntimeException("Statement type " + t.value + " is not supported");
            }
        } else {
            throw new RuntimeException("Unknown token: " + t.value);
        }
    }

    public SQL_Column getColumn(String name) {
        if (name == null)
            throw new RuntimeException("null column name");

        for (SQL_Column c : columns) {
            if (c.alias != null && c.alias.equals(name))
                return c;
            if (c.name.equals(name))
                return c;
            if (c.tableAlias != null) {
                if (name.equals(c.tableAlias + '.' + c.name))
                    return c;
            }
        }
        return null;
    }

    public void addColumn(SQL_Column col) {
        columns.add(col);
    }

    public abstract String toSQL(SQL_Config config);


    public static void main(String[] args) {
        check(" select  first,  last FROM contacts where account = 'hello world''s'");
        check("select  first,last FROM contacts c where account = 'hello world''s'");
        check(" select  c.first,  last as l FROM contacts c where account = 'hello world''s'");
        check(" select  c.first as f,  c.last as l FROM contacts c where c.account <= 1.2");
        check(" select  c.first as f,  c.last as l FROM contacts c where l <= -4");
        check(" select  c.first as f,  c.last as l FROM contacts c where l <= ?");
        check(" select  c.first as f,  c.last as l FROM contacts c where l <= ? and f > ? and something like '%blah'");
        check(" select  c.first as f,  c.last as l FROM contacts c where (l <= ? and f > ?) or something like '%blah'");
        check(" select  c.first as f,  c.last as l FROM contacts c where (l <= ? and (f > ? or g <= 3)) or something like '%blah'");
        check(" select  c.first as f,  c.last as l FROM contacts c where (l <= ? and (f > ? or g <= 3)) or something IN (123, 456)");
        check(" select  c.first as f,  c.last as l FROM contacts c where (l + x <= ? and (f > ? or g <= 3 * y)) or something IN (123, 456) or somethingelse in (555,777)");
        check(" select  c.first as f,  c.last as l FROM contacts c where (l <= ? and (f > ? or g <= 3)) or something like '%blah' order by f, last desc");
        check(" select  c.first as f,  c.last as l FROM contacts c where l <= -4 limit 10");
        check(" select  c.first as f,  c.last as l FROM contacts c order by l desc limit 10 offset 5");

    }

    public static void check(String sql) {
        System.out.println("Original: " + sql);
        SQL_Statement s = SQL_Statement.parse(sql);
        System.out.println("Parsed: " + s);
        System.out.println("SF: " + s.toSQL(SQL_Config.salesforceConfig));
        System.out.println("");
    }
}
