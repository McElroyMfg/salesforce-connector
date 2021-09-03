// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import org.hibernate.dialect.Dialect;

public class SFDialect extends Dialect {

    public boolean supportsLimit() {
        return true;
    }

    public String getLimitString(String sql, boolean hasOffset) {
        return (new StringBuffer(sql.length() + 20)).append(sql).append(hasOffset ? " limit ? offset ?" : " limit ?").toString();
    }
}
