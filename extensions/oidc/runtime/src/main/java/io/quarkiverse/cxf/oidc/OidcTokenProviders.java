package io.quarkiverse.cxf.oidc;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.oidc.client.runtime.AbstractTokensProducer;
import io.quarkus.oidc.client.runtime.OidcClientBuildTimeConfig;
import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.client.runtime.OidcClientsConfig;
import io.quarkus.oidc.client.runtime.OidcClientsImpl;
import io.quarkus.oidc.client.runtime.TokensHelper;
import io.smallrye.mutiny.Uni;

/** Application-owned adapters; all caching, expiry and refresh decisions remain in Quarkus. */
@ApplicationScoped
public class OidcTokenProviders {
    @Inject
    OidcClientsConfig config;
    @Inject
    OidcClientBuildTimeConfig buildConfig;
    @Inject
    Instance<OidcClientsImpl> clients;

    private final Map<String, Function<Map<String, String>, Uni<Tokens>>> providers = new ConcurrentHashMap<>();

    public Function<Map<String, String>, Uni<Tokens>> get(Optional<String> name, CxfOidcConfig.Mode mode) {
        if (name.isPresent() && !config.namedClients().containsKey(name.get())) {
            throw new IllegalStateException("The selected SOAP OIDC client is unavailable");
        }
        OidcClientConfig selected = name.isPresent() ? config.namedClients().get(name.get())
                : OidcClientsConfig.getDefaultClient(config);
        if (selected == null || !selected.clientEnabled() || !buildConfig.enabled()) {
            throw new IllegalStateException("The selected SOAP OIDC client is unavailable");
        }
        OidcClientConfig.Grant.Type grant = selected.grant().type();
        if (mode == CxfOidcConfig.Mode.ACQUIRE && grant != OidcClientConfig.Grant.Type.CLIENT
                || mode == CxfOidcConfig.Mode.EXCHANGE && grant != OidcClientConfig.Grant.Type.EXCHANGE) {
            throw new IllegalStateException("SOAP OIDC mode " + mode + " is incompatible with grant " + grant);
        }
        return providers.computeIfAbsent(name.orElse(OidcClientsConfig.DEFAULT_CLIENT_KEY), key -> {
            try {
                if (mode == CxfOidcConfig.Mode.EXCHANGE) {
                    OidcClient client = name.map(clients.get()::getClient).orElseGet(() -> clients.get().getClient());
                    TokensHelper helper = new TokensHelper(selected.tokenCacheMaxSize());
                    // Caller-dependent exchange is lazy; a background refresh has no subject token.
                    return parameters -> helper.getTokens(client, parameters, false);
                }
                Acquisition producer = new Acquisition(name);
                producer.oidcClientsConfig = config;
                producer.oidcClientBuildTimeConfig = buildConfig;
                producer.oidcClientsInstance = clients;
                producer.init();
                return producer::getTokens;
            } catch (RuntimeException error) {
                // Provider errors may include a token endpoint's response body. Do not keep that cause chain.
                throw new IllegalStateException("Could not initialize SOAP OIDC token acquisition");
            }
        });
    }

    @PreDestroy
    void close() {
        // Quarkus owns client connections and periodic refresh registration until application shutdown.
        providers.clear();
    }

    private static final class Acquisition extends AbstractTokensProducer {
        private final Optional<String> name;

        private Acquisition(Optional<String> name) {
            this.name = name;
        }

        @Override
        protected Optional<String> clientId() {
            return name;
        }
    }
}
