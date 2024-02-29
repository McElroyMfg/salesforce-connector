// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

import java.util.List;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.*;

public class SQL_Column extends SQL_Node {
    public enum ColumnType {SELECT, CONDITION, ORDER}

    private ColumnType columnType;
    private String name;
    private String alias;
    private String tableAlias;
    private Boolean ascending;

    public SQL_Column(SQL_Token.SQL_TokenIterator tokenIterator, ColumnType columnType) {
        this.columnType = columnType;
        SQL_Token t = tokenIterator.get("column name");
        this.name = t.getValue();
        SQL_Token p = tokenIterator.peek();
        if (columnType == ColumnType.SELECT && p != null) {
            if (p.is(AS)) {
                tokenIterator.next(); // consume peek
                t = tokenIterator.get("column alias");
                this.alias = t.getValue();
            }
        } else if (columnType == ColumnType.ORDER && p != null) {
            if (p.is(ASC, DESC)) {
                tokenIterator.next(); // consume peek
                this.ascending = p.is(ASC);
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
            if (this.name.equals(c.alias) || this.name.equals(c.name)) {
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
        return alias;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public Boolean getAscending() {
        return ascending;
    }

    @Override
    public String toString() {
        if (columnType == ColumnType.SELECT) {
            if (alias != null)
                return (tableAlias != null ? tableAlias + "." : "") + name + " AS " + alias;
            else
                return (tableAlias != null ? tableAlias + "." : "") + name;
        } else {
            String out = (tableAlias != null ? tableAlias + "." : "") + (alias != null ? alias : name);
            if (ascending != null)
                return out + (ascending ? " ASC" : " DESC");
            else
                return out;
        }
    }
}
