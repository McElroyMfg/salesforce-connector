// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import java.util.List;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.*;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.GROUP_OPEN;

public class SQL_Column extends SQL_Node {
    public enum ColumnType {SELECT, CONDITION, ORDER}

    private ColumnType columnType;
    private String tableAlias;
    private String name;
    private SQL_Column_Alias alias;
    private SQL_Column_Sort sort;
    private SQL_Column_Function column_function;

    public SQL_Column(SQL_Token.SQL_TokenIterator tokenIterator, ColumnType columnType) {
        this.columnType = columnType;
        SQL_Token t = tokenIterator.get("column name");
        this.name = t.getValue();
        SQL_Token p = tokenIterator.peek();
        if (columnType == ColumnType.SELECT && p != null) {
            if (p.is(GROUP_OPEN)) {
                column_function = new SQL_Column_Function(tokenIterator, this.name);
                p = tokenIterator.peek();
            }
            if (p.is(AS)) {
                alias = new SQL_Column_Alias(tokenIterator);
            }
        } else if (columnType == ColumnType.ORDER && p != null) {
            if (p.is(ASC, DESC)) {
                this.sort = new SQL_Column_Sort(tokenIterator);
            }
        }
    }


    public SQL_Column(String name, ColumnType columnType) {
        this.columnType = columnType;
        this.name = name;
    }

    protected void updateTableAlias(SQL_Table table) {
        if (table.getAlias() != null) {
            String prefix = table.getAlias() + ".";
            if (name.startsWith(prefix)) {
                tableAlias = table.getAlias();
                name = name.substring(prefix.length());
            }
        }
    }

    protected void updateAliasName(List<SQL_Column> selectColumns) {
        for (SQL_Column c : selectColumns) {
            if (this.name.equals(c.getAlias()) || this.name.equals(c.name)) {
                this.name = c.name;
                this.alias = c.alias;
                this.tableAlias = c.tableAlias;
                return;
            }
        }
    }

    public ColumnType getColumnType() {
        return columnType;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias != null ? alias.getAlias() : null;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        if (column_function != null)
            column_function.accept(visitor);
        if (columnType == ColumnType.SELECT && alias != null)
            alias.accept(visitor);
        if (sort != null)
            sort.accept(visitor);
        super.leave(visitor);
    }

    @Override
    public String toString() {
        if (columnType == ColumnType.SELECT) {
            return (tableAlias != null ? tableAlias + "." : "") + name;
        } else {
            return (tableAlias != null ? tableAlias + "." : "") + (alias != null ? alias.getAlias() : name);
        }
    }
}
