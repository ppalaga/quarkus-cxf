package io.quarkiverse.cxf.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.oidc.client.runtime.OidcClientConfig;
import io.quarkus.oidc.client.runtime.OidcClientsConfig;
import io.quarkus.oidc.common.OidcRequestContextProperties;
import io.quarkus.oidc.common.OidcResponseFilter.OidcResponseFilterContext;
import io.quarkus.runtime.ShutdownContext;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;

public class OidcRedactionTest {
    @Test
    @Tag("T-018")
    void redactBeforeQuarkusReadsOrLogsAFailedResponse() {
        OidcEndpointFilter filter = new OidcEndpointFilter();
        filter.config = () -> Map.of("soap", () -> new CxfOidcConfig.Oidc() {
            public CxfOidcConfig.Mode mode() {
                return CxfOidcConfig.Mode.EXCHANGE;
            }

            public Optional<String> clientName() {
                return Optional.of("exchange");
            }
        });
        filter.clients = () -> Map.of(OidcClientsConfig.DEFAULT_CLIENT_KEY, OidcClientConfig.builder().id("Default").build(),
                "exchange", OidcClientConfig.builder().id("custom-exchange-id").build());
        var response = response("custom-exchange-id", 400, "{\"error_description\":\"subject-secret\"}");
        filter.filter(response).await().indefinitely();
        assertThat(response.requestProperties().get(OidcRequestContextProperties.RESPONSE_BODY, Buffer.class).toString())
                .doesNotContain("subject-secret").contains("failed");
        var success = response("custom-exchange-id", 200, "{\"access_token\":\"access-secret\"}");
        filter.filter(success).await().indefinitely();
        assertThat(success.responseBody().toString()).contains("access-secret");
        for (String invalid : List.of("{broken", "{}", "{\"access_token\":\"\"}",
                "{\"access_token\":\"secret\\r\\nheader\"}")) {
            assertThatThrownBy(() -> filter.filter(response("custom-exchange-id", 200, invalid)).await().indefinitely())
                    .hasMessage("SOAP OIDC token response is invalid").hasNoCause();
        }
        var unrelated = response("unrelated", 400, "{\"error\":\"unrelated-client-error\"}");
        filter.filter(unrelated).await().indefinitely();
        assertThat(unrelated.responseBody().toString()).contains("unrelated-client-error");
    }

    private static OidcResponseFilterContext response(String client, int status, String body) {
        return new OidcResponseFilterContext(new OidcRequestContextProperties(Map.of("client-id", client)), status,
                MultiMap.caseInsensitiveMultiMap(), Buffer.buffer(body));
    }

    @Test
    @Tag("T-018")
    void redactSubjectTokensFromDependencyDebugLogsAndRestoreExistingFilter() {
        Logger logger = Logger.getLogger("io.quarkus.oidc.client.runtime.OidcClientImpl");
        Filter original = logger.getFilter();
        Level originalLevel = logger.getLevel();
        AtomicInteger filtered = new AtomicInteger();
        Filter previous = record -> {
            filtered.incrementAndGet();
            return true;
        };
        List<Runnable> cleanup = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            public void flush() {
            }

            public void close() {
            }
        };
        try {
            logger.setFilter(previous);
            logger.setLevel(Level.FINE);
            logger.addHandler(handler);
            new OidcRecorder().protectTokenRequestLogs(new ShutdownContext() {
                public void addShutdownTask(Runnable task) {
                    cleanup.add(task);
                }

                public void addLastShutdownTask(Runnable task) {
                    cleanup.add(task);
                }
            });
            logger.fine("Get token: url : http://provider/token, headers: [], request params: subject_token=subject-secret");
            assertThat(messages).hasSize(1).allSatisfy(message -> assertThat(message).doesNotContain("subject-secret"));
            assertThat(filtered.get()).isEqualTo(1);
            cleanup.forEach(Runnable::run);
            assertThat(logger.getFilter()).isSameAs(previous);
        } finally {
            logger.removeHandler(handler);
            logger.setFilter(original);
            logger.setLevel(originalLevel);
        }
    }
}
