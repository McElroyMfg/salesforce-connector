// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.node.clause.SQL_Clause;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

public class SQL_Having extends SQL_Node {

    private SQL_Clause clause;

    public SQL_Having(SQL_Token.SQL_TokenIterator tokenIterator){
        clause = new SQL_Clause(tokenIterator);
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        clause.accept(visitor);
    }

    @Override
    public String toString() {
        return "HAVING";
    }
}
