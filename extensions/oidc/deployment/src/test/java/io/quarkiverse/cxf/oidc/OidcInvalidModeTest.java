package io.quarkiverse.cxf.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.cxf.oidc.OidcConfigurationTest.Echo;
import io.quarkus.test.QuarkusExtensionTest;

/** T-007 is an augmentation failure, so it needs its own application archive rather than the running HTTP fixture. */
public class OidcInvalidModeTest {
    @RegisterExtension
    static final QuarkusExtensionTest app = new QuarkusExtensionTest()
            .withApplicationRoot(archive -> archive.addClass(Echo.class))
            .overrideConfigKey("quarkus.keycloak.devservices.enabled", "false")
            .overrideConfigKey("quarkus.oidc-client.client-enabled", "false")
            .overrideConfigKey("quarkus.cxf.client.invalid.service-interface", Echo.class.getName())
            .overrideConfigKey("quarkus.cxf.client.invalid.oidc.mode", "not-a-mode")
            .assertException(error -> assertThat(error).hasStackTraceContaining("not-a-mode"));

    @Test
    @Tag("T-007")
    void invalidModeCannotStart() {
        fail("T-007: an unsupported OIDC mode must be rejected during configuration");
    }
}
