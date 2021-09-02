// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser;

public class SQL_Column {
    public enum ColumnType {SELECT, EXPRESSION, ORDER}

    public ColumnType type;
    public String name;
    public String alias;
    public String tableAlias;

    public SQL_Column(String column, ColumnType t) {
        this.name = column;
        this.type = t;
    }

    public String fullName() {
        if (tableAlias != null)
            return tableAlias + "." + name;
        else
            return name;
    }

    public String toSQL(SQL_Config config) {
        StringBuilder b = new StringBuilder();

        if (config.isOutputTableAlias())
            b.append(fullName());
        else
            b.append(name);
        if (config.isOutputColumnAlias() && alias != null)
            b.append(" AS " + alias);

        return b.toString();
    }

    @Override
    public String toString() {
        return toSQL(SQL_Config.defaultConfig);
    }
}
