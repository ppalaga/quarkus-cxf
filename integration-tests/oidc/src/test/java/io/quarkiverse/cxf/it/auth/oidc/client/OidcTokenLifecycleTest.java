package io.quarkiverse.cxf.it.auth.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "lifecycle"))
public class OidcTokenLifecycleTest extends OidcTestSupport {
    @Tag("T-010")
    @ParameterizedTest(name = "T-010: reuse and reacquisition with {0}")
    @CsvSource({ "defaultClient, default, client_credentials", "lifecycleNamed, lifecycle, client_credentials",
            "lifecycleExchange, lifecycle-exchange, urn:ietf:params:oauth:grant-type:token-exchange" })
    void reuseAndReacquisition(String client, String selectedClient, String grantType) {
        verifyReuseAndReacquisition(this, client, selectedClient, grantType);
    }

    static void verifyReuseAndReacquisition(OidcTestSupport test, String client, String selectedClient, String grantType) {
        test.control("token", new JsonObject().put("client", selectedClient).put("expiresIn", 5));
        String first = id("T-010-first");
        test.success(test.invoke(client, "sync", test.callerA, first), first);
        String token = test.sentToken(first);
        JsonObject grant = test.grantFor(token);
        assertThat(grant.getString("client")).isEqualTo(selectedClient);
        assertThat(grant.getJsonObject("parameters").getString("grant_type")).isEqualTo(grantType);
        assertThat(grant.getString("refreshToken")).isNull();
        int grantsBeforeReuse = test.grants(selectedClient).size();
        String second = id("T-010-reuse");
        test.success(test.invoke(client, "async", test.callerA, second), second);
        assertThat(test.sentToken(second)).isEqualTo(token);
        assertThat(test.grants(selectedClient)).hasSize(grantsBeforeReuse);
        expired(grant);
        String third = id("T-010-reacquire");
        test.success(test.invoke(client, "sync", test.callerA, third), third);
        String replacement = test.sentToken(third);
        assertThat(replacement).isNotEqualTo(token);
        JsonObject replacementGrant = test.grantFor(replacement);
        assertThat(replacementGrant.getJsonObject("parameters").getString("grant_type")).isEqualTo(grantType);
        assertThat(replacementGrant.getLong("expiresAt")).isGreaterThan(Instant.now().getEpochSecond());
        if (OidcTestResource.EXCHANGE_GRANT.equals(grantType)) {
            test.assertExchange(third, test.callerA, selectedClient);
        }
    }

    @Tag("T-011")
    @ParameterizedTest(name = "T-011: refresh, endpoint failure={0}")
    @ValueSource(booleans = { false, true })
    void refresh(boolean failRefresh) {
        String client = failRefresh ? "refreshFailure" : "refresh";
        String selected = failRefresh ? "refresh-failure" : "refresh";
        control("token", new JsonObject().put("client", selected).put("expiresIn", 5).put("refresh", true));
        String first = id("T-011-first");
        success(invoke(client, "sync", null, first), first);
        String originalToken = sentToken(first);
        JsonObject originalGrant = grantFor(originalToken);
        String refreshToken = originalGrant.getString("refreshToken");
        assertThat(refreshToken).isNotBlank();
        if (failRefresh) {
            // Reject both refresh and any fallback acquisition; no usable alternative may reach SOAP.
            control("token", new JsonObject().put("client", selected).put("error", "oauth"));
        }
        expired(originalGrant);
        String second = id("T-011-refresh");
        JsonObject outcome = invoke(client, "async", null, second);
        if (failRefresh) {
            failureBeforeSoap(outcome, second);
            assertThat(outcome.getString("detail")).doesNotContain(originalToken, refreshToken);
        } else {
            success(outcome, second);
            String replacement = sentToken(second);
            assertThat(replacement).isNotEqualTo(originalToken);
            JsonObject refreshGrant = grantFor(replacement);
            assertThat(refreshGrant.getJsonObject("parameters").getString("grant_type")).isEqualTo("refresh_token");
            assertThat(refreshGrant.getJsonObject("parameters").getString("refresh_token")).isEqualTo(refreshToken);
        }
        assertThat(grants(selected)).anySatisfy(
                grant -> assertThat(grant.getJsonObject("parameters").getString("grant_type")).isEqualTo("refresh_token"));
    }

    static void expired(JsonObject grant) {
        Awaitility.await().atMost(Duration.ofSeconds(20))
                .until(() -> Instant.now().getEpochSecond() > grant.getLong("expiresAt"));
    }
}
