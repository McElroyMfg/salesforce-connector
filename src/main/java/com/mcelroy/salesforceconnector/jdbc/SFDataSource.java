// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class SFDataSource implements DataSource {
    private String url;
    private String clientId;
    private String clientSecret;
    private String user;
    private String privateKey;
    private String password;

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(null, null);
    }

    @Override
    public Connection getConnection(String s, String s1) throws SQLException {
        Properties p = new Properties();
        if (clientId != null)
            p.setProperty("clientId", clientId);
        if (clientSecret != null)
            p.setProperty("clientSecret", clientSecret);
        if (user != null)
            p.setProperty("user", user);
        if (privateKey != null)
            p.setProperty("privateKey", privateKey);
        if (password != null)
            p.setProperty("password", password);
        // connection arguments override the configured values; some applications
        // always pass a password (even blank) so it is also a backup way to set the JWT key
        if (s != null)
            p.setProperty("user", s);
        if (s1 != null)
            p.setProperty("password", s1);
        return DriverManager.getConnection(url, p);
    }

    @Override
    public <T> T unwrap(Class<T> aClass) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not Supported");
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return false;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter printWriter) throws SQLException {

    }

    @Override
    public void setLoginTimeout(int i) throws SQLException {

    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 2000;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    public void close() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    static {
        SFDriver d = new SFDriver(); // make sure loaded and registered if container doesn't support JNDI auto registration
    }
}
