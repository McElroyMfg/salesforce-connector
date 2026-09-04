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
            String environment = null;

            // an optional sandbox name may follow the host: jdbc:sf:https://host/mysandbox
            int pathStart = url.indexOf('/', url.indexOf("//") + 2);
            if (pathStart >= 0) {
                String path = url.substring(pathStart + 1);
                if (!path.trim().isEmpty())
                    environment = path.trim();
                url = url.substring(0, pathStart + 1);
            }

            if (!url.endsWith("/"))
                url = url + "/";
            String user = properties.getProperty("user");
            String clientId = properties.getProperty("clientId");
            String clientSecret = properties.getProperty("clientSecret");
            String privateKey = properties.getProperty("privateKey");

            boolean jwt = privateKey != null && !privateKey.trim().isEmpty();

            if (clientId == null || clientId.trim().isEmpty())
                throw new SQLException("clientId property is required");
            if (jwt) {
                if (user == null || user.trim().isEmpty())
                    throw new SQLException("user property is required for JWT authentication");
            } else {
                if (clientSecret == null || clientSecret.trim().isEmpty())
                    throw new SQLException("clientSecret property is required for client credentials authentication");
            }

            String key = url + user + clientId + "@"
                    + (privateKey == null ? "" : Integer.toHexString(privateKey.hashCode()));
            SFClient client;
            synchronized (clients) {
                client = clients.get(key);
                if (client == null) {
                    client = new SFClient(url, clientId, clientSecret, user, privateKey);
                    clients.put(key, client);
                }
            }
            SFConnection connection = new SFConnection(client, environment);
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
