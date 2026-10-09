// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.visitor;

import com.mcelroy.salesforceconnector.parser.exception.PlaceholderException;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.node.SQL_Placeholder;
import com.mcelroy.salesforceconnector.parser.node.SQL_Value;

import java.util.Map;
import java.util.function.Function;

public class SQL_Placeholder_Replacer implements SQL_Visitor {
    private SQL_Visitor writer;
    private Map<Integer, ?> values;
    private Function<Object, String> encoder;
    private int index = 0;

    public SQL_Placeholder_Replacer(SQL_Visitor writer, Map<Integer, ?> values) {
        this(writer, values, String::valueOf);
    }

    public SQL_Placeholder_Replacer(SQL_Visitor writer, Map<Integer, ?> values, Function<Object, String> encoder) {
        this.writer = writer;
        this.values = values;
        this.encoder = encoder;
    }

    @Override
    public void visit(SQL_Node node) {
        if (node instanceof SQL_Placeholder) {
            Object v = values.get(++index);
            if (v == null)
                throw new PlaceholderException();
            writer.visit(new SQL_Value(encoder.apply(v)));
        } else
            writer.visit(node);
    }

    @Override
    public void leave(SQL_Node node) {
        writer.leave(node);
    }
}
