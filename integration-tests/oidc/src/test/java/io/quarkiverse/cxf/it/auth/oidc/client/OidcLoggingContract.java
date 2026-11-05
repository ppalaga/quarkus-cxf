package io.quarkiverse.cxf.it.auth.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.TestLog;
import io.vertx.core.json.JsonObject;

public abstract class OidcLoggingContract extends OidcTestSupport {
    @Test
    @Tag("T-018")
    void tokensAreMaskedInMessagesAndErrors(TestLog testLog) throws Exception {
        Path logFile = testLog.getLogFilePath();
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> Files.isRegularFile(logFile));
        int offset = Files.readString(logFile).length();
        List<String> operations = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (String client : List.of("acquireNamed", "propagate", "exchangeNamed")) {
            String operation = id("T-018-success");
            success(invoke(client, "sync", callerA, operation), operation);
            operations.add(operation);
            String unauthorized = id("T-018-401");
            control("soap", new JsonObject().put("id", unauthorized).put("status", 401).put("challenge", true));
            JsonObject failure = invoke(client, "async", callerA, unauthorized);
            assertThat(failure.getBoolean("success")).isFalse();
            assertThat(posts(unauthorized)).hasSize(1);
            diagnostics.add(failure.getString("detail"));
            operations.add(unauthorized);
            String redirect = id("T-018-redirect");
            control("soap", new JsonObject().put("id", redirect).put("redirect", "same"));
            success(invoke(client, "sync", callerA, redirect), redirect);
            operations.add(redirect);
        }
        for (String mode : List.of("Acquire", "Propagate", "Exchange")) {
            String client = "wsdl" + mode + "Secure";
            control("document", new JsonObject().put("client", client).put("protected", true));
            String operation = id("T-018-wsdl");
            success(fresh(client, callerA, operation, false), operation);
            operations.add(operation);
        }
        control("token", new JsonObject().put("client", "refresh").put("expiresIn", 5).put("refresh", true));
        String refresh = id("T-018-refresh");
        success(invoke("refresh", "sync", null, refresh), refresh);
        OidcTokenLifecycleTest.expired(grantFor(sentToken(refresh)));
        String refreshed = id("T-018-refreshed");
        success(invoke("refresh", "sync", null, refreshed), refreshed);
        operations.add(refreshed);

        // Include a provider error that itself echoes a secret, exercising the diagnostic/cause-chain path.
        control("token", new JsonObject().put("client", "failure-exchange").put("error", "oauth").put("secret", callerA));
        String failedGrant = id("T-018-provider-error");
        JsonObject failed = invoke("failureExchange", "sync", callerA, failedGrant);
        failureBeforeSoap(failed, failedGrant);
        diagnostics.add(failed.getString("detail"));

        List<String> secrets = new ArrayList<>(List.of(callerA, callerB, replacementA));
        for (JsonObject grant : grants()) {
            for (String property : List.of("accessToken", "refreshToken")) {
                String value = grant.getString(property);
                if (value != null) {
                    secrets.add(value);
                }
            }
        }
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            String messages = Files.readString(logFile).substring(offset);
            assertThat(messages).as("Prove that CXF message logging was active").contains("REQ_OUT", "Authorization");
            for (String operation : operations) {
                assertThat(messages).contains(operation);
            }
            for (String secret : secrets) {
                assertThat(messages).doesNotContain(secret);
            }
        });
        for (String diagnostic : diagnostics) {
            for (String secret : secrets) {
                assertThat(diagnostic).doesNotContain(secret);
            }
        }
    }
}
