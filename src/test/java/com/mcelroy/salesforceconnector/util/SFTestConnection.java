package com.mcelroy.salesforceconnector.util;

import com.mcelroy.salesforceconnector.jdbc.SFConnection;
import com.mcelroy.salesforceconnector.rest.SFClientConnection;

public class SFTestConnection extends SFConnection {
    SFClientConnection connection;

    public SFTestConnection(SFClientConnection connection){
        super(null);
        this.connection = connection;
    }

    public SFClientConnection getClientConnection() {
        return connection;
    }
}
