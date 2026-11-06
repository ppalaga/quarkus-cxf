package io.quarkiverse.cxf.oidc;

import java.net.URI;

/** The origin, including normalized HTTP(S) ports, to which a credential was issued. */
public final class OidcRedirectOrigin {
    private OidcRedirectOrigin() {
    }

    public static boolean sameOrigin(URI source, URI target) {
        return source.getScheme() != null && target.getScheme() != null
                && source.getHost() != null && target.getHost() != null
                && source.getScheme().equalsIgnoreCase(target.getScheme())
                && source.getHost().equalsIgnoreCase(target.getHost()) && port(source) == port(target);
    }

    private static int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
