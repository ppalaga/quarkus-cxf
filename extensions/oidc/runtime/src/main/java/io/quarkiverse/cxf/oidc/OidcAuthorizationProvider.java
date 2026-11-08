package io.quarkiverse.cxf.oidc;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkiverse.cxf.vertx.http.client.AuthorizationProvider;
import io.quarkus.arc.Arc;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.client.runtime.OidcClientsConfig;
import io.quarkus.oidc.client.runtime.TokensHelper;
import io.quarkus.oidc.common.runtime.OidcConstants;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Future;

public class OidcAuthorizationProvider implements AuthorizationProvider {
    private static final Logger log = Logger.getLogger(OidcAuthorizationProvider.class);
    private static final String BEARER_SCHEME_WITH_SPACE = OidcConstants.BEARER_SCHEME + " ";

    private final CxfOidcConfig cxfOidcConfig;
    private final OidcClientsConfig oidcClientsConfig;
    private volatile OidcClients oidcClients;
    // TODO: figure out whether there is/could be any Quarkus wide facility providing shared TokenHelpers; it would be strange to have separate cache for REST client and SOAP
    private final Map<String, TokensHelper> tokensHelpersByOidcClientName = new ConcurrentHashMap<>();

    public OidcAuthorizationProvider(CxfOidcConfig cxfOidcConfig, OidcClientsConfig oidcClientsConfig) {
        this.cxfOidcConfig = cxfOidcConfig;
        this.oidcClientsConfig = oidcClientsConfig;
    }

    @Override
    public Future<String> getAuthorization(String cxfClientKey) {
        final CxfOidcConfig.Client cxfOidcClientConfig = cxfOidcConfig.clients().get(cxfClientKey);
        if (cxfOidcClientConfig != null) {
            final CxfOidcConfig.Oidc oidcConfig = cxfOidcClientConfig.oidc();
            return switch (oidcConfig.mode()) {
                case DISABLED -> Future.succeededFuture(); // nothing to do
                case ACQUIRE -> acquire(cxfClientKey, oidcConfig);
                case PROPAGATE -> propagate(oidcConfig);
                case EXCHANGE -> exchange(oidcConfig);
                default -> throw new IllegalStateException(
                        "Unexpected quarkus.cxf.\"" + cxfClientKey + "\".oidc.mode '+ oidcConfig.mode() +'");
            };
        }
        /* If the client does not have oidc configured, it is disabled */
        return Future.succeededFuture();
    }

    private Future<String> acquire(String cxfClientKey, CxfOidcConfig.Oidc oidcConfig) {
        Optional<String> oidcClientName = oidcConfig.clientName();
        final Optional<OidcClient> namedOidcClient = oidcClientName.map(name -> getOidcClients().getClient(name));
        if (oidcClientName.isPresent() && namedOidcClient.isEmpty()) {
            return Future.failedFuture(new IllegalStateException(
                    "quarkus.cxf.\"" + cxfClientKey + "\".oidc.client-name references an unknown OIDC client: "
                            + oidcClientName.get() + ". Select any of " + oidcClientsConfig.namedClients().keySet()
                            + " or unset it to select the default OIDC client"));
        }
        final OidcClient oidcClient = namedOidcClient.orElseGet(getOidcClients()::getClient);
        Uni<Tokens> tokens = getTokenHelper(oidcClientName.orElse(OidcClientsConfig.DEFAULT_CLIENT_KEY)).getTokens(oidcClient);
        return UniHelper.toFuture(tokens)
                .map(result -> BEARER_SCHEME_WITH_SPACE + result.getAccessToken());
    }

    private OidcClients getOidcClients() {
        OidcClients result = oidcClients;
        if (result == null) {
            result = oidcClients = Arc.container().instance(OidcClients.class).get();
        }
        return result;
    }

    private Future<String> exchange(CxfOidcConfig.Oidc oidcConfig) {
        // TODO
        throw new UnsupportedOperationException();
    }

    private Future<String> propagate(CxfOidcConfig.Oidc oidcConfig) {
        // TODO
        throw new UnsupportedOperationException();
    }

    private TokensHelper getTokenHelper(String oidcClientName) {
        Objects.requireNonNull(oidcClientName, "oidcClientName");
        return tokensHelpersByOidcClientName.computeIfAbsent(oidcClientName, k -> {
            OidcClientConfig oidcClientConfig = oidcClientsConfig.namedClients().get(oidcClientName);
            return oidcClientConfig != null ? new TokensHelper(oidcClientConfig.tokenCacheMaxSize()) : new TokensHelper();
        });
    }
}
