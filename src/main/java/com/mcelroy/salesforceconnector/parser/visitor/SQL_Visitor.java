// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.visitor;

import com.mcelroy.salesforceconnector.parser.node.SQL_Node;

public interface SQL_Visitor {
    public void visit(SQL_Node node);

    public void leave(SQL_Node node);
}
