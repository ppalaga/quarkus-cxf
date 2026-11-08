package io.quarkiverse.cxf.oidc;

import io.quarkiverse.cxf.vertx.http.client.AuthorizationProvider;
import io.quarkiverse.cxf.vertx.http.client.AuthorizationProviderLookup;
import io.quarkus.oidc.client.runtime.OidcClientsConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Installs {@link OidcAuthorizationProvider} as an {@link AuthorizationProvider}.
 *
 * @since 4.0.0
 */
@Recorder
public class QuarkusCxfOidcRecorder {

    private final RuntimeValue<CxfOidcConfig> cxfOidcConfig;
    private final RuntimeValue<OidcClientsConfig> oidcClientsConfig;

    public QuarkusCxfOidcRecorder(RuntimeValue<CxfOidcConfig> cxfOidcConfig,
            RuntimeValue<OidcClientsConfig> oidcClientsConfig) {
        this.cxfOidcConfig = cxfOidcConfig;
        this.oidcClientsConfig = oidcClientsConfig;
    }

    public void init() {
        AuthorizationProvider provider = new OidcAuthorizationProvider(cxfOidcConfig.getValue(), oidcClientsConfig.getValue());
        AuthorizationProviderLookup.setAuthorizationProvider(provider);
    }
}
