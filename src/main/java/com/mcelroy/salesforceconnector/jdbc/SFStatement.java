// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.parser.node.SQL_Call_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Catalog_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Insert_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Upsert_Statement;
import com.mcelroy.salesforceconnector.parser.node.SQL_Statement;
import com.mcelroy.salesforceconnector.parser.visitor.SOQL_Writer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Placeholder_Replacer;
import com.mcelroy.salesforceconnector.parser.visitor.SQL_Visitor;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;
import org.json.JSONObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SFStatement implements Statement {
    private SFConnection sfConnection;
    private SFClientConnection apiConnection;
    private ResultSet resultSet;
    protected String generatedId;
    protected int updateCount = -1;
    private List<String> batch = new ArrayList<>();

    public SFStatement(SFConnection sfConnection, SFClientConnection apiConnection) {
        this.sfConnection = sfConnection;
        this.apiConnection = apiConnection;
    }

    public SFClientConnection getApiConnection() {
        return apiConnection;
    }

    protected void resetExecution() {
        generatedId = null;
        updateCount = -1;
        resultSet = null;
    }

    public ResultSet execute(SQL_Statement sql_statement, Map<Integer, Object> placeholderValues) throws SQLException {
        resetExecution();
        if (sql_statement instanceof SQL_Call_Statement)
            throw new SQLFeatureNotSupportedException("Use prepareCall for CALL statements");
        if (sql_statement instanceof SQL_Insert_Statement) {
            SQL_Insert_Statement insert = (SQL_Insert_Statement) sql_statement;
            Map<String, Object> row = SFBinaryFields.prepare(sfConnection, apiConnection,
                    insert.getTable(), insert.bind(placeholderValues));
            row.replaceAll((field, value) -> SFParameterEncoder.toJson(value));
            try {
                JSONObject response = apiConnection.insert(insert.getTable(), new JSONObject(row).toString());
                generatedId = response.optString("id", null);
                updateCount = 1;
                return null;
            } catch (RuntimeException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }
        if (sql_statement instanceof SQL_Upsert_Statement) {
            SQL_Upsert_Statement upsert = (SQL_Upsert_Statement) sql_statement;
            try {
                Map<String, Object> row = upsert.bind(placeholderValues);
                Object key = row.remove(upsert.getExternalIdField());
                if (key == null || key == JSONObject.NULL || key.toString().isEmpty())
                    throw new SQLException("UPSERT external ID must not be null or empty");
                row = SFBinaryFields.prepare(sfConnection, apiConnection, upsert.getTable(), row);
                row.replaceAll((field, value) -> SFParameterEncoder.toJson(value));
                JSONObject response = apiConnection.upsert(upsert.getTable(), upsert.getExternalIdField(),
                        key.toString(), new JSONObject(row).toString());
                generatedId = response == null ? null : response.optString("id", null);
                updateCount = 1;
                return null;
            } catch (RuntimeException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }

        StringBuilder b = new StringBuilder();
        SQL_Visitor writer = new SOQL_Writer(b);
        if (placeholderValues != null)
            writer = new SQL_Placeholder_Replacer(writer, placeholderValues, SFParameterEncoder::toSoql);
        sql_statement.accept(writer);

        if(sql_statement instanceof SQL_Catalog_Statement){
            executeUpdate(b.toString());
            return null;
        }

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
        resetExecution();
        String sl = s.trim().toLowerCase(Locale.ROOT);
        if (sl.startsWith("insert") || sl.startsWith("upsert")) {
            try {
                execute(SQL_Statement.parse(s), null);
                return updateCount;
            } catch (RuntimeException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }
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
            String sl = s.trim().toLowerCase(Locale.ROOT);

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
        return updateCount;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        updateCount = -1;
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
        List<Object[]> rows = generatedId == null ? Collections.emptyList()
                : Collections.singletonList(new Object[]{generatedId});
        return new SFListResultSet(Collections.singletonList("Id"), rows,
                Collections.singletonList(Types.VARCHAR));
    }

    @Override
    public int executeUpdate(String s, int i) throws SQLException {
        execute(s);
        return Math.max(0, getUpdateCount());
    }

    @Override
    public int executeUpdate(String s, int[] ints) throws SQLException {
        execute(s);
        return Math.max(0, getUpdateCount());
    }

    @Override
    public int executeUpdate(String s, String[] strings) throws SQLException {
        execute(s);
        return Math.max(0, getUpdateCount());
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
        if (isWrapperFor(aClass))
            return aClass.cast(this);
        throw new SQLException("Not a wrapper for " + aClass);
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return aClass != null && aClass.isInstance(this);
    }
}
