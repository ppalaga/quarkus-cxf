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
import io.vertx.core.json.JsonObject;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "wsdl-origins"))
public class OidcWsdlOriginTest extends OidcTestSupport {
    static Stream<Arguments> origins() {
        return Stream.of("Propagate", "Exchange").flatMap(mode -> Stream.of("redirect", "import")
                .flatMap(kind -> Stream.of("host", "port", "https", "http")
                        .map(origin -> Arguments.of(mode, kind, origin))));
    }

    static Stream<Arguments> acquireOrigins() {
        return Stream.of("redirect", "import")
                .flatMap(kind -> Stream.of("same", "host", "port", "https", "http")
                        .map(origin -> Arguments.of(kind, origin)));
    }

    @Tag("T-022")
    @ParameterizedTest(name = "T-022: Acquire, WSDL {0}, origin {1}")
    @MethodSource("acquireOrigins")
    void acquireDocumentCredentials(String kind, String origin) {
        differentOriginDoesNotReceiveCredentials("Acquire", kind, origin);
    }

    @Tag("T-022")
    @ParameterizedTest(name = "T-022: Acquire, WSDL redirect and imports on another {0}")
    @CsvSource({ "host", "port" })
    void redirectedWsdlImportsDoNotReceiveCredentials(String origin) {
        verifyDocumentCredentials("wsdlAcquireSecure", new JsonObject().put("redirect", origin).put("import", origin),
                false, List.of("/redirected.wsdl", "/import.wsdl", "/import.xsd", "/nested.wsdl", "/nested.xsd"));
    }

    @Tag("T-022")
    @ParameterizedTest(name = "T-022: {0}, WSDL {1}, different origin {2}")
    @MethodSource("origins")
    void differentOriginDoesNotReceiveCredentials(String mode, String kind, String origin) {
        String client = "wsdl" + mode + ("http".equals(origin) ? "Tls" : "Secure");
        List<String> expectedFiles = "import".equals(kind)
                ? List.of("/import.wsdl", "/import.xsd", "/nested.wsdl", "/nested.xsd")
                : List.of("/redirected.wsdl");
        verifyDocumentCredentials(client, new JsonObject().put(kind, origin), "same".equals(origin), expectedFiles);
    }

    private void verifyDocumentCredentials(String client, JsonObject rule, boolean sameOrigin, List<String> expectedFiles) {
        control("document", rule.put("client", client).put("protected", true));
        int before = state().getJsonArray("documents").size();
        JsonObject outcome = fresh(client, callerA, id("T-022"), true);
        assertThat(outcome.getBoolean("success")).as(outcome.getString("detail")).isTrue();
        List<JsonObject> documents = OidcWsdlTest.documentsSince(this, before);
        List<JsonObject> roots = documents.stream().filter(document -> document.getString("path").endsWith("/root.wsdl"))
                .toList();
        assertThat(roots).isNotEmpty()
                .allSatisfy(document -> assertThat(document.getJsonArray("authorization").size()).isEqualTo(1));
        for (String file : expectedFiles) {
            List<JsonObject> targets = documents.stream().filter(document -> document.getString("path").endsWith(file))
                    .toList();
            assertThat(targets).as("The public target %s must actually be fetched", file).isNotEmpty()
                    .allSatisfy(document -> {
                        if (sameOrigin) {
                            assertThat(document.getJsonArray("authorization"))
                                    .isEqualTo(roots.get(0).getJsonArray("authorization"));
                        } else {
                            assertThat(document.getJsonArray("authorization").isEmpty()).isTrue();
                        }
                    });
        }
    }
}
