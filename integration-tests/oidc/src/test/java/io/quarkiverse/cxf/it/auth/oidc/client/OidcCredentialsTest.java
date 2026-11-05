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
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "credentials"))
public class OidcCredentialsTest extends OidcTestSupport {
    static Stream<Arguments> identities() {
        return Stream.of("propagate", "exchangeNamed")
                .flatMap(client -> Stream.of("sync", "async")
                        .flatMap(style -> Stream.of("anonymous", "absent", "ambiguous", "contextless")
                                .map(kind -> Arguments.of(client, style, kind))));
    }

    static Stream<Arguments> validIdentities() {
        return Stream.of("propagate", "exchangeNamed")
                .flatMap(client -> Stream.of(false, true).map(rawHeader -> Arguments.of(client, rawHeader)));
    }

    @Tag("T-005")
    @ParameterizedTest(name = "T-005: {0} uses the identity credential; raw header present={1}")
    @MethodSource("validIdentities")
    void identityOverridesRawHeader(String client, boolean rawHeader) {
        String operation = id("T-005");
        success(identity(client, "single", "sync", rawHeader ? callerB : null, operation, false, false), operation);
        if ("propagate".equals(client)) {
            assertThat(sentToken(operation)).isEqualTo(callerA);
        } else {
            assertExchange(operation, callerA, "exchange");
        }
    }

    @Tag("T-005")
    @Tag("T-006")
    @ParameterizedTest(name = "T-005/T-006: warmed {0}, {1}, {2} identity")
    @MethodSource("identities")
    void missingIdentityDoesNotBorrowWarmedCredentials(String client, String style, String kind) {
        String warm = id("T-006-warm");
        success(invoke(client, style, callerA, warm), warm);
        sentToken(warm);
        int before = grants().size();
        String operation = id("T-006");
        JsonObject outcome = identity(client, kind, style, null, operation, false, false);
        failureBeforeSoap(outcome, operation);
        assertThat(grants()).hasSize(before);
        assertThat(outcome.getString("detail")).doesNotContain(callerA, callerB);
    }

    @Tag("T-006")
    @ParameterizedTest(name = "T-006: anonymous HTTP request reaches {0}, {1}")
    @MethodSource("callerDependentStyles")
    void anonymousRequest(String client, String style) {
        String warm = id("T-006-warm-http");
        success(invoke(client, style, callerA, warm), warm);
        String operation = id("T-006-anonymous");
        failureBeforeSoap(invoke(client, style, null, operation), operation);
    }

    static Stream<Arguments> callerDependentStyles() {
        return Stream.of("propagate", "exchangeNamed")
                .flatMap(client -> Stream.of("sync", "async").map(style -> Arguments.of(client, style)));
    }
}
