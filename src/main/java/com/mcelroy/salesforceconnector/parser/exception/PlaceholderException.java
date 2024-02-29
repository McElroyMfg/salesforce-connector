// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.exception;

public class PlaceholderException extends RuntimeException {
    public PlaceholderException() {
        super("Missing placeholder value");
    }
}
