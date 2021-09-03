// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser;

import com.mcelroy.salesforceconnector.parser.exception.ExpectedException;
import com.mcelroy.salesforceconnector.parser.exception.MissingException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SQL_Token {
    public interface TokenizerType {
    }

    public enum TokenType implements TokenizerType {KEY_WORD, COMMA, GROUP_OPEN, GROUP_CLOSE, OPERATOR, QUOTE, WORD}

    public enum KeywordType implements TokenizerType {SELECT, INSERT, DELETE, UPDATE, FROM, WHERE, HAVING, GROUP, ORDER, BY, ASC, DESC, AS, LIMIT, OFFSET}

    public enum OperatorType implements TokenizerType {
        AND, OR, LIKE, IN, IS,
        MULTIPLY("*"), DIV("/"), SUB("-"), ADD("+"),
        EQ("="), NE("!="), NE2("<>"), LT("<"), GT(">"), LE("<="), GE(">=");
        private final String value;

        OperatorType() {
            value = null;
        }

        OperatorType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value != null ? value : name();
        }
    }

    private final static Map<String, KeywordType> keywordsMap = new HashMap<>();
    private final static Map<String, OperatorType> operatorMap = new HashMap<>();

    static {
        for (KeywordType kt : KeywordType.values()) {
            keywordsMap.put(kt.toString(), kt);
        }
        for (OperatorType ot : OperatorType.values()) {
            operatorMap.put(ot.toString(), ot);
        }
    }

    public static class SQL_TokenIterator implements Iterator<SQL_Token> {
        private final List<SQL_Token> tokens;
        private int current = -1;

        private SQL_TokenIterator(List<SQL_Token> list) {
            this.tokens = list;
        }

        public SQL_Token peek() {
            return peek(1);
        }

        public SQL_Token peek(int offset) {
            if (current + offset < tokens.size())
                return tokens.get(current + offset);
            else
                return null;
        }

        public SQL_Token get(TokenizerType... types) {
            return get(null, types);
        }

        public SQL_Token get(String word, TokenizerType... types) {
            SQL_Token token = null;
            if (hasNext()) {
                token = next();
                if (word != null && token.is(TokenType.WORD))
                    return token;
                if (token.is(types))
                    return token;
            }

            StringBuilder b = new StringBuilder();
            boolean first = true;
            if (word != null) {
                b.append(word);
                first = false;
            }
            for (TokenizerType t : types) {
                if (first)
                    first = false;
                else
                    b.append(", ");
                b.append(t.toString());
            }
            if (token == null)
                throw new MissingException(current(), b.toString());
            else
                throw new ExpectedException(token, b.toString());
        }

        public SQL_Token current() {
            return tokens.get(current);
        }

        @Override
        public boolean hasNext() {
            return current + 1 < tokens.size();
        }

        @Override
        public SQL_Token next() {
            return tokens.get(++current);
        }
    }

    public TokenType type;
    public String value;
    public KeywordType keyword;
    public OperatorType operator;
    private final char[] sql;
    private final int offset;

    private SQL_Token(char[] chars, int start, int end) {
        StringBuilder b = new StringBuilder(end - start);
        for (int i = start; i < end; i++)
            b.append(chars[i]);

        value = b.toString();
        String uv = value.toUpperCase();
        keyword = keywordsMap.get(uv);
        operator = operatorMap.get(uv);
        sql = chars;
        offset = start;

        if (keyword != null) {
            type = TokenType.KEY_WORD;
        } else if (operator != null) {
            type = TokenType.OPERATOR;
        } else {
            switch (uv) {
                case ",":
                    type = TokenType.COMMA;
                    break;
                case "(":
                    type = TokenType.GROUP_OPEN;
                    break;
                case ")":
                    type = TokenType.GROUP_CLOSE;
                    break;
                default:
                    if (uv.startsWith("'"))
                        type = TokenType.QUOTE;
                    else
                        type = TokenType.WORD;
            }
        }

    }

    public static SQL_TokenIterator tokenize(String statement) {
        List<SQL_Token> tokens = new ArrayList<>();
        int tokenStart = -1;
        char[] chars = statement.toCharArray();
        boolean inQuote = false;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char nc = i + 1 < chars.length ? chars[i + 1] : ' ';

            if (inQuote) {
                if (c == '\'') {
                    if (nc == '\'')
                        i++; // escaped quote character so skip the escape sequence
                    else
                        inQuote = false;
                }
            } else if (c == '\'') {
                inQuote = true;
                tokenStart = i;
            } else if (Character.isWhitespace(c)) {
                if (tokenStart >= 0) {
                    tokens.add(new SQL_Token(chars, tokenStart, i));
                    tokenStart = -1;
                }
            } else if (c == '-' && tokenStart < 0 && Character.isDigit(nc)) {
                tokenStart = i; // negative number
            } else if (c == '=' || c == '!' || c == '<' || c == '>' || c == '(' || c == ')' || c == ',' ||
                    c == '*' || c == '/' || c == '-' || c == '+') {
                if (c == '-' && tokenStart >= 0 && i - tokenStart == 4) {
                    // check if we have a date
                    StringBuilder b = new StringBuilder(27);
                    for (int j = tokenStart; j < chars.length && j < tokenStart + 28 && !Character.isWhitespace(chars[j]); j++)
                        b.append(chars[j]);
                    if (b.length() == 10) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        try {
                            Date d = sdf.parse(b.toString());
                            i = tokenStart + 9;
                            continue;
                        } catch (ParseException e) {
                        }
                    } else if (b.length() == 28) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                        try {
                            Date d = sdf.parse(b.toString());
                            i = tokenStart + 27;
                            continue;
                        } catch (ParseException e) {
                        }
                    }
                }

                if (tokenStart >= 0) {
                    tokens.add(new SQL_Token(chars, tokenStart, i));
                    tokenStart = -1;
                }
                if ((c == '<' || c == '>' || c == '!') && nc == '=') { // <= >= !=
                    tokens.add(new SQL_Token(chars, i, i + 2));
                    i++;
                } else if (c == '<' && nc == '>') { // <>
                    tokens.add(new SQL_Token(chars, i, i + 2));
                    i++;
                } else
                    tokens.add(new SQL_Token(chars, i, i + 1));
            } else {
                if (tokenStart < 0)
                    tokenStart = i;
            }
        }

        if (tokenStart >= 0)
            tokens.add(new SQL_Token(chars, tokenStart, chars.length));

        return new SQL_TokenIterator(tokens);
    }

    public boolean is(TokenizerType... types) {
        for (TokenizerType t : types) {
            if (t instanceof KeywordType && keyword == t)
                return true;
            else if (t instanceof TokenType && type == t)
                return true;
            else if (t instanceof OperatorType && operator == t)
                return true;
        }
        return false;
    }

    public String getErrorLocation() {
        int start = offset - 50;
        if (start < 0)
            start = 0;
        int end = offset + value.length();
        StringBuilder b = new StringBuilder(end - start);
        for (int i = start; i < end; i++)
            b.append(sql[i]);
        return b.toString();
    }

    @Override
    public String toString() {
        return type.toString() + ": " + value;
    }
}
