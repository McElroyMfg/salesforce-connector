// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.visitor;

import com.mcelroy.salesforceconnector.parser.node.SQL_Column;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;

public class SOQL_Writer extends SQL_Writer {

    public SOQL_Writer(StringBuilder builder){
        super(builder);
    }

    @Override
    public void visit(SQL_Node node) {
       if(node instanceof SQL_Column){
            // Salesforce SOQL does not support column aliases
            SQL_Column c = (SQL_Column)node;
            StringBuilder b = new StringBuilder();
            if(c.getTableAlias() != null)
                b.append(c.getTableAlias()).append(".");
            b.append(c.getName());
            if(c.getAscending() != null)
                b.append(c.getAscending() ? " ASC" : " DESC");
            write(b.toString());
        }else {
            super.visit(node);
        }
    }

    @Override
    public void leave(SQL_Node node) {
        super.leave(node);
    }
}
