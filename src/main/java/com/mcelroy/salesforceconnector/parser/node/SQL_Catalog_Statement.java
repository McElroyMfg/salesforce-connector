// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.NULL;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.WORD;

public class SQL_Catalog_Statement extends SQL_Statement {
    private String catalog;

    public SQL_Catalog_Statement(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token name = tokenIterator.get("Catalog name", WORD, NULL);
        if (name.is(NULL))
            catalog = null;
        else
            catalog = name.getValue();
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
    }

    @Override
    public String toString() {
        return "CATALOG " + catalog;
    }
}
