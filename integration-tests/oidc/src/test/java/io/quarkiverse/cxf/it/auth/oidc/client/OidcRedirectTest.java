package io.quarkiverse.cxf.it.auth.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "redirects"))
public class OidcRedirectTest extends OidcTestSupport {
    static Stream<Arguments> redirects() {
        return Stream.of("propagate", "exchange").flatMap(mode -> Stream.of("sync", "async")
                .flatMap(style -> Stream.of("same", "host", "port", "https", "http")
                        .map(origin -> Arguments.of(mode, style, origin))));
    }

    static Stream<Arguments> acquireRedirects() {
        return Stream.of("sync", "async")
                .flatMap(style -> Stream.of("same", "host", "port", "https", "http")
                        .map(origin -> Arguments.of(style, origin)));
    }

    @Tag("T-016")
    @Tag("T-017")
    @ParameterizedTest(name = "T-016/T-017: acquire, {0}, redirect to {1}")
    @MethodSource("acquireRedirects")
    void acquireRedirectCredentials(String style, String origin) {
        assertRedirectCredentials("acquire", style, origin);
    }

    @Tag("T-016")
    @Tag("T-017")
    @ParameterizedTest(name = "T-016/T-017: {0}, {1}, redirect to {2}")
    @MethodSource("redirects")
    void redirectCredentials(String mode, String style, String origin) {
        assertRedirectCredentials(mode, style, origin);
    }

    private void assertRedirectCredentials(String mode, String style, String origin) {
        String client = "http".equals(origin) ? mode + "Tls"
                : "propagate".equals(mode) ? "propagate" : mode + "Named";
        String operation = id("T-017");
        control("soap", new JsonObject().put("id", operation).put("redirect", origin));
        JsonObject outcome = invoke(client, style, callerA, operation);
        List<JsonObject> requests = posts(operation);
        assertThat(requests).as("Both redirect source and destination must actually be reached").hasSize(2);
        assertThat(requests.get(0).getString("path")).startsWith("/soap/");
        assertThat(requests.get(1).getString("path")).isEqualTo("/destination/" + operation);
        List<String> source = requests.get(0).getJsonArray("authorization").stream().map(Object::toString).toList();
        assertThat(source).hasSize(1);
        assertThat(source.get(0)).startsWith("Bearer ");
        String token = source.get(0).substring("Bearer ".length());
        if ("propagate".equals(mode)) {
            assertThat(token).isEqualTo(callerA);
        } else {
            JsonObject grant = grantFor(token);
            assertThat(grant.getString("client")).isEqualTo("acquire".equals(mode) ? "named" : "exchange");
            if ("exchange".equals(mode)) {
                assertThat(grant.getJsonObject("parameters").getString("subject_token")).isEqualTo(callerA);
            }
        }
        if ("same".equals(origin)) {
            success(outcome, operation);
            assertThat(requests.get(1).getJsonArray("authorization")).isEqualTo(requests.get(0).getJsonArray("authorization"));
        } else {
            assertThat(requests.get(1).getJsonArray("authorization").isEmpty()).isTrue();
        }
    }

    @Tag("T-017")
    @ParameterizedTest(name = "T-017: acquire, {0}, redirects through {1} to a different port")
    @CsvSource({ "sync, same", "async, same", "sync, port", "async, port" })
    void acquireRedirectChain(String style, String firstOrigin) {
        String operation = id("T-017-chain");
        control("soap", new JsonObject().put("id", operation)
                .put("redirectChain", new JsonArray(List.of(firstOrigin, "port"))));
        success(invoke("acquireNamed", style, null, operation), operation);

        List<JsonObject> requests = posts(operation);
        assertThat(requests).hasSize(3);
        JsonArray authorization = requests.get(0).getJsonArray("authorization");
        assertThat(authorization.size()).isEqualTo(1);
        assertThat(authorization.getString(0)).startsWith("Bearer ");
        assertThat(grantFor(authorization.getString(0).substring("Bearer ".length())).getString("client"))
                .isEqualTo("named");
        if ("same".equals(firstOrigin)) {
            assertThat(requests.get(1).getJsonArray("authorization")).isEqualTo(authorization);
        } else {
            assertThat(requests.get(1).getJsonArray("authorization").isEmpty()).isTrue();
        }
        assertThat(requests.get(2).getJsonArray("authorization").isEmpty()).isTrue();
    }
}
