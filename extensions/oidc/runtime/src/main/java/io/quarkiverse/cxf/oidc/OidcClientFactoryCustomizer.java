package io.quarkiverse.cxf.oidc;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;

import io.quarkiverse.cxf.CXFClientInfo;
import io.quarkiverse.cxf.ClientRequestAuthentication;
import io.quarkiverse.cxf.CxfClientProducer.ClientFactoryCustomizer;
import io.quarkiverse.cxf.CxfFixedConfig;
import io.quarkiverse.cxf.HTTPConduitImpl;
import io.quarkiverse.cxf.QuarkusHTTPConduitFactory;
import io.quarkus.oidc.client.Tokens;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class OidcClientFactoryCustomizer implements ClientFactoryCustomizer {
    @Inject
    CxfOidcConfig config;
    @Inject
    CxfFixedConfig cxfConfig;
    @Inject
    OidcCredentialResolver credentials;
    @Inject
    OidcTokenProviders tokens;

    @Override
    public void customize(CXFClientInfo info, JaxWsProxyFactoryBean factory) {
        CxfOidcConfig.Client client = config.clients().get(info.getConfigKey());
        if (client == null || client.oidc().mode() == CxfOidcConfig.Mode.DISABLED) {
            return;
        }
        var settings = client.oidc();
        if (info.getAuth().scheme().isPresent() || info.getAuth().username().isPresent()
                || info.getAuth().password().isPresent() || info.getAuth().token().isPresent()) {
            throw new IllegalStateException("SOAP OIDC authentication cannot be combined with configured HTTP authentication");
        }
        if (QuarkusHTTPConduitFactory.resolve(info, cxfConfig) != HTTPConduitImpl.VertxHttpClientHTTPConduitFactory) {
            throw new IllegalStateException("SOAP OIDC authentication requires VertxHttpClientHTTPConduitFactory");
        }
        final Function<Map<String, String>, Uni<Tokens>> provider = settings.mode() == CxfOidcConfig.Mode.PROPAGATE
                ? null
                : tokens.get(settings.clientName(), settings.mode());
        factory.getProperties().put(ClientRequestAuthentication.KEY, new ClientRequestAuthentication() {
            @Override
            public CompletionStage<String> authorization() {
                try {
                    Uni<String> token = switch (settings.mode()) {
                        case ACQUIRE -> provider.apply(Map.of()).map(Tokens::getAccessToken);
                        case PROPAGATE -> credentials.capture();
                        case EXCHANGE -> credentials.capture().flatMap(subject -> provider.apply(Map.of(
                                "subject_token", subject,
                                "subject_token_type", "urn:ietf:params:oauth:token-type:access_token")))
                                .map(Tokens::getAccessToken);
                        default -> throw new IllegalStateException("Unexpected disabled authentication provider");
                    };
                    return token.map(value -> {
                        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                            throw new IllegalStateException("The OIDC client did not supply a usable access token");
                        }
                        return "Bearer " + value;
                    }).onFailure().transform(error -> new IllegalStateException("SOAP OIDC authentication failed ("
                            + settings.mode().name().toLowerCase(java.util.Locale.ROOT) + ")"))
                            .subscribeAsCompletionStage();
                } catch (RuntimeException error) {
                    return CompletableFuture.failedFuture(new IllegalStateException("SOAP OIDC authentication failed"));
                }
            }

            @Override
            public boolean sameOrigin(URI source, URI target) {
                return OidcRedirectOrigin.sameOrigin(source, target);
            }
        });
        factory.getFeatures().stream().filter(LoggingFeature.class::isInstance).map(LoggingFeature.class::cast)
                .forEach(logging -> logging.addSensitiveProtocolHeaderNames(Set.of("Authorization")));
    }
}
