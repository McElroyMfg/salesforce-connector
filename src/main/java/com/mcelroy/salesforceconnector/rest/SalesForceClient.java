// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.rest;

import java.util.LinkedHashMap;
import java.util.Map;

public class SalesForceClient {
    private String host;
    private String clientId;
    private String clientSecret;
    private String user;
    private String pass;

    SalesForceAPI production = null;
    Map<String, SalesForceAPI> sandboxes = new LinkedHashMap<String, SalesForceAPI>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 5; // limit to 5 active sandbox api connections cached to prevent memory abuse
        }
    };


    public SalesForceClient(String host, String clientId, String clientSecret, String user, String pass) {
        this.host = host;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.user = user;
        this.pass = pass;
    }

    public synchronized SalesForceAPI.Connection getConnection(String sandboxName) {
        if (sandboxName == null || sandboxName.trim().equals("")) {
            if (production == null) {
                production = new SalesForceAPI(host, clientId, clientSecret, user, pass);
            }
            return production.getConnection();
        } else {
            SalesForceAPI api = sandboxes.get(sandboxName);
            if (api == null) {
                String sbHost = host.replaceFirst("\\.", "--" + sandboxName + ".");
                String sbUser = user + "." + sandboxName;
                api = new SalesForceAPI(sbHost, clientId, clientSecret, sbUser, pass);
                sandboxes.put(sandboxName, api);
            }
            return api.getConnection();
        }
    }
}
