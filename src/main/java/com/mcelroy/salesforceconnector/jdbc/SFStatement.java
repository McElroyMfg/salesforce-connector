// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.visitor.SOQL_Writer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Placeholder_Replacer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SFStatement implements Statement {
    private SFConnection sfConnection;
    private SFClientConnection apiConnection;
    private ResultSet resultSet;
    private List<String> batch = new ArrayList<>();

    public SFStatement(SFConnection sfConnection, SFClientConnection apiConnection) {
        this.sfConnection = sfConnection;
        this.apiConnection = apiConnection;
    }

    public SFClientConnection getApiConnection(){
        return apiConnection;
    }

    public ResultSet execute(SQL_Statement sql_statement, Map<Integer, String> placeholderValues) throws SQLException {
        StringBuilder b = new StringBuilder();
        SQL_Visitor writer = new SOQL_Writer(b);
        if(placeholderValues != null)
            writer = new SQL_Placeholder_Replacer(writer, placeholderValues);
        sql_statement.accept(writer);
        resultSet = new SFResultSet(this, sql_statement, apiConnection.query(b.toString()));
        return resultSet;
    }

    @Override
    public ResultSet executeQuery(String s) throws SQLException {
        SQL_Statement sql_statement = SQL_Statement.parse(s);
        return execute(sql_statement, null);
    }

    @Override
    public int executeUpdate(String s) throws SQLException {
        String sl = s.trim().toLowerCase();
        if (sl.startsWith("catalog")) {
            String[] parts = sl.replaceAll(" +", " ").split(" ");
            if (parts.length == 2 && !parts[1].trim().equals("null")) {
                sfConnection.setCatalog(parts[1]);
                return 0;
            } else if (parts.length == 1 || parts[1].trim().equals("null")) {
                sfConnection.setCatalog(null);
                return 0;
            }
        }
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void close() throws SQLException {

    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxFieldSize(int i) throws SQLException {

    }

    @Override
    public int getMaxRows() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxRows(int i) throws SQLException {

    }

    @Override
    public void setEscapeProcessing(boolean b) throws SQLException {

    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setQueryTimeout(int i) throws SQLException {

    }

    @Override
    public void cancel() throws SQLException {

    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {

    }

    @Override
    public void setCursorName(String s) throws SQLException {

    }

    @Override
    public boolean execute(String s) throws SQLException {
        if (s != null) {
            String sl = s.trim().toLowerCase();

            if (sl.startsWith("select")) {
                executeQuery(s);
                return true;
            } else {
                executeUpdate(s);
                return false;
            }
        }

        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return resultSet;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return -1;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return false;
    }

    @Override
    public void setFetchDirection(int i) throws SQLException {

    }

    @Override
    public int getFetchDirection() throws SQLException {
        return 0;
    }

    @Override
    public void setFetchSize(int i) throws SQLException {

    }

    @Override
    public int getFetchSize() throws SQLException {
        return 2000;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return 0;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return 0;
    }

    @Override
    public void addBatch(String s) throws SQLException {
        batch.add(s);
    }

    @Override
    public void clearBatch() throws SQLException {
        batch.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        int[] status = new int[batch.size()];
        for (int i = 0; i < batch.size(); i++) {
            try {
                execute(batch.get(i));
                status[i] = SUCCESS_NO_INFO;
            } catch (Exception e) {
                status[i] = EXECUTE_FAILED;
            }
        }
        return status;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return sfConnection;
    }

    @Override
    public boolean getMoreResults(int i) throws SQLException {
        return getMoreResults();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return null;
    }

    @Override
    public int executeUpdate(String s, int i) throws SQLException {
        execute(s);
        return 0;
    }

    @Override
    public int executeUpdate(String s, int[] ints) throws SQLException {
        execute(s);
        return 0;
    }

    @Override
    public int executeUpdate(String s, String[] strings) throws SQLException {
        execute(s);
        return 0;
    }

    @Override
    public boolean execute(String s, int i) throws SQLException {
        execute(s);
        return false;
    }

    @Override
    public boolean execute(String s, int[] ints) throws SQLException {
        execute(s);
        return false;
    }

    @Override
    public boolean execute(String s, String[] strings) throws SQLException {
        execute(s);
        return false;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return 0;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public void setPoolable(boolean b) throws SQLException {

    }

    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {

    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> aClass) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return false;
    }
}
