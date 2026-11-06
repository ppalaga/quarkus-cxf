package io.quarkiverse.cxf.oidc;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.Arc;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class OidcCredentialResolver {
    @Inject
    CurrentIdentityAssociation identity;

    public Uni<String> capture() {
        if (!Arc.container().requestContext().isActive()) {
            return Uni.createFrom().failure(new IllegalStateException("SOAP OIDC authentication requires an active request"));
        }
        // Capture the deferred identity on the caller thread, rather than resolving the request-scoped bean on a worker.
        return identity.getDeferredIdentity().map(caller -> {
            if (caller.isAnonymous()) {
                throw new IllegalStateException("SOAP OIDC authentication requires an authenticated caller");
            }
            // Quarkus AccessTokenCredential is a bearer TokenCredential. Using its security API base keeps
            // acquire-only applications independent of inbound OIDC configuration and its deployment extension.
            List<TokenCredential> credentials = caller.getCredentials().stream()
                    .filter(TokenCredential.class::isInstance).map(TokenCredential.class::cast)
                    .filter(token -> "bearer".equals(token.getType())).toList();
            if (credentials.size() != 1 || credentials.get(0).getToken() == null
                    || credentials.get(0).getToken().isBlank()) {
                throw new IllegalStateException("SOAP OIDC authentication requires exactly one access-token credential");
            }
            return credentials.get(0).getToken();
        });
    }
}
