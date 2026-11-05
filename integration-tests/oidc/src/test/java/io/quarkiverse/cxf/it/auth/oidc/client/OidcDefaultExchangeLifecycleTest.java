package io.quarkiverse.cxf.it.auth.oidc.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = {
        @ResourceArg(name = "suite", value = "default-exchange-lifecycle"),
        @ResourceArg(name = "default-grant", value = "exchange") })
public class OidcDefaultExchangeLifecycleTest extends OidcTestSupport {
    @Test
    @Tag("T-010")
    void defaultExchangeReuseAndReacquisition() {
        OidcTokenLifecycleTest.verifyReuseAndReacquisition(this, "defaultClient", "default", OidcTestResource.EXCHANGE_GRANT);
    }
}
