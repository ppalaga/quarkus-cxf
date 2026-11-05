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
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "wsdl"))
public class OidcWsdlTest extends OidcTestSupport {
    static Stream<Arguments> publicWsdls() {
        return Stream.of("Acquire", "Propagate", "Exchange")
                .flatMap(mode -> Stream.of("Omitted", "False").map(gate -> Arguments.of(mode, gate)));
    }

    @Tag("T-019")
    @ParameterizedTest(name = "T-019: {0} WSDL with secure-wsdl-access={1}")
    @MethodSource("publicWsdls")
    void publicWsdl(String mode, String gate) {
        String client = "wsdl" + mode + gate;
        control("document", new JsonObject().put("client", client));
        int before = state().getJsonArray("documents").size();
        JsonObject creation = fresh(client, null, id("T-019-anonymous-create"), true);
        assertThat(creation.getBoolean("success")).as(creation.getString("detail")).isTrue();
        List<JsonObject> creationGets = documentsSince(this, before);
        assertThat(creationGets).isNotEmpty()
                .allSatisfy(request -> assertThat(request.getJsonArray("authorization").isEmpty()).isTrue());
        before = state().getJsonArray("documents").size();
        String operation = id("T-019");
        success(fresh(client, callerA, operation, false), operation);
        assertThat(documentsSince(this, before)).isNotEmpty()
                .allSatisfy(request -> assertThat(request.getJsonArray("authorization").isEmpty()).isTrue());
        if ("Propagate".equals(mode)) {
            assertThat(sentToken(operation)).isEqualTo(callerA);
        } else if ("Exchange".equals(mode)) {
            assertExchange(operation, callerA, "exchange");
        } else {
            assertThat(grantFor(sentToken(operation)).getString("client")).isEqualTo("named");
        }
    }

    @Tag("T-020")
    @ParameterizedTest(name = "T-020: protected WSDL for {0}")
    @CsvSource({ "wsdlAcquireSecure, named", "wsdlPropagateSecure, propagate", "wsdlExchangeSecure, exchange",
            "wsdlDefaultSecure, default" })
    void protectedWsdl(String client, String selected) {
        verifyProtectedWsdl(this, client, selected, "wsdlExchangeSecure".equals(client));
    }

    static void verifyProtectedWsdl(OidcTestSupport test, String client, String selected, boolean exchange) {
        test.control("document", new JsonObject().put("client", client).put("protected", true));
        int before = test.state().getJsonArray("documents").size();
        String operation = id("T-020");
        test.success(test.fresh(client, test.callerA, operation, false), operation);
        List<JsonObject> documents = documentsSince(test, before);
        assertThat(documents).isNotEmpty();
        for (JsonObject document : documents) {
            List<String> headers = document.getJsonArray("authorization").stream().map(Object::toString).toList();
            assertThat(headers).hasSize(1);
            assertThat(headers.get(0)).startsWith("Bearer ");
            String token = headers.get(0).substring("Bearer ".length());
            if ("propagate".equals(selected)) {
                assertThat(token).isEqualTo(test.callerA);
            } else {
                JsonObject grant = test.grantFor(token);
                assertThat(grant.getString("client")).isEqualTo(selected);
                if (exchange) {
                    assertThat(grant.getJsonObject("parameters").getString("subject_token")).isEqualTo(test.callerA);
                }
            }
        }
        if ("propagate".equals(selected)) {
            assertThat(test.sentToken(operation)).isEqualTo(test.callerA);
        } else if (exchange) {
            test.assertExchange(operation, test.callerA, selected);
        } else {
            assertThat(test.grantFor(test.sentToken(operation)).getString("client")).isEqualTo(selected);
        }
    }

    static Stream<Arguments> missingWsdlCredentials() {
        return Stream.of("wsdlPropagateSecure", "wsdlExchangeSecure")
                .flatMap(client -> Stream.of("anonymous", "absent", "ambiguous", "contextless")
                        .map(kind -> Arguments.of(client, kind)));
    }

    @Tag("T-021")
    @ParameterizedTest(name = "T-021: {0} creation with {1} credentials")
    @MethodSource("missingWsdlCredentials")
    void missingWsdlCredential(String client, String kind) {
        control("document", new JsonObject().put("client", client).put("protected", true));
        int before = state().getJsonArray("soap").size();
        String operation = id("T-021");
        // Creation only: a failure in a later SOAP operation must not satisfy this test.
        JsonObject outcome = identity(client, kind, "sync", null, operation, true, true);
        failureBeforeSoap(outcome, operation);
        assertThat(state().getJsonArray("soap").size()).isEqualTo(before);
        // Q-7 does not specify whether an unauthenticated root GET may have been attempted.
    }

    static List<JsonObject> documentsSince(OidcTestSupport test, int offset) {
        List<JsonObject> all = objects(test.state().getJsonArray("documents"));
        return all.subList(offset, all.size());
    }
}
