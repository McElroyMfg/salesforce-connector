// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.expression;

import com.mcelroy.salesforceconnector.parser.SQL_Column;
import com.mcelroy.salesforceconnector.parser.SQL_Config;
import com.mcelroy.salesforceconnector.parser.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.SQL_Token.OperatorType;
import com.mcelroy.salesforceconnector.parser.exception.ExpectedException;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.*;


public class SQL_OperatorExpression extends SQL_Expression {
    private SQL_Expression lhs;
    private SQL_Expression rhs;
    private OperatorType operator;

    public SQL_OperatorExpression(SQL_Expression left, OperatorType op, SQL_Expression right) {
        this.lhs = left;
        this.rhs = right;
        this.operator = op;
    }

    protected static SQL_Expression parseOperatorExpressionSide(SQL_Statement s, SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.next();
        if (t.is(WORD)) {
            SQL_Column col = s.getColumn(t.getValue());
            if (col == null) {
                char c = t.getValue().charAt(0);
                if (Character.isAlphabetic(c) || c == '_') {
                    // column is not in select statement but is used in where expression
                    col = new SQL_Column(t.getValue(), SQL_Column.ColumnType.EXPRESSION);
                    s.addColumn(col);
                }
            }
            if (col != null)
                return new SQL_ColumnExpression(col);
            else
                return new SQL_ValueExpression(t.getValue()); // numeric value
        } else if (t.is(QUOTE))
            return new SQL_ValueExpression(t.getValue());
        else
            throw new ExpectedException(t, "column or value");
    }

    @Override
    public String toSQL(SQL_Config config) {
        return lhs.toSQL(config) + " " + operator + " " + rhs.toSQL(config);
    }
}
