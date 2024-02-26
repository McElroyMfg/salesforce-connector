// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.expression;

import com.mcelroy.salesforceconnector.parser.SQL_Config;
import com.mcelroy.salesforceconnector.parser.SQL_Token;

import java.util.ArrayList;
import java.util.List;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.COMMA;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.GROUP_CLOSE;

public class SQL_ListExpression extends SQL_Expression {
    private List<String> values = new ArrayList<>();

    public SQL_ListExpression() {
    }

    public void add(String v) {
        values.add(v);
    }

    protected static SQL_Expression parseInExpressionSide(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_ListExpression rhs = new SQL_ListExpression();
        do {
            SQL_Token t = tokenIterator.get("value");
            rhs.add(t.getValue());
            t = tokenIterator.get(COMMA, GROUP_CLOSE);
            if (t.is(GROUP_CLOSE)) {
                return rhs;
            }
        } while (true);
    }

    @Override
    public String toSQL(SQL_Config config) {
        StringBuilder b = new StringBuilder();
        b.append("(");
        boolean first = true;
        for (String s : values) {
            if (first)
                first = false;
            else
                b.append(", ");
            b.append(s);
        }
        b.append(")");
        return b.toString();
    }
}
