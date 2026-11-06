package io.quarkiverse.cxf.oidc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.client.runtime.OidcClientsConfig;
import io.quarkus.oidc.common.OidcEndpoint;
import io.quarkus.oidc.common.OidcRequestFilter;
import io.quarkus.oidc.common.OidcResponseFilter;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;

/** Bound token requests and validate/redact responses before Quarkus caches or logs them. */
@ApplicationScoped
@OidcEndpoint(OidcEndpoint.Type.TOKEN)
public class OidcEndpointFilter implements OidcRequestFilter, OidcResponseFilter {
    @Inject
    CxfOidcConfig config;
    @Inject
    OidcClientsConfig clients;

    @Override
    public Uni<Void> filter(OidcRequestFilterContext request) {
        OidcClientConfig selected = selectedClient(request.contextProperties().getString("client-id"));
        if (selected != null) {
            // Beta2 applies connection-timeout only to establishing a connection, not to receiving its response.
            request.request().timeout(selected.connectionTimeout().toMillis());
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> filter(OidcResponseFilterContext response) {
        OidcClientConfig selected = selectedClient(response.requestProperties().getString("client-id"));
        if (selected != null) {
            if (response.statusCode() != 200) {
                response.responseBody(Buffer.buffer("{\"error\":\"SOAP OIDC token request failed\"}"));
            } else {
                try {
                    String token = response.responseBody().toJsonObject().getString(selected.grant().accessTokenProperty());
                    if (token == null || token.isBlank() || token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
                        throw new IllegalArgumentException();
                    }
                } catch (RuntimeException error) {
                    // Reject unusable tokens before TokensHelper can retain a successful but invalid token response.
                    return Uni.createFrom().failure(new IllegalStateException("SOAP OIDC token response is invalid"));
                }
            }
        }
        return Uni.createFrom().voidItem();
    }

    private OidcClientConfig selectedClient(String clientId) {
        if (clientId == null) {
            return null;
        }
        return config.clients().values().stream()
                .map(CxfOidcConfig.Client::oidc)
                .filter(oidc -> oidc.mode() == CxfOidcConfig.Mode.ACQUIRE || oidc.mode() == CxfOidcConfig.Mode.EXCHANGE)
                .map(oidc -> {
                    var selected = oidc.clientName().isPresent() ? clients.namedClients().get(oidc.clientName().get())
                            : OidcClientsConfig.getDefaultClient(clients);
                    return selected != null && clientId.equals(selected.id().orElse(oidc.clientName().orElse("Default")))
                            ? selected
                            : null;
                })
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }
}
