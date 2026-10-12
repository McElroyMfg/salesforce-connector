// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.CALL;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.CATALOG;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.INSERT;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.UPSERT;
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
        // JDBC escape syntax: {call name(...)}
        boolean escaped = "{".equals(t.getValue());
        if (escaped)
            t = tokenIterator.get(CALL);
        if (t.is(KEY_WORD)) {
            if (t.is(SELECT)) {
                return new SQL_Select_Statement(tokenIterator);
            } else if (t.is(CATALOG)) {
                return new SQL_Catalog_Statement(tokenIterator);
            } else if (t.is(INSERT)) {
                return new SQL_Insert_Statement(tokenIterator);
            } else if (t.is(UPSERT)) {
                return new SQL_Upsert_Statement(tokenIterator);
            } else if (t.is(CALL)) {
                return new SQL_Call_Statement(tokenIterator, escaped);
            } else {
                throw new RuntimeException("Statement type " + t.getValue() + " is not supported");
            }
        } else {
            throw new RuntimeException("Unknown token: " + t.getValue());
        }
    }
}
