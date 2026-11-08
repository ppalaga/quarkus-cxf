package io.quarkiverse.cxf.vertx.http.client;

import io.vertx.core.Future;

/**
 * Allows extensions to record a specific {@link AuthorizationProvider} instance;
 *
 * @since 4.0.0
 */
public class AuthorizationProviderLookup {
    private static AuthorizationProvider authorizationProvider = s -> Future.succeededFuture();

    public static void setAuthorizationProvider(AuthorizationProvider authorizationProvider) {
        AuthorizationProviderLookup.authorizationProvider = authorizationProvider;
    }

    public static AuthorizationProvider get() {
        return authorizationProvider;
    }
}
