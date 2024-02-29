// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.PLACE_HOLDER;

public class SQL_Offset extends SQL_Node {

    private SQL_Node value;

    public SQL_Offset(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.get("offset value", PLACE_HOLDER);
        if (t.is(PLACE_HOLDER))
            value = new SQL_Placeholder();
        else
            value = new SQL_Value(t.getValue());
    }

    public SQL_Node getValue() {
        return value;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        value.accept(visitor);
    }

    @Override
    public String toString() {
        return "OFFSET";
    }
}
