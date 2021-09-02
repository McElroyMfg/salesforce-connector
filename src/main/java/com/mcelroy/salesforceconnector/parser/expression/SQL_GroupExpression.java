// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.expression;

import com.mcelroy.salesforceconnector.parser.SQL_Config;
import com.mcelroy.salesforceconnector.parser.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.SQL_Token;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.GROUP_CLOSE;


public class SQL_GroupExpression extends SQL_Expression {
    private SQL_Expression group;

    public SQL_GroupExpression(SQL_Expression e) {
        this.group = e;
    }

    protected static SQL_Expression parseGroupExpression(SQL_Statement s, SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Expression e = parseExpression(s, tokenIterator);
        tokenIterator.get(GROUP_CLOSE);
        return new SQL_GroupExpression(e);
    }

    @Override
    public String toSQL(SQL_Config config) {
        return "(" + group.toSQL(config) + ")";
    }
}
