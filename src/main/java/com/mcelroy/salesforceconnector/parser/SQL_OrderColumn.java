// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser;

import com.mcelroy.salesforceconnector.parser.expression.SQL_Expression;

public class SQL_OrderColumn extends SQL_Expression {
    private SQL_Column column;
    private String order;

    public SQL_OrderColumn(SQL_Column c) {
        this.column = c;
    }

    public void setOrder(SQL_Token.KeywordType kt) {
        if (kt == SQL_Token.KeywordType.ASC)
            order = "ASC";
        else if (kt == SQL_Token.KeywordType.DESC)
            order = "DESC";
        else
            throw new RuntimeException("Unknown colum ordering direction: " + kt.toString());
    }

    @Override
    public String toSQL(SQL_Config config) {
        StringBuilder b = new StringBuilder();

        if (config.isOutputColumnAlias() && column.alias != null)
            b.append(column.alias);
        else {
            if (config.isOutputTableAlias() && column.tableAlias != null)
                b.append(column.tableAlias).append(".");
            b.append(column.name);
        }
        if (order != null)
            b.append(" ").append(order);
        return b.toString();
    }
}
