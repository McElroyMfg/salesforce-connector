// SPDX-FileCopyrightText: © 2021 McElroy <www.mcelroy.com>
// SPDX-License-Identifier: MIT
package com.mcelroy.salesforceconnector.rest;

import java.util.LinkedHashMap;
import java.util.Map;

public class SFClient {
    private final String host;
    private final String clientId;
    private final String clientSecret;
    private final String user;
    private final String pass;

    SFRestConnection.SFRestConfig production = null;
    Map<String, SFRestConnection.SFRestConfig> sandboxes = new LinkedHashMap<String, SFRestConnection.SFRestConfig>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry eldest) {
            return size() > 5; // limit to 5 active sandbox api connections cached to prevent memory abuse
        }
    };


    public SFClient(String host, String clientId, String clientSecret, String user, String pass) {
        this.host = host;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.user = user;
        this.pass = pass;
    }


    public synchronized SFClientConnection getConnection(String sandboxName) {
        if (sandboxName == null || sandboxName.trim().equals("")) {
            if (production == null) {
                production = new SFRestConnection.SFRestConfig(host, clientId, clientSecret, user, pass);
            }
            return new SFClientConnection(production);
        } else {
            SFRestConnection.SFRestConfig config = sandboxes.get(sandboxName);
            if (config == null) {
                String sbHost = host.replaceFirst("\\.", "--" + sandboxName + ".sandbox."); // SF domain change required summer 2022
                String sbUser = user + "." + sandboxName;
                config = new SFRestConnection.SFRestConfig(sbHost, clientId, clientSecret, sbUser, pass);
                sandboxes.put(sandboxName, config);
            }
            return new SFClientConnection(config);
        }
    }
}
