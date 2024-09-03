// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.GROUP_CLOSE;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.GROUP_OPEN;

public class SQL_Column_Function extends SQL_Node {

    private String name;
    private String param;

    public SQL_Column_Function(SQL_Token.SQL_TokenIterator tokenIterator, String name) {
        this.name = name.toUpperCase();
        if (!this.name.equals("COUNT"))
            throw new RuntimeException("Unknown function: " + name);
        tokenIterator.get(GROUP_OPEN);
        SQL_Token p = tokenIterator.peek();
        if (!p.is(GROUP_CLOSE))
            param = tokenIterator.next().getValue();
        tokenIterator.get(GROUP_CLOSE);
    }

    public String getName() {
        return name;
    }

    public String getParam() {
        return param;
    }

    @Override
    public String toString() {
        return "( " + param + " )";
    }
}
