package com.mcelroy.salesforceconnector.parser.node.clause;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.node.SQL_Node;
import com.mcelroy.salesforceconnector.parser.node.SQL_Placeholder;
import com.mcelroy.salesforceconnector.parser.node.SQL_Value;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import java.util.ArrayList;
import java.util.List;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.TokenType.*;

public class SQL_List extends SQL_Node {

    private List<SQL_Node> list = new ArrayList<>();

    public SQL_List(SQL_Token.SQL_TokenIterator tokenIterator) {
        tokenIterator.get(GROUP_OPEN);
        add(tokenIterator);
        SQL_Token p = tokenIterator.peek();
        while (p != null && p.is(COMMA)) {
            tokenIterator.next(); // consume peek
            add(tokenIterator);
            p = tokenIterator.peek();
        }
        tokenIterator.get(GROUP_CLOSE);
    }

    private void add(SQL_Token.SQL_TokenIterator tokenIterator) {
        SQL_Token t = tokenIterator.get(WORD, QUOTE, PLACE_HOLDER);
        if (t.is(PLACE_HOLDER))
            list.add(new SQL_Placeholder());
        else
            list.add(new SQL_Value(t.getValue()));
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);
        for (SQL_Node n : list)
            n.accept(visitor);
        super.leave(visitor);
    }
}
