// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import java.util.ArrayList;
import java.util.List;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.COMMA;

public class SQL_Column_List extends SQL_Node {

    private List<SQL_Column> columns = new ArrayList<>();

    public SQL_Column_List(SQL_Token.SQL_TokenIterator tokenIterator, SQL_Column.ColumnType columnType) {
        columns.add(new SQL_Column(tokenIterator, columnType));
        SQL_Token p = tokenIterator.peek();
        while (p != null && p.is(COMMA)) {
            tokenIterator.next(); // consume peek
            columns.add(new SQL_Column(tokenIterator, columnType));
            p = tokenIterator.peek();
        }
    }

    public List<SQL_Column> getColumns() {
        return columns;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        for (SQL_Column c : columns)
            c.accept(visitor);
        super.leave(visitor);
    }
}
