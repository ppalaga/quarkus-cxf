package io.quarkiverse.cxf.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class OidcOriginTest {
    @Tag("T-016")
    @ParameterizedTest
    @CsvSource({
            "http://localhost:8080/a, http://localhost:8080/b, true",
            "http://localhost:8080/a, http://other:8080/a, false",
            "http://localhost:8080/a, http://localhost:8081/a, false",
            "http://localhost:8080/a, https://localhost:8080/a, false",
            "https://localhost:8080/a, http://localhost:8080/a, false",
            "http://localhost/a, http://LOCALHOST:80/b, true",
            "https://localhost/a, https://localhost:443/b, true"
    })
    void originIncludesSchemeHostAndEffectivePort(String source, String target, boolean expected) {
        assertThat(OidcRedirectOrigin.sameOrigin(URI.create(source), URI.create(target))).isEqualTo(expected);
    }
}
