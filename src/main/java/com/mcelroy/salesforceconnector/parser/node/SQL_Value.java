package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

public class SQL_Value extends SQL_Node {

    private String value;

    public SQL_Value(String value) {
        this.value = value;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
    }

    @Override
    public String toString() {
        return value;
    }
}
