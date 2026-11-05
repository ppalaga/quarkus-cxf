package io.quarkiverse.cxf.it.auth.oidc.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "default-grant", value = "exchange"))
public class OidcDefaultExchangeTest extends OidcTestSupport {
    @Tag("T-004")
    @ParameterizedTest(name = "T-004: default exchange through {0}, {1}")
    @MethodSource("ingressAndStyles")
    void defaultExchange(String ingress, String style) {
        String operation = id("T-004-default");
        success(ingress(ingress, "defaultClient", style, callerA, operation), operation);
        assertExchange(operation, callerA, "default");
    }
}
