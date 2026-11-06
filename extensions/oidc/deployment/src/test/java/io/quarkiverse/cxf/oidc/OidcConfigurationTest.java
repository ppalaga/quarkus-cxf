package io.quarkiverse.cxf.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.inject.Instance;
import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.QuarkusExtensionTest;

/** No inbound OIDC extension or provider configuration is present in this application. */
public class OidcConfigurationTest {
    @RegisterExtension
    static final QuarkusExtensionTest application = application();

    private static QuarkusExtensionTest application() {
        var app = new QuarkusExtensionTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(Echo.class))
                .overrideConfigKey("quarkus.http.test-port", "0")
                .overrideConfigKey("quarkus.oidc-client.discovery-enabled", "false")
                .overrideConfigKey("quarkus.oidc-client.early-tokens-acquisition", "false")
                .overrideConfigKey("quarkus.oidc-client.token-path", "http://localhost:1/token")
                .overrideConfigKey("quarkus.oidc-client.client-id", "application")
                .overrideConfigKey("quarkus.oidc-client.credentials.secret", "secret")
                .overrideConfigKey("quarkus.oidc-client.exchange.discovery-enabled", "false")
                .overrideConfigKey("quarkus.oidc-client.exchange.early-tokens-acquisition", "false")
                .overrideConfigKey("quarkus.oidc-client.exchange.token-path", "http://localhost:1/token")
                .overrideConfigKey("quarkus.oidc-client.exchange.client-id", "exchange")
                .overrideConfigKey("quarkus.oidc-client.exchange.grant.type", "exchange");
        for (String client : new String[] { "acquire", "disabled", "wrongGrant", "wrongAcquireGrant", "unsupported",
                "unknown" }) {
            app.overrideConfigKey("quarkus.cxf.client." + client + ".service-interface", Echo.class.getName())
                    .overrideConfigKey("quarkus.cxf.client." + client + ".client-endpoint-url", "http://localhost:1/soap");
        }
        return app.overrideConfigKey("quarkus.cxf.client.acquire.oidc.mode", "acquire")
                .overrideConfigKey("quarkus.cxf.client.disabled.oidc.client-name", "does-not-exist")
                .overrideConfigKey("quarkus.cxf.client.wrongGrant.oidc.mode", "exchange")
                .overrideConfigKey("quarkus.cxf.client.wrongAcquireGrant.oidc.mode", "acquire")
                .overrideConfigKey("quarkus.cxf.client.wrongAcquireGrant.oidc.client-name", "exchange")
                .overrideConfigKey("quarkus.cxf.client.unknown.oidc.mode", "acquire")
                .overrideConfigKey("quarkus.cxf.client.unknown.oidc.client-name", "does-not-exist")
                .overrideConfigKey("quarkus.cxf.client.unsupported.oidc.mode", "propagate")
                .overrideConfigKey("quarkus.cxf.client.unsupported.http-conduit-factory", "URLConnectionHTTPConduitFactory");
    }

    @CXFClient("acquire")
    Instance<Echo> acquire;
    @CXFClient("disabled")
    Instance<Echo> disabled;
    @CXFClient("wrongGrant")
    Instance<Echo> wrongGrant;
    @CXFClient("wrongAcquireGrant")
    Instance<Echo> wrongAcquireGrant;
    @CXFClient("unsupported")
    Instance<Echo> unsupported;
    @CXFClient("unknown")
    Instance<Echo> unknown;

    @Test
    @Tag("T-001")
    @Tag("T-002")
    void clientsCanBeCreatedWithoutInboundOidc() {
        assertThat(acquire.get()).isNotNull();
        assertThat(disabled.get()).isNotNull();
    }

    @Test
    @Tag("T-007")
    void rejectIncompatibleGrantAtCreation() {
        assertThatThrownBy(wrongGrant::get).hasStackTraceContaining("incompatible with grant");
        assertThatThrownBy(wrongAcquireGrant::get).hasStackTraceContaining("incompatible with grant");
    }

    @Test
    @Tag("T-007")
    void unknownNamedClientCannotFallBackToDefault() {
        assertThatThrownBy(unknown::get).hasStackTraceContaining("selected SOAP OIDC client is unavailable");
    }

    @Test
    @Tag("T-009")
    void rejectUnsupportedTransportAtCreation() {
        assertThatThrownBy(unsupported::get).hasStackTraceContaining("requires VertxHttpClientHTTPConduitFactory");
    }

    @WebService
    public interface Echo {
        @WebMethod
        String echo(String value);
    }
}
