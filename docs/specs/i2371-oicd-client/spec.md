# Quarkus OIDC Client integration for SOAP clients

## Original requirements from [#2371](https://github.com/quarkiverse/quarkus-cxf/issues/2371)

Applications calling OAuth 2.0 / OIDC-protected SOAP services should be able to configure authentication declaratively,
using Quarkus OIDC facilities without writing application-specific CXF interceptors for token management.
The requested experience is comparable to REST client integration through `@OidcClientFilter` and `@AccessToken`.

Quarkus CXF client proxies must set the HTTP `Authorization` header to `Bearer <access_token>`
for the following scenarios:

1. <a id="r-2"></a>**R-2 — Client credentials**:
   acquire the token through a named Quarkus OIDC client and attach it to outbound SOAP calls.
2. <a id="r-3"></a>**R-3 — Access-token propagation**:
   forward the current caller's access token to the downstream SOAP service.
3. <a id="r-4"></a>**R-4 — RFC 8693 token exchange**:
   exchange the caller's token through a named or default Quarkus OIDC client,
   then authenticate the SOAP call with the resulting token.

The issue's follow-up acknowledges evaluation and adds no further requirements.

## Existing capabilities and design context

These observations explain why the open questions matter; they do not prescribe an implementation.

* Quarkus CXF already supports a configured Bearer token through
  `quarkus.cxf.client."client-name".auth.scheme` and `quarkus.cxf.client."client-name".auth.token`.
  The requested integration adds token acquisition, lifecycle management, and selection of the current caller's token.
* Quarkus OIDC Client provides asynchronous token APIs and a `TokenProvider` SPI.
  Its token helpers and producers already implement caching, refresh, and reacquisition.
  Exchange results can be cached separately for different additional grant parameters, including subject tokens.
* Quarkus REST token propagation supports both RFC 8693 exchange and JWT-bearer exchange.
* The Quarkus REST OIDC client filter's optional refresh-on-unauthorized behavior
  replaces the token on the next invocation;
  it does not replay the failed REST invocation.
* CXF has its own authentication policies, authentication suppliers, redirects, and authorization retransmits.
  It also retrieves WSDL documents separately from SOAP operation invocations.
* Asynchronous CXF invocations made on an event-loop thread can be dispatched to a worker thread.
  Propagation must account for access to the caller's identity across that transition.

## Further design and scope decisions

### Activation and configuration API

#### <a id="r-1"></a>R-1: Activation and modes

* Enable OIDC for SOAP clients through configuration in application.properties only for now
* This will allow using generated interfaces without modification
* `@OidcClientFilter` and `@AccessToken` can be implemented later
* `quarkus.cxf.client."client-name".oidc.mode` - one of `disabled` (default), `acquire`, `propagate`, `exchange`

#### <a id="r-5"></a>R-5: OIDC client selection and grant compatibility

* `quarkus.cxf.client."client-name".oidc.client-name` - the name of a configured Quarkus OIDC client
  (or Default OIDC client);
  used only for acquisition or exchange.
  * The client's `grant.type` must be compatible with `quarkus.cxf.client."client-name".oidc.mode`.
    OIDC client with an incompatible grant is rejected on SOAP client creation.

### Code organisation, target stream, native mode

#### <a id="r-6"></a>R-6: Extension and supported proxies

* The new OIDC functionality will be hosted in a new extension called `quarkus-cxf-oidc`
* All three modes will be covered by `quarkus-cxf-oidc`
* Only proxies injected with `@CXFClient` will be supported
* Programmatic JAX-WS clients are out of scope for now

#### <a id="r-7"></a>R-7: Target stream and execution modes

* The new functionality is planned for Quarkus 4.0.
* Support both JVM and native mode

### Source and availability of the caller's access token

* **[R-3](#r-3):** Obtain the token from the authenticated Quarkus security context for each invocation
  rather than from raw incoming HTTP header
  * The propagation will be tested for requests coming via SOAP and REST
* <a id="r-8"></a>**R-8:** When the caller is anonymous, the credential is absent or ambiguous
  or there is no active request context (as in a scheduled job) then the SOAP client invocation must fail.

### Token lifetime, caching, and concurrency

* <a id="r-9"></a>**R-9:** Reuse Quarkus token lifecycle support and its documented configuration where applicable.
* <a id="r-10"></a>**R-10:** Keep exchanged tokens isolated by subject token, selected OIDC client,
  and relevant grant parameters.
* <a id="r-11"></a>**R-11:** Explicitly document the effect of exchange caching on revocation detection.

### Failures, timeouts, and downstream authorization failures

* <a id="r-12"></a>**R-12:** Fail before sending the SOAP request
  if the selected OIDC client cannot supply its a token required for the given authentication mode.
* <a id="r-13"></a>**R-13:** Do not automatically replay a SOAP operation after an authorization failure.
  Any refresh-on-unauthorized option must distinguish replacing the token for a later invocation
  from retrying the current invocation.

### Interaction with existing HTTP authentication and transport behavior

* <a id="r-14"></a>**R-14:** OIDC configuration and Basic or similar authentication on the same client
  results in failure on client creation
* <a id="r-15"></a>**R-15:** Bearer credentials should be resent or not resent on redirects
  according to the same criteria s Quarkus REST,
  which is said to rely on underlying [Vert.x redirect policy](https://vertx.io/docs/5.2.2/vertx-core/java/#_default_http_redirect_policy):
  resend for same-origin redirects (same host, port and URI scheme), don't resend otherwise
* <a id="r-16"></a>**R-16:** The tokens must be masked in CXF message logging, diagnostics, and authentication errors

### WSDL retrieval

* <a id="r-17"></a>**R-17:** The new OIDC functionality will cover the retrieval of WSDL documents
  only if `quarkus.cxf.client."client-name".secure-wsdl-access` is enabled.
* <a id="r-18"></a>**R-18:** WSDL loading must fail if caller token is required but not available.
* <a id="r-19"></a>**R-19:** Imported documents or WSDL redirects to a different origin
  won't get the Authorization header

### Acceptance criteria

* <a id="r-20"></a><a id="ac-1"></a>**R-20 / AC-1:** Only VertxHttpClientHTTPConduit is supported.
* Cover iby tests:
  * <a id="ac-2"></a>**AC-2:** Synchronous and asynchronous SOAP client calls
  * <a id="ac-3"></a>**AC-3:** All three OIDC modes
  * <a id="ac-4"></a>**AC-4:** Named and default OIDC clients
  * <a id="ac-5"></a>**AC-5:** Token reuse, expiry, refresh, and reacquisition.
  * <a id="ac-6"></a>**AC-6:** Missing caller credentials
  * <a id="ac-7"></a>**AC-7:** Invalid configuration
  * <a id="ac-8"></a>**AC-8:** Token-endpoint failures
  * <a id="ac-9"></a>**AC-9:** Redirects
  * <a id="ac-10"></a>**AC-10:** WSDL access
  * <a id="ac-11"></a>**AC-11:** SOAP client auth Configuration conflicts (Basic and OIDC)
  * <a id="ac-12"></a>**AC-12:** Concurrent calls with different callers must demonstrate
    that tokens do not cross between identities.
  * <a id="r-21"></a><a id="ac-13"></a>**R-21 / AC-13:**
    Use intergration tests as examples in the documentation for each mode.
