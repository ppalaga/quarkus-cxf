package io.quarkiverse.cxf.it.auth.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "authorization-failures"))
public class OidcAuthorizationFailureTest extends OidcTestSupport {
    static Stream<Arguments> unauthorized() {
        return Stream.of("acquireNamed", "propagate", "exchangeNamed").flatMap(client -> Stream.of("sync", "async")
                .flatMap(style -> Stream.of("challenge", "unauthorized", "forbidden")
                        .map(response -> Arguments.of(client, style, response))));
    }

    @Tag("T-015")
    @ParameterizedTest(name = "T-015: {0}, {1}, {2} must not replay SOAP")
    @MethodSource("unauthorized")
    void noReplay(String client, String style, String response) {
        String operation = id("T-015");
        control("soap", new JsonObject().put("id", operation).put("status", "forbidden".equals(response) ? 403 : 401)
                .put("challenge", "challenge".equals(response)));
        JsonObject outcome = invoke(client, style, callerA, operation);
        assertThat(outcome.getBoolean("success")).isFalse();
        assertThat(posts(operation)).as("One receipt, including challenged authorization failures").hasSize(1);
        sentToken(operation);
        // An explicitly requested second operation is distinct from an automatic retransmit.
        String later = id("T-015-later");
        success(invoke(client, style, callerA, later), later);
        assertThat(posts(operation)).hasSize(1);
        assertThat(posts(later)).hasSize(1);
    }
}
