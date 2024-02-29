// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.WORD;

public class SQL_Table extends SQL_Node {

    private String name;
    private String alias;

    public SQL_Table(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.get("table name");
        this.name = t.getValue();
        SQL_Token p = tokenIterator.peek();
        if (p != null) {
            if (p.is(WORD)) {
                t = tokenIterator.next();
                this.alias = t.getValue();
            }
        }
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public String toString() {
        if (alias != null)
            return name + " " + alias;
        else
            return name;
    }
}
