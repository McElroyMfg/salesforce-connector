// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.AS;

public class SQL_Column_Alias extends SQL_Node {

    private String alias;

    public SQL_Column_Alias(SQL_Token.SQL_TokenIterator tokenIterator) {
        tokenIterator.get(AS);
        this.alias = tokenIterator.get("column alias").getValue();
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public String toString() {
        return "AS " + alias;
    }
}
