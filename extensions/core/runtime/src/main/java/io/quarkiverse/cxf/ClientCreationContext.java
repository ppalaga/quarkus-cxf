package io.quarkiverse.cxf;

import org.apache.cxf.transport.http.HTTPConduitFactory;

/** Client-local WSDL transport configuration, including nested imports, without modifying the shared bus. */
public final class ClientCreationContext implements AutoCloseable {
    private static final ThreadLocal<ClientCreationContext> CURRENT = new ThreadLocal<>();
    private final ClientCreationContext previous;
    private final HTTPConduitFactory conduitFactory;
    private final ClientRequestAuthentication.Request authentication;

    public ClientCreationContext(HTTPConduitFactory conduitFactory, ClientRequestAuthentication.Request authentication) {
        this.previous = CURRENT.get();
        this.conduitFactory = conduitFactory;
        this.authentication = authentication;
        CURRENT.set(this);
    }

    public static ClientCreationContext current() {
        return CURRENT.get();
    }

    public HTTPConduitFactory conduitFactory() {
        return conduitFactory;
    }

    public ClientRequestAuthentication.Request authentication() {
        return authentication;
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
