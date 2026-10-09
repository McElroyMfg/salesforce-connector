// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.exception.ExpectedException;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.NULL;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.OperatorType.ADD;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.OperatorType.SUB;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.*;

public class SQL_Insert_Statement extends SQL_Statement {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private final String table;
    private final SQL_Column_List columns;
    private final List<SQL_Node> values = new ArrayList<>();

    public SQL_Insert_Statement(SQL_Token.SQL_TokenIterator tokenIterator) {
        expectWord(tokenIterator, "INTO");
        SQL_Token tableToken = tokenIterator.get("table name");
        table = identifier(tableToken, tableToken.getValue());

        tokenIterator.get(GROUP_OPEN);
        columns = new SQL_Column_List(tokenIterator, SQL_Column.ColumnType.CONDITION);
        SQL_Token close = tokenIterator.get(GROUP_CLOSE);
        Set<String> names = new HashSet<>();
        for (SQL_Column column : columns.getColumns()) {
            identifier(close, column.getName());
            if (!names.add(column.getName().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Duplicate INSERT column: " + column.getName());
        }

        expectWord(tokenIterator, "VALUES");
        tokenIterator.get(GROUP_OPEN);
        SQL_Token separator;
        do {
            values.add(value(tokenIterator));
            separator = tokenIterator.get(COMMA, GROUP_CLOSE);
        } while (separator.is(COMMA));

        SQL_Token end = tokenIterator.peek();
        if (end != null && ";".equals(end.getValue()))
            tokenIterator.next();
        if (tokenIterator.hasNext())
            throw new ExpectedException(tokenIterator.next(), "end of INSERT statement");
        if (columns.getColumns().size() != values.size())
            throw new IllegalArgumentException("INSERT column and value counts do not match");
    }

    public String getTable() {
        return table;
    }

    public LinkedHashMap<String, Object> bind(Map<Integer, Object> parameters) throws SQLException {
        LinkedHashMap<String, Object> record = new LinkedHashMap<>();
        int parameter = 0;
        for (int i = 0; i < values.size(); i++) {
            SQL_Node node = values.get(i);
            Object value;
            if (node instanceof SQL_Placeholder) {
                parameter++;
                if (parameters == null || !parameters.containsKey(parameter))
                    throw new SQLException("Missing INSERT parameter " + parameter);
                value = parameters.get(parameter);
                if (value == null)
                    value = JSONObject.NULL;
            } else {
                value = literalValue(node.toString());
            }
            record.put(columns.getColumns().get(i).getName(), value);
        }
        return record;
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        columns.accept(visitor);
        for (SQL_Node value : values)
            value.accept(visitor);
        super.leave(visitor);
    }

    private static void expectWord(SQL_Token.SQL_TokenIterator tokenIterator, String word) {
        SQL_Token t = tokenIterator.get(word);
        if (!word.equalsIgnoreCase(t.getValue()))
            throw new ExpectedException(t, word);
    }

    private static String identifier(SQL_Token location, String name) {
        if (!IDENTIFIER.matcher(name).matches())
            throw new ExpectedException(location, "identifier but found " + name);
        return name;
    }

    private static SQL_Node value(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.get("value", PLACE_HOLDER, QUOTE, NULL, ADD, SUB);
        if (t.is(PLACE_HOLDER))
            return new SQL_Placeholder();
        String literal = t.getValue();
        if (t.is(ADD, SUB))
            literal += tokenIterator.get("number").getValue();
        try {
            literalValue(literal);
        } catch (IllegalArgumentException e) {
            throw new ExpectedException(t, "placeholder or literal");
        }
        return new SQL_Value(literal);
    }

    private static Object literalValue(String literal) {
        if (literal.startsWith("'")) {
            StringBuilder result = new StringBuilder();
            int i = 1;
            while (i < literal.length()) {
                char c = literal.charAt(i++);
                if (c == '\'') {
                    if (i < literal.length() && literal.charAt(i) == '\'') {
                        i++;
                    } else if (i == literal.length()) {
                        return result.toString();
                    } else {
                        throw invalid();
                    }
                } else if (c == '\\') {
                    if (i >= literal.length())
                        throw invalid();
                    c = literal.charAt(i++);
                    switch (c) {
                        case 'n': c = '\n'; break;
                        case 'r': c = '\r'; break;
                        case 't': c = '\t'; break;
                        case 'b': c = '\b'; break;
                        case 'f': c = '\f'; break;
                        case '0': c = '\0'; break;
                        default: break;
                    }
                }
                result.append(c);
            }
            throw invalid();
        }
        if ("null".equalsIgnoreCase(literal))
            return JSONObject.NULL;
        if ("true".equalsIgnoreCase(literal) || "false".equalsIgnoreCase(literal))
            return Boolean.valueOf(literal);
        if (NUMBER.matcher(literal).matches()) {
            try {
                return new BigDecimal(literal);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid INSERT number", e);
            }
        }
        throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Expected INSERT INTO table (columns) VALUES (placeholders or literals)");
    }
}
