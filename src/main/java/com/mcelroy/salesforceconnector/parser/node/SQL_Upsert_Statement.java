// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

public class SQL_Upsert_Statement extends SQL_Write_Statement {
    private final String externalIdField;

    public SQL_Upsert_Statement(SQL_Token.SQL_TokenIterator tokenIterator) {
        super(tokenIterator, "UPSERT");
        expectWord(tokenIterator, "ON");
        SQL_Token fieldToken = tokenIterator.get("external ID field");
        String field = identifier(fieldToken, fieldToken.getValue());
        externalIdField = columns.getColumns().stream()
                .map(SQL_Column::getName)
                .filter(name -> name.equalsIgnoreCase(field))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "UPSERT ON field must be a listed column: " + field));
        endStatement(tokenIterator);
    }

    public String getExternalIdField() {
        return externalIdField;
    }
}
