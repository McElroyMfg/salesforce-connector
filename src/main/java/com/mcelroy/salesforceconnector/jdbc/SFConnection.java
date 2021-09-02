// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SalesForceAPI;
import com.mcelroy.salesforceconnector.rest.SalesForceClient;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class SFConnection implements Connection {
    SalesForceClient client;
    String environment = null;

    public SFConnection(SalesForceClient client) {
        this.client = client;
    }

    public SalesForceAPI.Connection getApiConnection() {
        return client.getConnection(environment);
    }

    @Override
    public Statement createStatement() throws SQLException {
        return new SFStatement(this, getApiConnection());
    }

    @Override
    public PreparedStatement prepareStatement(String s) throws SQLException {
        return new SFPreparedStatement(this, getApiConnection(), s);
    }

    @Override
    public CallableStatement prepareCall(String s) throws SQLException {
        return new SFCallableStatement(this, getApiConnection(), s);
    }

    @Override
    public String nativeSQL(String s) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setAutoCommit(boolean b) throws SQLException {

    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return true;
    }

    @Override
    public void commit() throws SQLException {

    }

    @Override
    public void rollback() throws SQLException {
        throw new SQLException("Rollback not available");
    }

    @Override
    public void close() throws SQLException {

    }

    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setReadOnly(boolean b) throws SQLException {

    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override
    public void setCatalog(String s) throws SQLException {
        this.environment = s;
    }

    @Override
    public String getCatalog() throws SQLException {
        return this.environment;
    }

    @Override
    public void setTransactionIsolation(int i) throws SQLException {

    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return 0;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {

    }

    @Override
    public Statement createStatement(int i, int i1) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1) throws SQLException {
        return prepareStatement(s);
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1) throws SQLException {
        return prepareCall(s);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return null;
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {

    }

    @Override
    public void setHoldability(int i) throws SQLException {

    }

    @Override
    public int getHoldability() throws SQLException {
        return 0;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return null;
    }

    @Override
    public Savepoint setSavepoint(String s) throws SQLException {
        return null;
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLException("Rollback not available");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {

    }

    @Override
    public Statement createStatement(int i, int i1, int i2) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1, int i2) throws SQLException {
        return prepareStatement(s);
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1, int i2) throws SQLException {
        return prepareCall(s);
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i) throws SQLException {
        return prepareStatement(s);
    }

    @Override
    public PreparedStatement prepareStatement(String s, int[] ints) throws SQLException {
        return prepareStatement(s);
    }

    @Override
    public PreparedStatement prepareStatement(String s, String[] strings) throws SQLException {
        return prepareStatement(s);
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public boolean isValid(int i) throws SQLException {
        return true;
    }

    @Override
    public void setClientInfo(String s, String s1) throws SQLClientInfoException {

    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {

    }

    @Override
    public String getClientInfo(String s) throws SQLException {
        return null;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return null;
    }

    @Override
    public Array createArrayOf(String s, Object[] objects) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public Struct createStruct(String s, Object[] objects) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public void setSchema(String s) throws SQLException {

    }

    @Override
    public String getSchema() throws SQLException {
        return null;
    }

    @Override
    public void abort(Executor executor) throws SQLException {

    }

    @Override
    public void setNetworkTimeout(Executor executor, int i) throws SQLException {

    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return 0;
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
