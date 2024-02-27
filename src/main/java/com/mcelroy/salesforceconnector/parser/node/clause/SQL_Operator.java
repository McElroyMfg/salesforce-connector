package com.mcelroy.salesforceconnector.parser.node.clause;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

public class SQL_Operator extends SQL_Node {
    SQL_Node lhs;
    SQL_Token.OperatorType operatorType;
    SQL_Node rhs;

    public SQL_Operator(SQL_Node lhs, SQL_Token.OperatorType operatorType, SQL_Node rhs){
        this.lhs = lhs;
        this.operatorType = operatorType;
        this.rhs = rhs;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        lhs.accept(visitor);
        super.accept(visitor);
        rhs.accept(visitor);
    }

    @Override
    public String toString() {
        return operatorType.toString();
    }
}
