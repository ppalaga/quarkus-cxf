package io.quarkiverse.cxf;

import java.net.URI;
import java.util.concurrent.CompletionStage;

/** Extension-neutral authentication for one invocation of an injected client. */
public interface ClientRequestAuthentication {
    String KEY = ClientRequestAuthentication.class.getName();

    /** Capture caller state on the invoking thread, before any worker dispatch. */
    CompletionStage<String> authorization();

    /** Whether a credential issued for the original destination may be sent to this destination. */
    boolean sameOrigin(URI original, URI destination);

    final class Request {
        private final ClientRequestAuthentication provider;
        private final URI origin;
        private final CompletionStage<String> authorization;

        public Request(ClientRequestAuthentication provider, URI origin) {
            this.provider = provider;
            this.origin = origin;
            this.authorization = provider.authorization();
        }

        public CompletionStage<String> authorization() {
            return authorization;
        }

        public boolean allows(URI destination) {
            return provider.sameOrigin(origin, destination);
        }
    }

    /** Scoped transfer to the CXF message; never stored in the shared proxy request context. */
    final class Scope implements AutoCloseable {
        private static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();
        private final Request previous;

        public Scope(Request request) {
            previous = CURRENT.get();
            CURRENT.set(request);
        }

        public static Request current() {
            return CURRENT.get();
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
}
