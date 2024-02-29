package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

public class SQL_Placeholder extends SQL_Node {

    public SQL_Placeholder() {
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
    }

    @Override
    public String toString() {
        return "?";
    }
}
