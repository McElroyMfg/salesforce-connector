// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.jdbc;

import com.mcelroy.salesforceconnector.rest.SFClient;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class SFDriver implements Driver {
    private static Map<String, SFClient> clients = new HashMap<>();

    @Override
    public Connection connect(String s, Properties properties) throws SQLException {
        if (acceptsURL(s)) {
            String url = s.trim().replace("jdbc:sf:", "");
            if (!url.endsWith("/"))
                url = url + "/";
            String user = properties.getProperty("user");
            String password = properties.getProperty("password");
            String clientId = properties.getProperty("clientId");
            String clientSecret = properties.getProperty("clientSecret");
            String key = url + user + clientId;
            SFClient client;
            synchronized (clients) {
                client = clients.get(key);
                if (client == null) {
                    client = new SFClient(url, clientId, clientSecret, user, password);
                    clients.put(key, client);
                }
            }
            SFConnection connection = new SFConnection(client);
            return connection;
        }
        return null;
    }

    @Override
    public boolean acceptsURL(String s) throws SQLException {
        return s != null && s.startsWith("jdbc:sf:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String s, Properties properties) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return true;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    static {
        try {
            DriverManager.registerDriver(new SFDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Can not register SFDriver", e);
        }
    }
}
