// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.exception;

import com.mcelroy.salesforceconnector.parser.SQL_Token;

public class MissingException extends RuntimeException {
    public MissingException(SQL_Token t, String expected) {
        super(getMessage(t, expected));
    }

    private static String getMessage(SQL_Token t, String expected) {
        StringBuilder b = new StringBuilder();
        b.append("Missing ").append(expected);
        if(t != null)
            b.append("\n").append(t.getErrorLocation());
        return b.toString();
    }
}
