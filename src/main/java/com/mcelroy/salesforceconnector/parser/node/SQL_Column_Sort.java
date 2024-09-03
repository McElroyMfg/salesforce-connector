// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.ASC;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.DESC;

public class SQL_Column_Sort extends SQL_Node {

    private SQL_Token token;

    public SQL_Column_Sort(SQL_Token.SQL_TokenIterator tokenIterator) {
        this.token = tokenIterator.get(ASC, DESC);
    }

    @Override
    public String toString() {
        return token.getValue();
    }
}
