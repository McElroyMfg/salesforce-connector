// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

public class SQL_From extends SQL_Node {

    private SQL_Table table;

    public SQL_From(SQL_Token.SQL_TokenIterator tokenIterator) {
        table = new SQL_Table(tokenIterator);
    }

    public SQL_Table getTable() {
        return table;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        table.accept(visitor);
    }

    @Override
    public String toString() {
        return "FROM";
    }
}
