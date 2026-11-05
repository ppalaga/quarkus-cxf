package io.quarkiverse.cxf.it.auth.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@QuarkusTestResource(value = OidcTestResource.class, restrictToAnnotatedClass = true, initArgs = @ResourceArg(name = "suite", value = "isolation"))
public class OidcIsolationTest extends OidcTestSupport {
    @Test
    @Tag("T-012")
    void exchangeCacheSeparatesSubjectTokensAndClients() {
        String initial = id("T-012-initial");
        success(invoke("exchangeNamed", "sync", callerA, initial), initial);
        String originalToken = sentToken(initial);
        String repeat = id("T-012-repeat");
        success(invoke("exchangeNamed", "async", callerA, repeat), repeat);
        assertThat(sentToken(repeat)).isEqualTo(originalToken);
        // A replacement token for the same subject must not hit A's previous cache entry.
        for (String subject : List.of(callerB, replacementA)) {
            String operation = id("T-012-subject");
            success(invoke("exchangeNamed", "sync", subject, operation), operation);
            assertExchange(operation, subject, "exchange");
            assertThat(sentToken(operation)).isNotEqualTo(originalToken);
        }
        String otherClient = id("T-012-client");
        success(invoke("exchangeOther", "sync", callerA, otherClient), otherClient);
        assertExchange(otherClient, callerA, "exchange-other");
        assertThat(sentToken(otherClient)).isNotEqualTo(originalToken);
        String audience = id("T-012-configured-audience");
        success(invoke("exchangeAudience", "sync", callerA, audience), audience);
        assertExchange(audience, callerA, "exchange-audience");
        assertThat(grantFor(originalToken).getJsonObject("parameters").getString("audience")).isEqualTo("soap-primary");
        assertThat(grantFor(sentToken(audience)).getJsonObject("parameters").getString("audience"))
                .isEqualTo("soap-secondary");
        assertThat(sentToken(audience)).isNotEqualTo(originalToken);
        // Changing only an invocation's grant parameter, with the same OIDC client, remains Q-5 (see pending tests).
    }

    static Stream<Arguments> overlappingCalls() {
        return Stream.of("propagate", "concurrentExchange").flatMap(client -> Stream.of("rest", "soap")
                .flatMap(ingress -> Stream.of("sync", "async").map(style -> Arguments.of(client, ingress, style))));
    }

    @Tag("T-013")
    @ParameterizedTest(name = "T-013: concurrent {0}, {1} ingress, {2}")
    @MethodSource("overlappingCalls")
    void concurrentCallers(String client, String ingress, String style) throws Exception {
        overlap(client, ingress, style, "T-013", true);
        // Same shared proxy and same tokens, now exercising warmed exchange caches and worker reuse.
        overlap(client, ingress, style, "T-013-warm", false);
    }

    private void overlap(String client, String ingress, String style, String scenario, boolean cold) throws Exception {
        boolean exchange = !"propagate".equals(client);
        String selected = "asyncExchange".equals(client) ? "async-exchange" : "concurrent-exchange";
        String a = id(scenario + "-A");
        String b = id(scenario + "-B");
        control("soap", new JsonObject().put("id", a).put("hold", true));
        control("soap", new JsonObject().put("id", b).put("hold", true));
        if (exchange) {
            control("token", new JsonObject().put("client", selected).put("hold", cold));
        }
        String pendingA = "token:" + selected + ":" + callerA;
        String pendingB = "token:" + selected + ":" + callerB;
        CompletableFuture<JsonObject> resultA = CompletableFuture
                .supplyAsync(() -> ingress(ingress, client, style, callerA, a));
        CompletableFuture<JsonObject> resultB = null;
        try {
            awaitPending(exchange && cold ? pendingA : "soap:" + a);
            resultB = CompletableFuture.supplyAsync(() -> ingress(ingress, client, style, callerB, b));
            awaitPending(exchange && cold ? pendingB : "soap:" + b);
            if (exchange && cold) {
                // Complete B's token request first while A still has a pending grant.
                release(pendingB);
                awaitPending("soap:" + b);
                release(pendingA);
                awaitPending("soap:" + a);
            }
            String missing = id(scenario + "-missing");
            JsonObject failure = invoke(client, style, null, missing);
            failureBeforeSoap(failure, missing);
            if ("event-loop".equals(style)) {
                assertThat(failure.getBoolean("eventLoop")).isTrue();
            }
            release("soap:" + b);
            JsonObject completedB = resultB.get(20, TimeUnit.SECONDS);
            success(completedB, b);
            assertThat(resultA.isDone()).as("A must still be independently held by the SOAP fixture").isFalse();
            release("soap:" + a);
            JsonObject completedA = resultA.get(20, TimeUnit.SECONDS);
            success(completedA, a);
            if ("event-loop".equals(style)) {
                assertThat(completedA.getBoolean("eventLoop")).isTrue();
                assertThat(completedB.getBoolean("eventLoop")).isTrue();
            }
            if (exchange) {
                assertExchange(a, callerA, selected);
                assertExchange(b, callerB, selected);
            } else {
                assertThat(sentToken(a)).isEqualTo(callerA);
                assertThat(sentToken(b)).isEqualTo(callerB);
            }
        } finally {
            release(pendingA);
            release(pendingB);
            release("soap:" + a);
            release("soap:" + b);
            resultA.cancel(true);
            if (resultB != null) {
                resultB.cancel(true);
            }
        }
    }

    @Tag("T-023")
    @ParameterizedTest(name = "T-023: {0} preserves identity across event-loop/worker dispatch")
    @ValueSource(strings = { "propagate", "asyncExchange" })
    void eventLoopIdentity(String client) throws Exception {
        overlap(client, "rest", "event-loop", "T-023", true);
        overlap(client, "rest", "event-loop", "T-023-warm", false);
    }

    @Test
    @Tag("T-023")
    void eventLoopAcquisitionCompletesAsynchronously() throws Exception {
        control("token", new JsonObject().put("client", "async-acquire").put("hold", true));
        String a = id("T-023-acquire-A");
        String b = id("T-023-acquire-B");
        control("soap", new JsonObject().put("id", a).put("hold", true));
        control("soap", new JsonObject().put("id", b).put("hold", true));
        CompletableFuture<JsonObject> resultA = CompletableFuture
                .supplyAsync(() -> invoke("asyncAcquire", "event-loop", callerA, a));
        CompletableFuture<JsonObject> resultB = null;
        try {
            awaitPending("token:async-acquire:");
            resultB = CompletableFuture.supplyAsync(() -> invoke("asyncAcquire", "event-loop", callerB, b));
            // Later grants are allowed to finish too; no concurrent grant-coalescing guarantee is assumed.
            control("token", new JsonObject().put("client", "async-acquire"));
            release("token:async-acquire:");
            awaitPending("soap:" + a);
            awaitPending("soap:" + b);
            release("soap:" + b);
            JsonObject completedB = resultB.get(20, TimeUnit.SECONDS);
            success(completedB, b);
            release("soap:" + a);
            JsonObject completedA = resultA.get(20, TimeUnit.SECONDS);
            success(completedA, a);
            assertThat(completedA.getBoolean("eventLoop")).isTrue();
            assertThat(completedB.getBoolean("eventLoop")).isTrue();
            assertThat(grantFor(sentToken(a)).getString("client")).isEqualTo("async-acquire");
            assertThat(grantFor(sentToken(b)).getString("client")).isEqualTo("async-acquire");
        } finally {
            release("token:async-acquire:");
            release("soap:" + a);
            release("soap:" + b);
            resultA.cancel(true);
            if (resultB != null) {
                resultB.cancel(true);
            }
        }
    }

    @Tag("T-023")
    @ParameterizedTest(name = "T-023: event-loop token failure in {0}")
    @ValueSource(strings = { "failureAcquire", "failureExchange" })
    void eventLoopTokenFailure(String client) {
        String selected = "failureAcquire".equals(client) ? "failure-acquire" : "failure-exchange";
        control("token", new JsonObject().put("client", selected).put("error", "oauth"));
        String operation = id("T-023-failure");
        JsonObject outcome = invoke(client, "event-loop", callerA, operation);
        failureBeforeSoap(outcome, operation);
        assertThat(outcome.getBoolean("eventLoop")).isTrue();
    }
}
