package io.quarkiverse.cxf.vertx.http.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RedirectOriginTest {
    @ParameterizedTest
    @CsvSource({
            "http://example.com/start, http://example.com/next?query=1, true",
            "http://example.com/start, http://example.com:80/next, true",
            "http://example.com:80/start, http://example.com/next, true",
            "https://example.com/start, https://example.com:443/next, true",
            "https://example.com:443/start, https://example.com/next, true",
            "HTTP://EXAMPLE.COM/start, http://example.com/next, true",
            "http://example.com:8080/start, http://example.com:8080/next, true",
            "http://example.com/start, http://other.example.com/next, false",
            "http://example.com:8080/start, http://example.com:8081/next, false",
            "http://example.com/start, https://example.com/next, false",
            "https://example.com/start, http://example.com/next, false",
            "http://example.com:8080/start, https://example.com:8080/next, false",
            "https://example.com:8080/start, http://example.com:8080/next, false"
    })
    void compareOrigins(String origin, String target, boolean sameOrigin) {
        assertThat(VertxHttpClientHTTPConduit.AuthorizationContext.isSameOrigin(URI.create(origin), URI.create(target)))
                .isEqualTo(sameOrigin);
    }
}
