// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.exception.ExpectedException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.*;

public class SQL_Call_Statement extends SQL_Statement {
    private final String flowName;
    private final List<String> inputNames = new ArrayList<>();
    private final boolean positional;

    public SQL_Call_Statement(SQL_Token.SQL_TokenIterator tokenIterator, boolean escaped) {
        flowName = tokenIterator.get("flow name").getValue();

        int placeholders = 0;
        SQL_Token t = tokenIterator.peek();
        if (t != null && t.is(GROUP_OPEN)) {
            tokenIterator.next();
            t = tokenIterator.peek();
            if (t != null && t.is(GROUP_CLOSE)) {
                tokenIterator.next();
            } else {
                SQL_Token separator;
                do {
                    SQL_Token input = tokenIterator.get("input name", PLACE_HOLDER, KEY_WORD);
                    if (input.is(PLACE_HOLDER))
                        placeholders++;
                    else
                        inputNames.add(input.getValue());
                    separator = tokenIterator.get(COMMA, GROUP_CLOSE);
                } while (separator.is(COMMA));
                if (placeholders > 0 && !inputNames.isEmpty())
                    throw new ExpectedException(separator, "only placeholders or only input names");
            }
        }
        positional = placeholders > 0;

        if (escaped) {
            SQL_Token close = tokenIterator.get("}");
            if (!"}".equals(close.getValue()))
                throw new ExpectedException(close, "}");
        }
        SQL_Token end = tokenIterator.peek();
        if (end != null && ";".equals(end.getValue()))
            tokenIterator.next();
        if (tokenIterator.hasNext())
            throw new ExpectedException(tokenIterator.next(), "end of CALL statement");
    }

    public String getFlowName() {
        return flowName;
    }

    public List<String> getInputNames() {
        return Collections.unmodifiableList(inputNames);
    }

    public boolean isPositional() {
        return positional;
    }

    @Override
    public String toString() {
        return "CALL " + flowName;
    }
}
