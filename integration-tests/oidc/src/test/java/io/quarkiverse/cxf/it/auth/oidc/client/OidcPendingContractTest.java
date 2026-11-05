package io.quarkiverse.cxf.it.auth.oidc.client;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Pending parts of the plan are visible in test reports instead of being presented as passing coverage. */
public class OidcPendingContractTest {
    @Test
    @Tag("T-009")
    @Disabled("Q-4: specify rejection phase or automatic conduit selection for an explicitly unsupported conduit")
    void unsupportedConduit() {
        fail("Add a separate unsupported-conduit configuration after Q-4 defines its expected outcome");
    }

    @Test
    @Tag("T-012")
    @Disabled("Q-5: no configuration/API is specified to vary only an additional grant parameter on the same OIDC client")
    void exchangeCacheSeparatesOnlyTheGrantParameter() {
        fail("Keep the subject token and selected OIDC client fixed, vary only audience/scope, and inspect issued tokens");
    }

    @Test
    @Tag("T-016")
    @Disabled("The implementation does not yet expose its origin decision for a scheme-only comparison at an identical port")
    void schemeOnlyOriginChange() {
        fail("Exercise the implementation's actual origin decision; do not duplicate that decision in a test helper");
    }

    @Test
    @Tag("T-024")
    @Disabled("Q-8: no refresh-on-unauthorized option or supported modes have been specified; T-015 covers mandatory no-replay")
    void refreshOnUnauthorizedOnlyAffectsALaterInvocation() {
        fail("Enable the confirmed policy and assert that only a separately submitted operation uses a replacement token");
    }

    @Test
    @Tag("T-025")
    @Disabled("Documentation build and semantic review of exchange-cache/revocation prose require the completed implementation")
    void renderedDocumentationUsesTheExecutableExamples() {
        fail("Build Antora after implementation and review all mode examples and exchange revocation limitations");
    }
}
