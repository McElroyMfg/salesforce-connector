package com.mcelroy.salesforceconnector.parser.node.clause;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.exception.ExpectedException;
import com.mcelroy.salesforceconnector.parser.node.SQL_Column;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.NULL;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.OperatorType.*;
import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.*;

public class SQL_Clause extends SQL_Node {

    SQL_Node node;

    public SQL_Clause(SQL_Token.SQL_TokenIterator tokenIterator){
        node = logical(tokenIterator);
    }

    public SQL_Node logical(SQL_Token.SQL_TokenIterator tokenIterator){
        SQL_Node lhs = condition(tokenIterator);

        SQL_Token p = tokenIterator.peek();
        if(p != null && p.is(AND, OR)){
            SQL_Token op = tokenIterator.next();
            SQL_Node rhs = logical(tokenIterator);
            return new SQL_Operator(lhs, op.getOperator(), rhs);
        }else
            return lhs;
    }

    private SQL_Node condition(SQL_Token.SQL_TokenIterator tokenIterator){
        SQL_Node lhs = expression(tokenIterator);

        SQL_Token p = tokenIterator.peek();
        if(p != null && p.is(EQ, NE, NE2, LT, GT, LE, GE, IN, IS, LIKE)) {
            SQL_Token op = tokenIterator.next();
            if(op.is(IN)){
                return new SQL_Operator(lhs, op.getOperator(), new SQL_List(tokenIterator));
            }else if(op.is(IS)){
                SQL_Token.OperatorType operatorType = IS;
                p = tokenIterator.peek();
                if(p != null && p.is(NOT)){
                    tokenIterator.next(); // consume peek;
                    operatorType = IS_NOT;
                }
                tokenIterator.get(NULL);
                return new SQL_Operator(lhs, operatorType, new SQL_Value("NULL"));
            }else {
                SQL_Node rhs = expression(tokenIterator);
                return new SQL_Operator(lhs, op.getOperator(), rhs);
            }
        }else
            return lhs;
    }

    private SQL_Node expression(SQL_Token.SQL_TokenIterator tokenIterator){
        SQL_Node lhs = term(tokenIterator);

        SQL_Token p = tokenIterator.peek();
        if(p != null && p.is(MULTIPLY, DIV, ADD, SUB)){
            SQL_Token op = tokenIterator.next();
            SQL_Node rhs = expression(tokenIterator);
            return new SQL_Operator(lhs, op.getOperator(), rhs);
        }else
            return lhs;
    }

    private SQL_Node term(SQL_Token.SQL_TokenIterator tokenIterator){
        SQL_Token t = tokenIterator.next();

        if(t != null) {
            if (t.is(GROUP_OPEN)) {  // internal expression groups
                SQL_Node n = logical(tokenIterator);
                tokenIterator.get(GROUP_CLOSE);
                return new SQL_Group(n);
            }else if (t.is(QUOTE)) {
                return new SQL_Value(t.getValue());
            } else if (t.is(WORD)) {
                char c = t.getValue().charAt(0);
                if (Character.isAlphabetic(c) || c == '_') {
                    return new SQL_Column(t.getValue(), SQL_Column.ColumnType.CONDITION);
                }else
                    return new SQL_Value(t.getValue());
            } else if (t.is(PLACE_HOLDER))
                return new SQL_Placeholder();
        }
        throw new ExpectedException(t, "column or value");
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        node.accept(visitor);
    }
}
