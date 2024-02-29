// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

public abstract class SQL_Node {

    public void accept(SQL_Visitor visitor) {
        visitor.visit(this);
    }

    public void leave(SQL_Visitor visitor) {
        visitor.leave(this);
    }

    @Override
    public String toString() {
        return "";
    }
}
