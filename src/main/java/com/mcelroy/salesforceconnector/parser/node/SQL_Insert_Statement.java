// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

public class SQL_Insert_Statement extends SQL_Write_Statement {
    public SQL_Insert_Statement(SQL_Token.SQL_TokenIterator tokenIterator) {
        super(tokenIterator, "INSERT");
        endStatement(tokenIterator);
    }
}
