// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.OFFSET;

public class SQL_Limit extends SQL_Node {

    private String limit;
    private String offset;

    public SQL_Limit(SQL_Token.SQL_TokenIterator tokenIterator){
        SQL_Token t = tokenIterator.get("limit value");
        limit = t.getValue();
        if (tokenIterator.hasNext()) {
            t = tokenIterator.peek();
            if (t.is(OFFSET)) {
                tokenIterator.next(); //skip peek
                t = tokenIterator.get("offset value");
                offset = t.getValue();
            }
        }
    }

    public String getLimit() {
        return limit;
    }

    public String getOffset() {
        return offset;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
    }

    @Override
    public String toString() {
        if(offset != null)
            return "LIMIT " + limit + " OFFSET " + offset;
        else
            return "LIMIT " + limit;
    }
}
