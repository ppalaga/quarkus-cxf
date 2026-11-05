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
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "token-failures"))
public class OidcTokenFailureTest extends OidcTestSupport {
    static Stream<Arguments> failures() {
        return Stream.of("Acquire", "Exchange").flatMap(mode -> Stream.of("sync", "async")
                .flatMap(style -> Stream.of("oauth", "server", "malformed", "missing", "empty", "timeout", "refused")
                        .map(error -> Arguments.of(mode, style, error))));
    }

    @Tag("T-014")
    @ParameterizedTest(name = "T-014: {0}, {1}, {2}")
    @MethodSource("failures")
    void tokenFailurePreventsDispatch(String mode, String style, String error) {
        String client = ("refused".equals(error) ? "refused" : "failure") + mode;
        String selected = "failure-" + mode.toLowerCase();
        int before = grants(selected).size();
        if (!"refused".equals(error)) {
            control("token", new JsonObject().put("client", selected).put("error", error).put("secret", callerA));
        }
        String operation = id("T-014");
        JsonObject outcome = invoke(client, style, callerA, operation);
        failureBeforeSoap(outcome, operation);
        assertThat(outcome.getString("detail")).doesNotContain(callerA, callerB);
        if (!"refused".equals(error)) {
            assertThat(grants(selected).size()).as("A grant must actually have been attempted").isGreaterThan(before);
        }
    }
}
