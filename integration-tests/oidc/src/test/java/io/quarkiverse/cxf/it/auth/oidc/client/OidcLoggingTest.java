package io.quarkiverse.cxf.it.auth.oidc.client;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "logging", value = "global"))
public class OidcLoggingTest extends OidcLoggingContract {
}
