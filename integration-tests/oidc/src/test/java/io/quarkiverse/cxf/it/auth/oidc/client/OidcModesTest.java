package io.quarkiverse.cxf.it.auth.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "modes"))
public class OidcModesTest extends OidcTestSupport {
    @Tag("T-001")
    @DisplayName("T-001: omitted and disabled OIDC preserve anonymous, Basic, and static Bearer clients")
    @ParameterizedTest
    @CsvSource({ "omittedAnonymous, none", "disabledAnonymous, none", "omittedBasic, basic", "disabledBasic, basic",
            "omittedBearer, bearer", "disabledBearer, bearer" })
    void disabled(String client, String authentication) {
        int before = grants().size();
        String operation = id("T-001");
        success(invoke(client, "sync", null, operation), operation);
        assertThat(grants()).hasSize(before);
        assertThat(posts(operation)).hasSize(1);
        List<String> headers = posts(operation).get(0).getJsonArray("authorization").stream().map(Object::toString).toList();
        switch (authentication) {
            case "none" -> assertThat(headers).isEmpty();
            case "basic" -> assertThat(headers).containsExactly("Basic " + Base64.getEncoder()
                    .encodeToString("user:password".getBytes(StandardCharsets.UTF_8)));
            case "bearer" -> assertThat(headers).containsExactly("Bearer static-bearer-T001");
            default -> throw new IllegalArgumentException(authentication);
        }
        String active = id("T-001-active");
        success(invoke("acquireNamed", "sync", null, active), active);
        assertThat(grantFor(sentToken(active)).getString("client")).isEqualTo("named");
    }

    @Tag("T-002")
    @ParameterizedTest(name = "T-002: {0}, {1}")
    @CsvSource({ "acquireDefault, sync, default", "acquireDefault, async, default",
            "acquireNamed, sync, named", "acquireNamed, async, named" })
    void acquisition(String client, String style, String selectedClient) {
        String operation = id("T-002");
        success(invoke(client, style, null, operation), operation);
        JsonObject grant = grantFor(sentToken(operation));
        assertThat(grant.getString("client")).isEqualTo(selectedClient);
        assertThat(grant.getBoolean("authenticated")).isTrue();
        assertThat(grant.getJsonObject("parameters").getString("grant_type")).isEqualTo("client_credentials");
        assertThat(grant.getJsonObject("parameters").getString("subject_token")).isNull();
    }

    @Tag("T-003")
    @ParameterizedTest(name = "T-003: {0} ingress, {1}")
    @MethodSource("ingressAndStyles")
    void propagation(String ingress, String style) {
        int before = grants().size();
        for (String caller : List.of(callerA, callerB, replacementA)) {
            String operation = id("T-003");
            success(ingress(ingress, "propagate", style, caller, operation), operation);
            assertThat(sentToken(operation)).isEqualTo(caller);
        }
        assertThat(grants()).hasSize(before);
    }

    @Tag("T-004")
    @ParameterizedTest(name = "T-004: named exchange through {0}, {1}")
    @MethodSource("ingressAndStyles")
    void exchange(String ingress, String style) {
        String operation = id("T-004");
        success(ingress(ingress, "exchangeNamed", style, callerA, operation), operation);
        assertExchange(operation, callerA, "exchange");
    }

    @Tag("T-009")
    @ParameterizedTest(name = "T-009: explicit Vert.x conduit for {0}, {1}")
    @MethodSource("modesAndStyles")
    void supportedConduit(String client, String style) {
        // application.properties deliberately sets the GLOBAL default to URLConnection, including in native builds.
        JsonObject creation = fresh(client, callerA, id("T-009-create"), true);
        assertThat(creation.getBoolean("success")).as(creation.getString("detail")).isTrue();
        assertThat(creation.getString("detail")).endsWith(".VertxHttpClientHTTPConduit");
        String operation = id("T-009");
        success(invoke(client, style, callerA, operation), operation);
        sentToken(operation);
    }
}
