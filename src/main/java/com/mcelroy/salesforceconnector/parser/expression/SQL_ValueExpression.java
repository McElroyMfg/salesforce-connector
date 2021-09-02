// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.expression;

import com.mcelroy.salesforceconnector.parser.SQL_Config;

public class SQL_ValueExpression extends SQL_Expression {
    private String value;

    public SQL_ValueExpression(String v) {
        this.value = v;
    }

    @Override
    public String toSQL(SQL_Config config) {
        return value;
    }
}
