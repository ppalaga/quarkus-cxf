package io.quarkiverse.cxf.it.auth.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "configuration"))
public class OidcConfigurationTest extends OidcTestSupport {
    static Stream<Arguments> conflicts() {
        return Stream.of("Acquire", "Propagate", "Exchange")
                .flatMap(mode -> Stream.of("Basic", "Digest").map(scheme -> Arguments.of(mode, scheme)));
    }

    @Tag("T-008")
    @ParameterizedTest(name = "T-008: {0} conflicts with {1} at client creation")
    @MethodSource("conflicts")
    void authenticationConflict(String mode, String scheme) {
        int before = state().getJsonArray("soap").size();
        JsonObject result = fresh("conflict" + mode + scheme, callerA, id("T-008"), true);
        assertThat(result.getBoolean("success")).isFalse();
        assertThat(result.getString("detail")).isNotBlank();
        assertThat(state().getJsonArray("soap").size()).isEqualTo(before);
    }

    @Tag("T-007")
    @ParameterizedTest(name = "T-007: unknown OIDC client must not fall back to the default, {0}")
    @ValueSource(strings = { "sync", "async" })
    void unknownClient(String style) {
        // Initialize through a named client first; cold default-client initialization has separate acquisition coverage.
        String namedOperation = id("T-007-named");
        success(invoke("acquireNamed", style, null, namedOperation), namedOperation);
        String defaultOperation = id("T-007-default");
        success(invoke("acquireDefault", style, null, defaultOperation), defaultOperation);
        assertThat(grantFor(sentToken(defaultOperation)).getString("client")).isEqualTo("default");

        int grantsBefore = grants().size();
        String operation = id("T-007-unknown");
        JsonObject result = invoke("unknownAcquire", style, null, operation);
        failureBeforeSoap(result, operation);
        assertThat(result.getString("detail")).contains("No OIDC client named 'missing-client' is configured");
        assertThat(grants()).hasSize(grantsBefore);
    }

    @Tag("T-007")
    @Disabled("Q-1: confirm the incompatible grant pairs before fixing these creation-time expectations")
    @ParameterizedTest(name = "T-007: incompatible grant for {0}")
    @ValueSource(strings = { "incompatibleAcquire", "incompatibleExchange" })
    void incompatibleGrant(String client) {
        int before = state().getJsonArray("soap").size();
        JsonObject result = fresh(client, callerA, id("T-007"), true);
        assertThat(result.getBoolean("success")).isFalse();
        assertThat(state().getJsonArray("soap").size()).isEqualTo(before);
    }
}
