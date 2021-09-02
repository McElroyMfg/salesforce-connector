// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.SQL_Config;
import com.mcelroy.salesforceconnector.parser.SQL_Statement;
import com.mcelroy.salesforceconnector.rest.SalesForceAPI;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SFStatement implements Statement {
    SFConnection sfConnection;
    SalesForceAPI.Connection apiConnection;
    ResultSet resultSet;
    List<String> batch = new ArrayList<>();
    SQL_Statement sql_statement;
    String nextResultSet = null;

    public SFStatement(SFConnection sfConnection, SalesForceAPI.Connection apiConnection) {
        this.sfConnection = sfConnection;
        this.apiConnection = apiConnection;
    }

    @Override
    public ResultSet executeQuery(String s) throws SQLException {
        sql_statement = SQL_Statement.parse(s);
        updateResultSet(apiConnection.query(sql_statement.toSQL(SQL_Config.salesforceConfig)));
        return resultSet;
    }

    private void updateResultSet(JSONObject r) throws SQLException {
        nextResultSet = r.optString("nextRecordsUrl");

        List<JSONObject> records = new ArrayList<>();
        JSONArray a = r.optJSONArray("records");
        if (a != null) {
            try {
                // for each record in the batch add the object to the results
                for (int i = 0; i < a.length(); i++)
                    records.add(a.getJSONObject(i));
            } catch (Exception e) {
                throw new SQLException("Could not get record from JSONArray", e);
            }
        }
        resultSet = new SFResultSet(this, records);
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
        if (nextResultSet == null || nextResultSet.trim().equals(""))
            return false;

        updateResultSet(apiConnection.queryNext(nextResultSet));
        return true;
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
