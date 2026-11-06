# Outbound OIDC contract tests

These tests implement the scenarios in
[the issue 2371 test plan](../../docs/specs/i2371-oicd-client/test-plan.md).
They use the `quarkus-cxf-oidc` extension.
All new test methods carry `@Tag("T-nnn")`; parameterized invocations also display the scenario ID.

`OidcTestResource` runs an external, controllable OIDC provider, SOAP recorder, and HTTP/HTTPS document servers.
The application authenticates incoming opaque tokens using Quarkus OIDC introspection.
Both REST and SOAP ingress call the same injected proxies generated from `wsdl/oidc-echo.wsdl`.
The application contains no outbound token-management interceptor.
Synthetic identities are confined to the explicit test-driver identity route.
A plain executor supplies the no-request-context variant.

The recorder counts SOAP receipt before checking authentication, including failed and redirected requests.
Each operation has a unique ID.
Tests inspect issued grants, exact Authorization headers, expiry times, and independent pending-response gates.
They do not infer authentication from a successful SOAP result alone.
Fixture state is observed over HTTP and application logs through `TestLog`, including for native execution.

Each test class has an isolated resource configuration.
The default client uses client credentials except in the dedicated default-exchange classes.
All supported SOAP clients explicitly select the Vert.x conduit,
while application.properties sets URLConnection globally.
Remote WSDL tests create fresh injected proxies and evict the root definition from CXF's WSDL cache before creation.
The generated interface is also configured explicitly so no runtime WSDL is needed for ordinary operation tests.

| Scenario | Coverage |
|---|---|
| T-001 | `OidcModesTest`: omitted/disabled mode with anonymous, Basic, and static Bearer authentication |
| T-002 | `OidcModesTest`: named/default acquisition, sync/callback calls |
| T-003 | `OidcModesTest`: SOAP/REST propagation with A, B, and a replacement token for A |
| T-004 | `OidcModesTest`, `OidcDefaultExchangeTest`: named/default RFC 8693 exchange |
| T-005 | `OidcCredentialsTest`: security identity wins over raw headers; absent and ambiguous credentials fail |
| T-006 | `OidcCredentialsTest`: warmed proxies, anonymous requests, and contextless sync/async invocations |
| T-007 | Extension deployment tests: invalid modes, incompatible grants, unknown clients |
| T-008 | `OidcConfigurationTest`: Basic/Digest conflicts in every enabled mode, before invocation |
| T-009 | `OidcModesTest`: effective conduit; extension deployment tests reject unsupported conduits |
| T-010 | `OidcTokenLifecycleTest`, `OidcDefaultExchangeLifecycleTest`: reuse and expiry/reacquisition |
| T-011 | `OidcTokenLifecycleTest`: refresh replacement and failure with no successful fallback |
| T-012 | `OidcIsolationTest`: subject-token and selected-client isolation, configured audiences |
| T-013 | `OidcIsolationTest`: overlapping SOAP/REST sync/async calls, reversed completion, warm caches |
| T-014 | `OidcTokenFailureTest`: OAuth errors, 5xx, malformed/tokenless replies, refusal, timeout |
| T-015 | `OidcAuthorizationFailureTest`: 401 with/without challenge and 403 never replay operations |
| T-016 | `OidcRedirectTest` and extension `OidcOriginTest`: live redirects, scheme-only changes, default ports |
| T-017 | `OidcRedirectTest`: all modes and sync/async redirects, including both HTTP/HTTPS directions |
| T-018 | `OidcLoggingTest`, `OidcPerClientLoggingTest`: logged traffic and diagnostic/cause-chain masking |
| T-019 | `OidcWsdlTest`: omitted/false secure WSDL gate, including anonymous creation |
| T-020 | `OidcWsdlTest`, `OidcDefaultExchangeWsdlTest`: protected WSDL and subsequent SOAP authentication |
| T-021 | `OidcWsdlTest`: missing credentials fail during fresh WSDL creation, before an operation |
| T-022 | `OidcWsdlOriginTest`: cross-origin WSDL redirects and WSDL/XSD imports |
| T-023 | `OidcIsolationTest`: event-loop entry, worker dispatch, held grants, identity isolation, failures |
| T-024 | Not applicable: no optional unauthorized-refresh policy; mandatory no-replay assertions are enabled in T-015 |
| T-025 | Antora source copying and rendered examples; see implementation notes for documentation validation |
| T-026 | Matching `*IT` subclasses inherit application cases and add the T-026 tag |

The module already appears in the reactor and native CI matrix.
`OidcInvalidModeTest` lives in `extensions/oidc/deployment` because invalid configuration cannot start
an HTTP-driven application, and Quarkus rejects mixing the two test frameworks in one test run.
It has no native counterpart.

The original pending placeholders remain disabled.
Their current status is:

- Q-1/Q-4: grant compatibility and creation-time transport rejection are covered by extension deployment tests.
- Q-5: varying only an additional exchange parameter while keeping the subject token and selected client fixed.
  The enabled audience test changes the configured OIDC client too, so it does not claim that independent dimension.
- T-016: `OidcOriginTest` exercises the implementation's origin decision at an identical port.
- Q-8: no optional unauthorized-refresh policy is provided.
- T-025: documentation rendering is checked separately from the application tests.

`OidcPendingContractTest` does not count as passing application coverage.
The enabled lifecycle tests use the Quarkus 4.0.0.Beta2 settings `token-cache-max-size`,
`early-tokens-acquisition`, `refresh-token-time-skew`, `connection-timeout`, and `connection-retry-count`.
They do not prescribe refresh behavior for exchange, exact concurrent grant counts, default-port normalization,
redirect chains that return to the original origin, or the timing of a failed unauthenticated WSDL GET.

Run the outbound fixture suite after installing the extension and BOM:

```bash
mvnd -f integration-tests/oidc/pom.xml clean test '-Dtest=Oidc*Test' \
  -Dquarkus.keycloak.devservices.enabled=false
mvnd -f extensions/oidc/deployment/pom.xml test \
  -Dtest=OidcConfigurationTest,OidcInvalidModeTest,OidcOriginTest,OidcRedactionTest
```

The local resources supply OIDC provider URLs after Dev Services initialization, so disable Keycloak Dev Services
for these outbound tests.
The separate `HelloServiceTest` still uses Keycloak.
See [implementation notes](../../docs/specs/i2371-oicd-client/implementation-notes.md)
for evidence supporting fixture corrections and validation results.
