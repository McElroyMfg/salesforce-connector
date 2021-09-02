// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.expression;

import com.mcelroy.salesforceconnector.parser.SQL_Column;
import com.mcelroy.salesforceconnector.parser.SQL_Config;

public class SQL_ColumnExpression extends SQL_Expression {
    private SQL_Column column;

    public SQL_ColumnExpression(SQL_Column c) {
        this.column = c;
    }

    @Override
    public String toSQL(SQL_Config config) {
        if (config.isOutputColumnAlias() && column.alias != null)
            return column.alias;

        StringBuilder b = new StringBuilder();
        if (config.isOutputTableAlias() && column.tableAlias != null)
            b.append(column.tableAlias).append(".");
        b.append(column.name);
        return b.toString();
    }
}
