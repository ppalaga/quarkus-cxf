package io.quarkiverse.cxf.oidc;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocFilename;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/** OIDC authentication of injected SOAP clients. */
@ConfigMapping(prefix = "quarkus.cxf")
@ConfigDocFilename("quarkus-cxf-oidc.adoc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface CxfOidcConfig {
    /** SOAP client configurations. */
    @WithName("client")
    @ConfigDocMapKey("client-name")
    Map<String, Client> clients();

    @ConfigGroup
    interface Client {
        /** OpenID Connect authentication. */
        Oidc oidc();
    }

    @ConfigGroup
    interface Oidc {
        /**
         * Authentication mode: disabled, acquire a client-credentials token, propagate the current caller's
         * access token, or exchange it using RFC 8693.
         * Only injected clients using the Vert.x HTTP conduit are supported.
         *
         * @since 4.0.0
         */
        @WithDefault("disabled")
        Mode mode();

        /**
         * Quarkus OIDC client selected for acquisition or exchange.
         * When absent, use the default OIDC client.
         * Ignored in disabled and propagate modes.
         *
         * @since 4.0.0
         */
        Optional<String> clientName();
    }

    enum Mode {
        DISABLED,
        ACQUIRE,
        PROPAGATE,
        EXCHANGE
    }
}
