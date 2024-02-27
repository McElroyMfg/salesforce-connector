// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.visitor;

import com.mcelroy.salesforceconnector.parser.exception.PlaceholderException;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.node.clause.SQL_Placeholder;
import com.mcelroy.salesforceconnector.parser.node.clause.SQL_Value;

import java.util.Map;

public class SQL_Placeholder_Replacer implements SQL_Visitor {
    private SQL_Visitor writer;
    private Map<Integer, String> values;
    private int index = 0;

    public SQL_Placeholder_Replacer(SQL_Visitor writer, Map<Integer, String> values){
        this.writer = writer;
        this.values = values;
    }

    @Override
    public void visit(SQL_Node node) {
        if(node instanceof SQL_Placeholder){
            String v = values.get(++index);
            if(v == null)
                throw new PlaceholderException();
            writer.visit(new SQL_Value(v));
        }else
            writer.visit(node);
    }

    @Override
    public void leave(SQL_Node node) {
        writer.leave(node);
    }
}
