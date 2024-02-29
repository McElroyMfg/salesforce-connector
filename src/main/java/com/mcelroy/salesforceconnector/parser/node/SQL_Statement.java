// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.SELECT;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.KEY_WORD;

public class SQL_Statement extends SQL_Node {

    protected SQL_Statement() {
    }

    public static SQL_Statement parse(String sql) {
        SQL_Token.SQL_TokenIterator tokenIterator = SQL_Token.tokenize(sql);
        if (!tokenIterator.hasNext())
            throw new RuntimeException("Empty SQL statement");

        SQL_Token t = tokenIterator.next();
        if (t.is(KEY_WORD)) {
            if (t.is(SELECT)) {
                return new SQL_Select_Statement(tokenIterator);
            } else {
                throw new RuntimeException("Statement type " + t.getValue() + " is not supported");
            }
        } else {
            throw new RuntimeException("Unknown token: " + t.getValue());
        }
    }
}
