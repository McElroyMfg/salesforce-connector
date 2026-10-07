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
            String password = properties.getProperty("password"); // tolerated for compatibility

            // some applications always set password (even blank); accept it, and
            // allow it as a backup way to supply the JWT private key
            privateKey = resolvePrivateKey(privateKey, password);

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
            long metadataCacheTtlSeconds;
            int metadataCacheMaxEntries;
            try {
                metadataCacheTtlSeconds = Long.parseLong(properties.getProperty("metadataCacheTtlSeconds", "300"));
                metadataCacheMaxEntries = Integer.parseInt(properties.getProperty("metadataCacheMaxEntries", "500"));
                if (metadataCacheMaxEntries <= 0)
                    throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new SQLException("Invalid metadata cache properties: TTL must be an integer and max entries must be positive", e);
            }
            SFConnection connection = new SFConnection(client, environment,
                    metadataCacheTtlSeconds, metadataCacheMaxEntries);
            return connection;
        }
        return null;
    }

    @Override
    public boolean acceptsURL(String s) throws SQLException {
        return s != null && s.startsWith("jdbc:sf:");
    }

    /**
     * Resolves the JWT private key from the driver properties.
     * An explicit {@code privateKey} wins; otherwise a {@code password} that
     * contains a PEM private key is used as a backup way to supply the key.
     * A blank or real password is ignored (some applications always set it).
     */
    public static String resolvePrivateKey(String privateKey, String password) {
        if (privateKey != null && !privateKey.trim().isEmpty())
            return privateKey; // explicit privateKey wins
        if (password != null && password.contains("PRIVATE KEY"))
            return password; // PEM key pasted into the password field
        return privateKey; // blank/real password -> ignored
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String s, Properties properties) throws SQLException {
        if (!acceptsURL(s))
            return new DriverPropertyInfo[0];

        Properties info = properties == null ? new Properties() : properties;
        String privateKey = resolvePrivateKey(
                info.getProperty("privateKey"),
                info.getProperty("password"));

        DriverPropertyInfo[] p = new DriverPropertyInfo[7];
        p[0] = new DriverPropertyInfo("clientId", info.getProperty("clientId"));
        p[0].required = true;
        p[0].description = "Salesforce Connected App consumer key";

        p[1] = new DriverPropertyInfo("clientSecret", null); // never echo secrets back to the caller
        p[1].required = privateKey == null || privateKey.trim().isEmpty();
        p[1].description = "Connected App consumer secret (required for client credentials authentication)";

        p[2] = new DriverPropertyInfo("user", info.getProperty("user"));
        p[2].required = privateKey != null && !privateKey.trim().isEmpty();
        p[2].description = "Salesforce username (required for JWT authentication, used as the JWT sub claim)";

        p[3] = new DriverPropertyInfo("privateKey", null); // never echo secrets back to the caller
        p[3].required = false;
        p[3].description = "PKCS#8 PEM RSA private key; presence selects JWT bearer authentication";

        p[4] = new DriverPropertyInfo("password", null); // never echo secrets back to the caller
        p[4].required = false;
        p[4].description = "Accepted for compatibility; if privateKey is not set and this contains a PEM private key it is used as the JWT signing key";

        p[5] = new DriverPropertyInfo("metadataCacheTtlSeconds", info.getProperty("metadataCacheTtlSeconds", "300"));
        p[5].description = "Metadata cache lifetime in seconds; zero or negative disables caching";

        p[6] = new DriverPropertyInfo("metadataCacheMaxEntries", info.getProperty("metadataCacheMaxEntries", "500"));
        p[6].description = "Maximum metadata cache entries per connection (positive integer)";

        return p;
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
