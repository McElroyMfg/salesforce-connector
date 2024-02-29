// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.visitor;

import com.mcelroy.salesforceconnector.parser.node.SQL_Column_List;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.node.clause.SQL_Group;
import com.mcelroy.salesforceconnector.parser.node.clause.SQL_List;

public class SQL_Writer implements SQL_Visitor {
    private final StringBuilder b;
    private boolean inList = false;
    private boolean firstListItem = false;

    public SQL_Writer(StringBuilder builder) {
        this.b = builder;
    }

    @Override
    public void visit(SQL_Node node) {
        if (node instanceof SQL_Group)
            write("(");
        else if (node instanceof SQL_List) {
            write("(");
            inList = true;
            firstListItem = true;
        } else if (node instanceof SQL_Column_List) {
            write(node.toString());
            inList = true;
            firstListItem = true;
        } else {
            write(node.toString());
        }
    }

    @Override
    public void leave(SQL_Node node) {
        if (node instanceof SQL_Group)
            write(")");
        else if (node instanceof SQL_List) {
            inList = false;
            write(")");
        } else if (node instanceof SQL_Column_List) {
            inList = false;
        }
    }

    protected void write(String v) {
        if (!"".equals(v)) {
            if (inList) {
                if (firstListItem)
                    firstListItem = false;
                else
                    b.append(",");
            }

            if (b.length() != 0)
                b.append(" ");
            b.append(v);
        }
    }
}
