package com.mcelroy.salesforceconnector.parser.node.clause;

import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

public class SQL_Group extends SQL_Node {

    private SQL_Node node;

    public SQL_Group(SQL_Node node){
        this.node = node;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        node.accept(visitor);
        super.leave(visitor);
    }
}
