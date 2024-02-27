// SPDX-FileCopyrightText: © 2024 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.parser.node;

import com.mcelroy.salesforceconnector.parser.SQL_Token;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;

import static com.mcelroy.salesforceconnector.parser.SQL_Token.KeywordType.*;

public class SQL_Select_Statement extends SQL_Statement {
    private SQL_Column_List selectColumns;
    private SQL_From from;
    private SQL_Where where;
    private SQL_Having having;
    private SQL_Order_By order_by;
    private SQL_Limit limit;

    public SQL_Select_Statement(SQL_Token.SQL_TokenIterator tokenIterator) {
        selectColumns = new SQL_Column_List(tokenIterator, SQL_Column.ColumnType.SELECT);

        tokenIterator.get(FROM);
        from = new SQL_From(tokenIterator);

        if(tokenIterator.hasNext()) {
            SQL_Token t = tokenIterator.get(WHERE, ORDER, LIMIT);

            if (t.is(WHERE)) {
                where = new SQL_Where(tokenIterator);
                if(tokenIterator.hasNext())
                    t = tokenIterator.get(HAVING, ORDER, LIMIT);
            }

            if(t.is(HAVING)){
                having = new SQL_Having(tokenIterator);
                if(tokenIterator.hasNext())
                    t = tokenIterator.get(ORDER, LIMIT);
            }

            if (t.is(ORDER)){
                tokenIterator.get(BY);
                order_by = new SQL_Order_By(tokenIterator);
            }

            if(t.is(LIMIT)){
                limit = new SQL_Limit(tokenIterator);
            }

        }

        // update columns with table alias
        this.accept(new SQL_Visitor() {
            @Override
            public void visit(SQL_Node node) {
                if(node instanceof SQL_Column){
                    ((SQL_Column)node).updateTableAlias(from.getTable());
                }
            }

            @Override
            public void leave(SQL_Node node) {

            }
        });

        // update columns with select column aliases
        this.accept(new SQL_Visitor() {
            @Override
            public void visit(SQL_Node node) {
                if(node instanceof SQL_Column){
                    SQL_Column c = (SQL_Column)node;
                    if(!(c.getColumnType() == SQL_Column.ColumnType.SELECT))
                        c.updateAliasName(selectColumns.getColumns());
                }
            }

            @Override
            public void leave(SQL_Node node) {

            }
        });
    }

    @Override
    public void accept(SQL_Visitor visitor) {
        super.accept(visitor);

        selectColumns.accept(visitor);

        from.accept(visitor);

        if(where != null)
            where.accept(visitor);

        if(having != null)
            having.accept(visitor);

        if(order_by != null)
            order_by.accept(visitor);

        if(limit != null)
            limit.accept(visitor);
    }

    @Override
    public String toString() {
        return "SELECT";
    }
}
