// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.OFFSET;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.PLACE_HOLDER;

public class SQL_Limit extends SQL_Node {

    private SQL_Node limit;
    private SQL_Offset offset;

    public SQL_Limit(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.get("limit value", PLACE_HOLDER);
        if (t.is(PLACE_HOLDER))
            limit = new SQL_Placeholder();
        else
            limit = new SQL_Value(t.getValue());
        if (tokenIterator.hasNext()) {
            t = tokenIterator.peek();
            if (t.is(OFFSET)) {
                tokenIterator.next(); // consume peek
                offset = new SQL_Offset(tokenIterator);
            }
        }
    }

    public SQL_Node getValue() {
        return limit;
    }

    public SQL_Node getOffset() {
        return offset;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        limit.accept(visitor);
        if (offset != null)
            offset.accept(visitor);
    }

    @Override
    public String toString() {
        return "LIMIT";
    }
}
