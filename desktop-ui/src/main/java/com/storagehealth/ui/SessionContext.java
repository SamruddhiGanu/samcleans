package com.storagehealth.ui;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;

/**
 * Application-wide singleton that holds the active scan session ID.
 * All tabs observe this value so they automatically react when a scan completes.
 */
public class SessionContext {

    private static final SessionContext INSTANCE = new SessionContext();
    private final LongProperty sessionId = new SimpleLongProperty(-1L);

    private SessionContext() {}

    public static SessionContext get() { return INSTANCE; }

    public LongProperty sessionIdProperty() { return sessionId; }

    public long getSessionId() { return sessionId.get(); }

    public void setSessionId(long id) { sessionId.set(id); }

    public boolean hasSession() { return sessionId.get() > 0; }
}
