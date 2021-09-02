// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.expression;

import com.mcelroy.salesforceconnector.parser.SQL_Config;
import com.mcelroy.salesforceconnector.parser.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.exception.MissingException;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.OperatorType.IN;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.GROUP_OPEN;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.OPERATOR;


public abstract class SQL_Expression {

    protected SQL_Expression() {
    }

    public abstract String toSQL(SQL_Config config);

    public static SQL_Expression parseExpression(SQL_Statement s, SQL_Token.SQL_TokenIterator tokenIterator) {
        return parseExpression(null, s, tokenIterator);
    }

    /*
        This parses expressions but doesn't validate so "where x < 2 < 7" is not valid SQL but will parse here.
        This is mainly to separate out columns and values for post processing.
     */
    private static SQL_Expression parseExpression(SQL_Token op, SQL_Statement s, SQL_Token.SQL_TokenIterator tokenIterator) {
        if (tokenIterator.hasNext()) {
            SQL_Expression e;
            SQL_Token p = tokenIterator.peek();

            if (op != null && op.is(IN)) {
                tokenIterator.get(GROUP_OPEN);
                e = SQL_ListExpression.parseInExpressionSide(tokenIterator);
            } else if (p.is(GROUP_OPEN)) {
                tokenIterator.next(); // skip peek
                e = SQL_GroupExpression.parseGroupExpression(s, tokenIterator);
            } else {
                e = SQL_OperatorExpression.parseOperatorExpressionSide(s, tokenIterator);
            }
            if (tokenIterator.hasNext()) {
                p = tokenIterator.peek();
                if (p.is(OPERATOR)) {
                    tokenIterator.next(); // skip peek
                    SQL_Expression rhs = parseExpression(p, s, tokenIterator);
                    e = new SQL_OperatorExpression(e, p.operator, rhs);
                }
            }

            if (op == null && !p.is(OPERATOR))
                throw new MissingException(tokenIterator.current(), "operator");
            return e;
        } else {
            throw new MissingException(tokenIterator.current(), "expression");
        }
    }

    @Override
    public String toString() {
        return toSQL(SQL_Config.defaultConfig);
    }
}
