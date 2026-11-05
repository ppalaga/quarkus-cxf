package io.quarkiverse.cxf.it.auth.oidc.client;

import org.junit.jupiter.api.Tag;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/** T-026: execute the same HTTP-observed contract against the packaged/native application. */
@QuarkusIntegrationTest
@Tag("T-026")
public class OidcConfigurationIT extends OidcConfigurationTest {
}
