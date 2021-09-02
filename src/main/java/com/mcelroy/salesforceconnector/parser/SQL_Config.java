// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser;

public class SQL_Config {
    private boolean outputTableAlias = true;
    private boolean outputColumnAlias = true;
    public static final SQL_Config defaultConfig = defaultConfig();
    public static final SQL_Config salesforceConfig = salesForceConfig();

    private SQL_Config() {
    }

    private static SQL_Config defaultConfig() {
        return new SQL_Config();
    }

    private static SQL_Config salesForceConfig() {
        SQL_Config c = new SQL_Config();
        c.outputColumnAlias = false;
        return c;
    }

    public boolean isOutputTableAlias() {
        return outputTableAlias;
    }

    public boolean isOutputColumnAlias() {
        return outputColumnAlias;
    }
}
