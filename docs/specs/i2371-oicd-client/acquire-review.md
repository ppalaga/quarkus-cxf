# Acquire implementation review

AI-generated review by Codex on behalf of Peter Palaga, 2026-11-08.

Reviewed commit: `0385252c97a8c0a6db0525b69f8a77f7421cd595` (`Initial OIDC acquire implementation`).
Scope: the acquire mode and its shared requirements in [the specification](spec.md).
The propagation/exchange TODOs are outside this assessment.

Follow-up: finding 3 is fixed in the working tree.
Unknown explicit OIDC client names now produce a failed authorization result before token acquisition or SOAP dispatch.
The `OidcConfigurationTest.unknownClient` regression covers sync and async calls with a working default client available.
The findings below describe the reviewed commit before that fix.

## Confirmed findings

1. **P1: Bearer credentials are forwarded to other origins on SOAP redirects — R-15.**
   The new header is stored in the original `RequestOptions`.
   Existing `redirect()` calls `retransmit()` with `Function.identity()`,
   and `retransmit()` copies those options without removing Authorization.
   Host-only and port-only redirects expose the token to the destination in both sync and async tests.
   HTTPS-to-HTTP cases also expose it.
   Keep the token's permitted origin and remove Authorization
   whenever a redirect changes host, effective port, or scheme.
   Location: [VertxHttpClientHTTPConduit.java](/home/agentuser/qcxf/extensions/core/runtime/src/main/java/io/quarkiverse/cxf/vertx/http/client/VertxHttpClientHTTPConduit.java:569).

2. **P1: WSDL authentication ignores the opt-in gate and imported-document origin — R-17/R-19.**
   Remote WSDLs and imports use the same conduit/client key as SOAP operations.
   The unconditional provider lookup cannot distinguish those requests or apply `secure-wsdl-access`.
   The existing tests confirm Bearer headers on WSDL GETs when that option is omitted or false.
   The resolver also reuses this configuration for imports from other origins.
   Pass document-request and root-origin information to the authentication decision;
   skip acquisition when secure WSDL access is disabled and withhold credentials from other origins.
   Location: [VertxHttpClientHTTPConduit.java](/home/agentuser/qcxf/extensions/core/runtime/src/main/java/io/quarkiverse/cxf/vertx/http/client/VertxHttpClientHTTPConduit.java:555).

3. **P1: An unknown explicit OIDC client silently falls back to the default — R-5/R-12.**
   Quarkus `OidcClients.getClient(name)` returns null when the name is absent.
   `Optional.map()` converts that into an empty Optional, and `orElseGet()` then selects the default client.
   A configuration typo can therefore authenticate with another client's credentials instead of failing.
   A standalone probe against the compiled provider reproduced this behavior.
   Select the default only when no name was configured; reject an unresolved explicit name.
   Location: [OidcAuthorizationProvider.java](/home/agentuser/qcxf/extensions/oidc/runtime/src/main/java/io/quarkiverse/cxf/oidc/OidcAuthorizationProvider.java:58).

4. **P1: Default-client acquisition fails until a named client has initialized the provider — R-5/AC-4.**
   On a cold default-client invocation, the `Optional.map()` lambda never runs,
   so the `oidcClients` field remains null.
   Evaluating the bound method reference `oidcClients::getClient` immediately throws `NullPointerException`.
   Both default sync/async acquisition tests fail this way; named acquisition passes.
   A previous named call masks the bug, which explains why a warmed default secure-WSDL test passes.
   Resolve `getOidcClients()` before selection, or use `() -> getOidcClients().getClient()` for the default branch.
   Location: [OidcAuthorizationProvider.java](/home/agentuser/qcxf/extensions/oidc/runtime/src/main/java/io/quarkiverse/cxf/oidc/OidcAuthorizationProvider.java:62).

5. **P1: A token response without a usable access token still dispatches SOAP — R-12.**
   The selected Quarkus version accepts a token response with a null or empty access-token value.
   `TokensHelper` caches it, and this mapper produces `Bearer null` or `Bearer `.
   The existing missing-token test records a SOAP POST, violating the failure-before-dispatch requirement.
   Standalone probes independently reproduce both null and empty cases.
   Reject null/blank tokens before header construction and ensure invalid results do not remain reusable cached tokens.
   Location: [OidcAuthorizationProvider.java](/home/agentuser/qcxf/extensions/oidc/runtime/src/main/java/io/quarkiverse/cxf/oidc/OidcAuthorizationProvider.java:71).

6. **P1: Token-endpoint errors expose secret-bearing response bodies — R-16.**
   The failed Uni reaches CXF unchanged.
   In Quarkus 4.0.0.Beta2, `OidcClientImpl.emitGrantTokens()` includes the raw failed response body
   in `OidcClientException` and logs it at DEBUG.
   The acquire OAuth-error test fails because the caller-visible exception chain contains the fixture's synthetic token.
   Sanitize the failure without retaining the original secret-bearing cause;
   the dependency's earlier DEBUG logging also needs a redaction solution.
   This finding concerns diagnostics/errors;
   it does not assume ordinary CXF outbound logging sees the late-added header.
   Location: [OidcAuthorizationProvider.java](/home/agentuser/qcxf/extensions/oidc/runtime/src/main/java/io/quarkiverse/cxf/oidc/OidcAuthorizationProvider.java:67).

7. **P2: Required client-creation validation is absent — R-5/R-14.**
   The runtime recorder installs the provider, but `OidcClientFactoryCustomizer.customize()` remains empty.
   There is no grant compatibility check, and acquire plus Basic/Digest creates a proxy successfully.
   Both acquire authentication-conflict cases fail their creation-time assertions.
   At invocation, the new header overwrites existing authentication instead of reporting the invalid configuration.
   Implement validation before proxy creation, rather than relying on a later grant or downstream failure.
   Location: [CxfOidcProcessor.java](/home/agentuser/qcxf/extensions/oidc/deployment/src/main/java/io/quarkiverse/cxf/oidc/deployment/CxfOidcProcessor.java:19).

## Assessment

This is a useful initial implementation, but it does not yet satisfy the acquire contract.
The asynchronous transport hook and reuse of Quarkus's lifecycle helper are sound foundations.
The remaining problems include credential disclosure and incorrect authentication selection, beyond missing polish.

| Acquire requirement | Assessment |
|---|---|
| Configuration/default disabled/extension (R-1/R-6) | Present, mostly inherited from preceding scaffolding |
| Named client credentials, sync/async (R-2/AC-2) | Pass with the test setup adjustments below |
| Default/named selection (R-5/AC-4) | Cold default selection fails; unknown names fall back incorrectly |
| Lifecycle (R-9/AC-5) | Named reuse, expiry/reacquisition, refresh and rejected-refresh handling pass |
| Fail before SOAP (R-12/AC-8) | Ordinary failed grants stop dispatch; null/empty responses do not |
| No authorization replay (R-13) | All six acquire 401/403 sync/async cases pass |
| Invalid grant/authentication combinations (R-5/R-14) | Validation missing |
| Redirect origin policy (R-15) | Violated |
| Secret masking (R-16) | Diagnostic/error masking violated |
| WSDL (R-17/R-19) | Protected WSDL works; gate and origin restrictions are missing |
| Vert.x-only (R-20/AC-1) | Hook exists only in Vert.x; treatment of alternatives is unspecified |
| JVM/native/docs (R-7/R-21) | JVM partly verified; native not run; linked user-guide section absent |

No additional acquire tests were added by this commit.
Substantial contract tests already exist from earlier commits;
their existence is not evidence that this commit passes them.
The extension usage text links to `authentication-authorization.adoc#client-oidc`,
but that guide currently has no such section.

## Validation and limits

The extension and dependencies built successfully:

```bash
mvnd -B -pl extensions/oidc/deployment -am install -DskipTests -Dquarkus.build.skip
```

Focused JVM tests were executed using Maven after an mvnd daemon communication failure.
Two command-line adjustments were necessary without editing test sources or application configuration:

- `-Dquarkus.devservices.enabled=false` avoids automatic Keycloak container startup,
  which fails in this environment; these contract tests supply their own HTTP OIDC fixture.
- Override the default and configured named OIDC clients' `connection-retry-count` to `1`.
  Their existing value `0` fails in Quarkus 4.0.0.Beta2's Mutiny `retry().atMost(0)` path
  before the token request reaches the fixture.

The first adjusted run selected acquisition, refresh, token failure, authentication conflict, SOAP redirect,
and public/protected WSDL methods.
It ran 80 parameterized cases with 66 failures, including propagation/exchange cases outside this review's scope.
This total must not be interpreted as an acquire-only pass rate.

Acquire-specific results from that run:

- Named sync/async acquisition: both pass.
- Cold default sync/async acquisition: both fail with `NullPointerException`.
- Refresh and rejected-refresh handling: both pass.
- Same-origin SOAP redirects: sync and async pass.
- Host, port, and HTTPS-to-HTTP SOAP redirects: destinations receive credentials and assertions fail.
  HTTP-to-HTTPS cases do not reach the destination, so they do not establish that boundary's header behavior.
- Public WSDL with secure access omitted/false: both fail because credentials are sent.
- Protected named/default WSDL: both pass in a provider already initialized by named acquisition.
- Acquire plus Basic/Digest: both fail because client creation succeeds.
- OAuth-error diagnostic masking and missing-token dispatch assertions fail.
  The cached invalid token contaminates later cases on the same client;
  those later failures do not independently prove each endpoint-failure variant.

A second adjusted run selected disabled-mode compatibility, reuse/reacquisition,
authorization-failure replay, and WSDL origins.
It ran 51 cases with 38 failures, again including propagation/exchange cases outside this review's scope.
Acquire-specific observations:

- All six omitted/disabled authentication compatibility cases pass.
- Named token reuse and expiry/reacquisition pass; cold default acquisition fails again.
- All six acquire authorization-failure cases pass: 401 with/without a challenge and 403, sync and async.
  Each failed operation is received once, and a separate later invocation succeeds.
- WSDL host redirects and HTTPS-to-HTTP redirects expose credentials.
  Other WSDL-origin cases fail before recording the expected document fetches,
  so they do not independently validate import/header behavior.
  The import concern in finding 2 follows the resolver/conduit code path.

A standalone diagnostic program used the compiled provider and real `TokensHelper`, with stubbed OIDC clients,
to independently reproduce cold-default failure, unknown-name fallback, null/empty header generation,
and successful valid-token reuse.
It does not replace integration testing.

Local evidence:
[build log](/tmp/qcxf-i2371-review-build.log),
[adjusted contract-test log](/tmp/qcxf-i2371-review-adjusted-tests.log),
[lifecycle/origin test log](/tmp/qcxf-i2371-review-lifecycle-origins.log),
[probe source](/tmp/QcxfAcquireProbe.java), and
[probe results](/tmp/qcxf-i2371-review-probe.log).

## Questions and unverified behavior

- The exact compatible grant matrix is still an open question in the test plan,
  and its incompatible-grant test is disabled.
  This does not remove the specification's requirement to validate compatibility on creation.
- Direct `TokensHelper` use provides demand-driven lifecycle handling,
  but does not register the background refresh used by `AbstractTokensProducer`.
  Clarify which lifecycle settings this integration promises and document them.
  In particular, Quarkus documents the integration-specific scope of
  [`refresh-interval`](https://quarkus.io/guides/security-openid-connect-client-reference/#refreshing-access-tokens).
- Native execution, full logging behavior, and concurrency/event-loop-specific scenarios were not verified here.
  The static provider's application/dev-mode shutdown behavior also remains untested.

This review stays within OSS Helper's code-review scope.
It does not replace specialized AI review tools or static analysis.
Inline comments are review annotations only; production behavior was not changed.
