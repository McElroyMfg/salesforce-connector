// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser;

import com.mcelroy.salesforceconnector.parser.exception.ExpectedException;
import com.mcelroy.salesforceconnector.parser.expression.SQL_Expression;

import java.util.ArrayList;
import java.util.List;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.*;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.COMMA;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.WORD;

public class SQL_SelectStatement extends SQL_Statement {

    public SQL_Expression where;
    public List<SQL_OrderColumn> orderColumns;
    public String limit;
    public String offset;


    protected SQL_SelectStatement(SQL_Token.SQL_TokenIterator tokenIterator) {
        type = StatementType.SELECT;
        parseSelectColumn(tokenIterator);
    }

    private void parseSelectColumn(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.get("column name");
        SQL_Column column = new SQL_Column(t.value, SQL_Column.ColumnType.SELECT);
        columns.add(column);
        t = tokenIterator.get(AS, COMMA, FROM);
        if (t.is(AS)) {
            t = tokenIterator.get("column alias");
            column.alias = t.value;
            t = tokenIterator.get(COMMA, FROM);
        }

        if (t.is(FROM))
            parseTable(tokenIterator);
        else if (t.is(COMMA))
            parseSelectColumn(tokenIterator);
    }

    protected void parseTable(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.get("table name");
        tableName = t.value;

        if (tokenIterator.hasNext()) {
            t = tokenIterator.next();
            if (t.is(WORD)) {
                tableAlias = t.value;
                if (!tokenIterator.hasNext())
                    return; // had table alias but no WHERE clause
                else
                    t = tokenIterator.next();
            }

            if (t.is(WHERE))
                parseWhere(tokenIterator);
            else if (t.is(ORDER))
                parseOrder(tokenIterator);
            else
                throw new ExpectedException(t, WHERE.toString());
        }
    }

    protected void parseWhere(SQL_Token.SQL_TokenIterator tokenIterator) {
        where = SQL_Expression.parseExpression(this, tokenIterator);
        if (tokenIterator.hasNext()) {
            SQL_Token t = tokenIterator.get(ORDER, GROUP, LIMIT);
            if (t.is(ORDER))
                parseOrder(tokenIterator);
            else if (t.is(GROUP))
                parseGroupBy(tokenIterator);
            else if (t.is(LIMIT))
                parseLimit(tokenIterator);
        }
    }

    protected void parseGroupBy(SQL_Token.SQL_TokenIterator tokenIterator) {
        throw new RuntimeException("Not Implemented");
    }

    protected void parseHaving(SQL_Token.SQL_TokenIterator tokenIterator) {
        throw new RuntimeException("Not Implemented");
    }

    protected void parseOrder(SQL_Token.SQL_TokenIterator tokenIterator) {
        tokenIterator.get(BY);
        SQL_Token t;
        orderColumns = new ArrayList<>();
        do {
            t = tokenIterator.get("column");
            SQL_Column c = getColumn(t.value);
            if (c == null) {
                c = new SQL_Column(t.value, SQL_Column.ColumnType.ORDER);
                addColumn(c);
            }
            SQL_OrderColumn oc = new SQL_OrderColumn(c);
            orderColumns.add(oc);

            if (!tokenIterator.hasNext())
                return;

            t = tokenIterator.next();
            if (t.is(ASC) || t.is(DESC)) {
                oc.setOrder(t.keyword);

                if (!tokenIterator.hasNext())
                    return;

                t = tokenIterator.next();
            }
        } while (t.is(COMMA));

        if (t.is(GROUP))
            parseGroupBy(tokenIterator);
        else if (t.is(LIMIT))
            parseLimit(tokenIterator);
        else
            throw new ExpectedException(t, "COMMA, GROUP, LIMIT, or EOL");
    }

    protected void parseLimit(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.get("limit value");
        limit = t.value;
        if (tokenIterator.hasNext()) {
            t = tokenIterator.peek();
            if (t.is(OFFSET)) {
                tokenIterator.next(); //skip peek
                t = tokenIterator.get("offset value");
                offset = t.value;
            }
        }
    }

    @Override
    public String toString() {
        return toSQL(SQL_Config.defaultConfig);
    }

    @Override
    public String toSQL(SQL_Config config) {
        StringBuilder b = new StringBuilder();
        b.append(type);
        boolean first = true;
        for (SQL_Column c : columns) {
            if (c.type == SQL_Column.ColumnType.SELECT) {
                if (first)
                    first = false;
                else
                    b.append(",");

                b.append(" ");
                b.append(c.toSQL(config));
            }
        }
        b.append(" FROM ").append(tableName);
        if (config.isOutputTableAlias() && tableAlias != null)
            b.append(" " + tableAlias);

        if (where != null)
            b.append(" WHERE ").append(where.toSQL(config));

        if (orderColumns != null) {
            b.append(" ORDER BY ");
            first = true;
            for (SQL_OrderColumn c : orderColumns) {
                if (first)
                    first = false;
                else
                    b.append(", ");
                b.append(c.toSQL(config));
            }
        }

        if (limit != null)
            b.append(" LIMIT ").append(limit);
        if (offset != null)
            b.append(" OFFSET ").append(offset);

        return b.toString();
    }
}
