// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

public class SQL_Order_By extends SQL_Column_List {

    public SQL_Order_By(SQL_Token.SQL_TokenIterator tokenIterator) {
        super(tokenIterator, SQL_Column.ColumnType.ORDER);
    }

    @Override
    public String toString() {
        return "ORDER BY";
    }
}
