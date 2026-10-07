// SPDX-FileCopyrightText: © 2026 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQL_Insert_Statement extends SQL_Statement {
    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*";
    private static final Pattern HEADER = Pattern.compile(
            "\\A\\s*INSERT\\s+INTO\\s+(" + IDENTIFIER + ")\\s*\\(\\s*("
                    + IDENTIFIER + "(?:\\s*,\\s*" + IDENTIFIER
                    + ")*)\\s*\\)\\s*VALUES\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private final String table;
    private final SQL_Column_List columns;
    private final List<SQL_Node> values = new ArrayList<>();

    public SQL_Insert_Statement(String sql) {
        Matcher header = HEADER.matcher(sql);
        if (!header.find())
            throw invalid();
        table = header.group(1);
        columns = new SQL_Column_List(SQL_Token.tokenize(header.group(2)), SQL_Column.ColumnType.CONDITION);
        Set<String> names = new HashSet<>();
        for (SQL_Column column : columns.getColumns()) {
            if (!names.add(column.getName().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Duplicate INSERT column: " + column.getName());
        }

        // Scan INSERT literals locally: SELECT's tokenizer does not handle backslash-escaped quotes.
        int position = header.end();
        while (true) {
            position = skipWhitespace(sql, position);
            if (position >= sql.length())
                throw invalid();
            int start = position;
            char first = sql.charAt(position);
            if (first == '?') {
                values.add(new SQL_Placeholder());
                position++;
            } else if (first == '\'') {
                position = stringEnd(sql, position);
                values.add(new SQL_Value(sql.substring(start, position)));
            } else {
                while (position < sql.length() && !Character.isWhitespace(sql.charAt(position))
                        && sql.charAt(position) != ',' && sql.charAt(position) != ')')
                    position++;
                String literal = sql.substring(start, position);
                literalValue(literal);
                values.add(new SQL_Value(literal));
            }
            position = skipWhitespace(sql, position);
            if (position >= sql.length())
                throw invalid();
            char separator = sql.charAt(position++);
            if (separator == ',')
                continue;
            if (separator != ')')
                throw invalid();
            break;
        }
        position = skipWhitespace(sql, position);
        if (position < sql.length() && sql.charAt(position) == ';')
            position = skipWhitespace(sql, position + 1);
        if (position != sql.length())
            throw invalid();
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

    private static int skipWhitespace(String sql, int position) {
        while (position < sql.length() && Character.isWhitespace(sql.charAt(position)))
            position++;
        return position;
    }

    private static int stringEnd(String sql, int position) {
        position++;
        while (position < sql.length()) {
            char c = sql.charAt(position++);
            if (c == '\\') {
                if (position >= sql.length())
                    throw invalid();
                position++;
            } else if (c == '\'') {
                if (position < sql.length() && sql.charAt(position) == '\'')
                    position++;
                else
                    return position;
            }
        }
        throw invalid();
    }

    private static Object literalValue(String literal) {
        if (literal.startsWith("'")) {
            StringBuilder result = new StringBuilder();
            for (int i = 1; i < literal.length() - 1; i++) {
                char c = literal.charAt(i);
                if (c == '\'') {
                    i++;
                } else if (c == '\\') {
                    c = literal.charAt(++i);
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
            return result.toString();
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
