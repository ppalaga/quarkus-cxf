package io.quarkiverse.cxf.it.auth.oidc.client;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.certs.CertificateGenerator;
import io.smallrye.certs.CertificateRequest;
import io.smallrye.certs.Format;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * External OIDC provider and SOAP/document recorder, shared by JVM and native test drivers.
 * Control and observation use HTTP; no application JVM objects or static test state cross the process boundary.
 */
public class OidcTestResource implements QuarkusTestResourceLifecycleManager {
    static final String EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final Pattern OPERATION = Pattern.compile("<(?:[^:<>]+:)?operationId>([^<]+)</");
    private final JsonArray grants = new JsonArray();
    private final JsonArray soap = new JsonArray();
    private final JsonArray documents = new JsonArray();
    private final Map<String, String> callers = new HashMap<>();
    private final Map<String, JsonObject> tokenRules = new HashMap<>();
    private final Map<String, JsonObject> soapRules = new HashMap<>();
    private final Map<String, JsonObject> documentRules = new HashMap<>();
    private final Map<String, JsonObject> issued = new HashMap<>();
    private final Map<String, JsonObject> refreshTokens = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private Vertx vertx;
    private String http;
    private String otherPort;
    private String https;
    private String wsdl;
    private Map<String, String> arguments = Map.of();

    @Override
    public void init(Map<String, String> arguments) {
        this.arguments = arguments;
    }

    @Override
    public Map<String, String> start() {
        try {
            try (var stream = getClass().getClassLoader().getResourceAsStream("wsdl/oidc-echo.wsdl")) {
                if (stream == null) {
                    throw new IllegalStateException("Missing generated-client WSDL fixture");
                }
                wsdl = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            Path certificates = Files.createTempDirectory("qcxf-oidc-");
            new CertificateGenerator(certificates, true).generate(new CertificateRequest().withName("localhost")
                    .withCN("localhost").withFormat(Format.PKCS12).withPassword("password")
                    .withDuration(Duration.ofDays(2)));
            vertx = Vertx.vertx();
            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());
            // Use one Vert.x context for all state changes, even when the three listeners accept requests concurrently.
            var context = vertx.getOrCreateContext();
            router.route().handler(request -> context.runOnContext(ignored -> handle(request)));
            http = listen(router, new HttpServerOptions(), "http");
            otherPort = listen(router, new HttpServerOptions(), "http");
            https = listen(router, new HttpServerOptions().setSsl(true).setKeyCertOptions(new PfxOptions()
                    .setPath(certificates.resolve("localhost-keystore.p12").toString()).setPassword("password")), "https");

            Map<String, String> config = new HashMap<>();
            config.put("oidc.fixture.http", http);
            config.put("oidc.fixture.https", https);
            config.put("quarkus.oidc.auth-server-url", http);
            config.put("quarkus.oidc.discovery-enabled", "false");
            config.put("quarkus.oidc.introspection-path", "/introspect");
            config.put("quarkus.oidc.token.principal-claim", "sub");
            config.put("quarkus.oidc.allow-token-introspection-cache", "false");
            config.put("quarkus.oidc-client.client-enabled", "true");
            config.put("quarkus.tls.oidc-fixture.trust-store.p12.path",
                    certificates.resolve("localhost-truststore.p12").toString());
            config.put("quarkus.tls.oidc-fixture.trust-store.p12.password", "password");
            // TLS configuration also applies to an HTTPS target reached from an HTTP SOAP redirect.
            for (String client : List.of("acquireDefault", "acquireNamed", "defaultClient", "propagate", "exchangeNamed",
                    "wsdlAcquireSecure", "wsdlPropagateSecure", "wsdlExchangeSecure")) {
                config.put("quarkus.cxf.client." + client + ".tls-configuration-name", "oidc-fixture");
            }
            for (String client : List.of("named", "exchange", "exchange-other", "exchange-audience", "lifecycle",
                    "lifecycle-exchange", "refresh", "refresh-failure", "failure-acquire", "failure-exchange",
                    "refused-acquire", "refused-exchange", "concurrent-exchange", "async-acquire", "async-exchange")) {
                config.put("quarkus.oidc-client." + client + ".client-enabled", "true");
            }
            for (String client : List.of("concurrent-exchange", "async-acquire", "async-exchange")) {
                config.put("quarkus.oidc-client." + client + ".connection-timeout", "15S");
            }
            try (ServerSocket unused = new ServerSocket(0)) {
                String refused = "http://localhost:" + unused.getLocalPort() + "/token";
                config.put("quarkus.oidc-client.refused-acquire.token-path", refused);
                config.put("quarkus.oidc-client.refused-exchange.token-path", refused);
            }
            if ("exchange".equals(arguments.get("default-grant"))) {
                config.put("quarkus.oidc-client.grant.type", "exchange");
                config.put("quarkus.cxf.client.defaultClient.oidc.mode", "exchange");
                config.put("quarkus.cxf.client.wsdlDefaultSecure.oidc.mode", "exchange");
            }
            if ("global".equals(arguments.get("logging"))) {
                config.put("quarkus.cxf.logging.enabled-for", "clients");
            } else if ("client".equals(arguments.get("logging"))) {
                for (String client : List.of("acquireNamed", "propagate", "exchangeNamed", "refresh", "failureExchange",
                        "wsdlAcquireSecure", "wsdlPropagateSecure", "wsdlExchangeSecure")) {
                    config.put("quarkus.cxf.client." + client + ".logging.enabled", "true");
                }
            }
            return config;
        } catch (Exception error) {
            stop();
            throw new IllegalStateException("Cannot start OIDC contract fixtures", error);
        }
    }

    private String listen(Router router, HttpServerOptions options, String scheme) {
        HttpServer server = vertx.createHttpServer(options).requestHandler(router).listen(0, "localhost")
                .toCompletionStage().toCompletableFuture().join();
        return scheme + "://localhost:" + server.actualPort();
    }

    private void handle(RoutingContext request) {
        String path = request.request().path();
        if (path.startsWith("/control/")) {
            control(request, path.substring("/control/".length()));
        } else if (path.equals("/state")) {
            json(request, new JsonObject().put("grants", grants).put("soap", soap).put("documents", documents)
                    .put("pending", new JsonArray(pending.stream().map(Pending::key).toList()))
                    .put("http", http).put("otherPort", otherPort).put("https", https));
        } else if (path.equals("/introspect")) {
            String token = request.request().getFormAttribute("token");
            String subject = callers.get(token);
            json(request, new JsonObject().put("active", subject != null).put("sub", subject)
                    .put("username", subject).put("exp", Instant.now().plusSeconds(3600).getEpochSecond()));
        } else if (path.startsWith("/token/")) {
            grant(request, path.substring("/token/".length()));
        } else if (path.startsWith("/wsdl/")) {
            document(request);
        } else if (path.startsWith("/soap/") || path.startsWith("/destination/")) {
            soap(request);
        } else {
            request.response().setStatusCode(404).end();
        }
    }

    private void control(RoutingContext request, String action) {
        JsonObject body = request.body().asJsonObject();
        switch (action) {
            case "caller" -> {
                String token = "subject-" + body.getString("subject") + "-" + UUID.randomUUID();
                callers.put(token, body.getString("subject"));
                json(request, new JsonObject().put("token", token));
                return;
            }
            case "token" -> tokenRules.put(body.getString("client"), body);
            case "soap" -> soapRules.put(body.getString("id"), body);
            case "document" -> documentRules.put(body.getString("client"), body);
            case "release" -> {
                List<Pending> matches = pending.stream().filter(item -> item.key().equals(body.getString("key"))).toList();
                pending.removeAll(matches);
                matches.forEach(item -> item.reply().run());
            }
            default -> throw new IllegalArgumentException(action);
        }
        json(request, new JsonObject().put("ok", true));
    }

    private void grant(RoutingContext request, String client) {
        JsonObject parameters = new JsonObject();
        request.request().formAttributes().forEach(entry -> parameters.put(entry.getKey(), entry.getValue()));
        String authorization = request.request().getHeader("Authorization");
        String expectedBasic = "Basic " + Base64.getEncoder()
                .encodeToString((client + ":secret-" + client).getBytes(StandardCharsets.UTF_8));
        boolean authenticated = expectedBasic.equals(authorization)
                || (client.equals(parameters.getString("client_id"))
                        && ("secret-" + client).equals(parameters.getString("client_secret")));
        JsonObject record = new JsonObject().put("client", client).put("parameters", parameters)
                .put("authenticated", authenticated);
        grants.add(record);
        if (!authenticated) {
            request.response().setStatusCode(401).end();
            return;
        }
        JsonObject rule = tokenRules.getOrDefault(client, new JsonObject());
        Runnable reply = () -> tokenResponse(request, record, rule);
        if (rule.getBoolean("hold", false)) {
            pending.add(new Pending("token:" + client + ":" + parameters.getString("subject_token", ""), reply));
        } else if ("timeout".equals(rule.getString("error"))) {
            // A real pending HTTP request: Quarkus' configured endpoint timeout must end the invocation.
            pending.add(new Pending("timeout:" + client, reply));
        } else {
            reply.run();
        }
    }

    private void tokenResponse(RoutingContext request, JsonObject record, JsonObject rule) {
        String error = rule.getString("error", "");
        if (!error.isEmpty()) {
            switch (error) {
                case "oauth", "timeout" -> request.response().setStatusCode(400).end(new JsonObject()
                        .put("error", "invalid_grant").put("error_description", "Rejected " + rule.getString("secret", ""))
                        .encode());
                case "server" -> request.response().setStatusCode(503).end("Unavailable");
                case "malformed" -> request.response().putHeader("Content-Type", "application/json").end("{broken");
                case "missing" -> json(request, new JsonObject().put("expires_in", 60));
                case "empty" -> json(request, new JsonObject().put("access_token", "").put("expires_in", 60));
                default -> throw new IllegalArgumentException(error);
            }
            return;
        }
        JsonObject parameters = record.getJsonObject("parameters");
        String grantType = parameters.getString("grant_type");
        JsonObject previous = refreshTokens.get(parameters.getString("refresh_token", ""));
        if (!("client_credentials".equals(grantType)
                || EXCHANGE_GRANT.equals(grantType) && callers.containsKey(parameters.getString("subject_token"))
                || "refresh_token".equals(grantType) && previous != null)) {
            request.response().setStatusCode(400).end(new JsonObject().put("error", "invalid_grant").encode());
            return;
        }
        String token = "access-" + record.getString("client") + "-" + UUID.randomUUID();
        int lifetime = rule.getInteger("expiresIn", 3600);
        record.put("accessToken", token).put("expiresAt", Instant.now().plusSeconds(lifetime).getEpochSecond());
        issued.put(token, record);
        JsonObject response = new JsonObject().put("access_token", token).put("token_type", "Bearer")
                .put("expires_in", lifetime);
        if (rule.getBoolean("refresh", false)) {
            String refresh = "refresh-" + UUID.randomUUID();
            refreshTokens.put(refresh, record);
            record.put("refreshToken", refresh);
            response.put("refresh_token", refresh).put("refresh_expires_in", 3600);
        }
        json(request, response);
    }

    private void soap(RoutingContext request) {
        Matcher matcher = OPERATION.matcher(request.body().asString());
        if (!matcher.find()) {
            request.response().setStatusCode(400).end("Missing operation ID");
            return;
        }
        String id = matcher.group(1);
        JsonObject record = observation(request).put("id", id);
        soap.add(record); // Count receipt before authentication, redirects, or deliberately unsuccessful responses.
        JsonObject rule = soapRules.getOrDefault(id, new JsonObject());
        boolean destination = request.request().path().startsWith("/destination/");
        JsonArray redirectChain = rule.getJsonArray("redirectChain");
        int redirectIndex = (int) soap.stream().map(JsonObject.class::cast)
                .filter(receipt -> id.equals(receipt.getString("id"))).count() - 1;
        Runnable reply = () -> {
            if (redirectChain != null && redirectIndex < redirectChain.size()) {
                request.response().setStatusCode(307)
                        .putHeader("Location", target(redirectChain.getString(redirectIndex))
                                + "/destination/" + id + "/hop-" + redirectIndex)
                        .end();
            } else if (!destination && rule.containsKey("redirect")) {
                request.response().setStatusCode(307).putHeader("Location", target(rule.getString("redirect"))
                        + "/destination/" + id).end();
            } else if (rule.containsKey("status")) {
                if (rule.getBoolean("challenge", false)) {
                    request.response().putHeader("WWW-Authenticate", "Bearer realm=\"soap\"");
                }
                request.response().setStatusCode(rule.getInteger("status")).end();
            } else {
                String auth = request.request().getHeader("Authorization");
                boolean disabled = request.request().path().contains("omitted")
                        || request.request().path().contains("disabled");
                if (!disabled && !destination && !validBearer(auth)) {
                    request.response().setStatusCode(401).end();
                    return;
                }
                request.response().putHeader("Content-Type", "text/xml; charset=UTF-8").end("""
                        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                          <soap:Body><e:echoResponse xmlns:e="urn:quarkus-cxf:oidc:echo">
                            <return>%s</return>
                          </e:echoResponse></soap:Body>
                        </soap:Envelope>
                        """.formatted(id));
            }
        };
        if (rule.getBoolean("hold", false)) {
            pending.add(new Pending("soap:" + id, reply));
        } else {
            reply.run();
        }
    }

    private boolean validBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        String token = authorization.substring("Bearer ".length());
        JsonObject grant = issued.get(token);
        return callers.containsKey(token) || grant != null
                && grant.getLong("expiresAt") > Instant.now().getEpochSecond();
    }

    private void document(RoutingContext request) {
        documents.add(observation(request));
        String[] path = request.request().path().split("/");
        String client = path[2];
        String file = path[3];
        JsonObject rule = documentRules.getOrDefault(client, new JsonObject());
        if ("root.wsdl".equals(file)) {
            if (rule.getBoolean("protected", false) && !validBearer(request.request().getHeader("Authorization"))) {
                request.response().setStatusCode(401).end();
                return;
            }
            if (rule.containsKey("redirect")) {
                request.response().setStatusCode(307).putHeader("Location", target(rule.getString("redirect"))
                        + "/wsdl/" + client + "/redirected.wsdl").end();
                return;
            }
        }
        String content;
        if ("import.wsdl".equals(file)) {
            content = """
                    <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" targetNamespace="urn:oidc:imported">
                      <wsdl:import namespace="urn:oidc:nested" location="nested.wsdl"/>
                    </wsdl:definitions>
                    """;
        } else if ("nested.wsdl".equals(file)) {
            content = """
                    <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" targetNamespace="urn:oidc:nested"/>
                    """;
        } else if ("import.xsd".equals(file)) {
            content = """
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:oidc:imported">
                      <xs:import namespace="urn:oidc:nested" schemaLocation="nested.xsd"/>
                      <xs:element name="imported" type="xs:string"/>
                    </xs:schema>
                    """;
        } else if ("nested.xsd".equals(file)) {
            content = """
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:oidc:nested">
                      <xs:element name="nested" type="xs:string"/>
                    </xs:schema>
                    """;
        } else {
            content = wsdl.replace("http://localhost:1/soap", http + "/soap/" + client);
            if (rule.containsKey("import")) {
                String base = target(rule.getString("import")) + "/wsdl/" + client;
                content = content.replace("<wsdl:types>", "<wsdl:import namespace=\"urn:oidc:imported\" location=\""
                        + base + "/import.wsdl\"/><wsdl:types>")
                        .replace("<xs:element name=\"echo\">", "<xs:import namespace=\"urn:oidc:imported\" schemaLocation=\""
                                + base + "/import.xsd\"/><xs:element name=\"echo\">");
            }
        }
        request.response().putHeader("Content-Type", "text/xml").end(content);
    }

    private String target(String origin) {
        return switch (origin) {
            case "same" -> http;
            case "host" -> http.replace("localhost", "127.0.0.1");
            case "port" -> otherPort;
            case "https" -> https;
            case "http" -> http;
            default -> throw new IllegalArgumentException(origin);
        };
    }

    private JsonObject observation(RoutingContext request) {
        return new JsonObject().put("uri", request.request().absoluteURI())
                .put("path", request.request().path()).put("method", request.request().method().name())
                .put("authorization", new JsonArray(request.request().headers().getAll("Authorization")));
    }

    private static void json(RoutingContext request, JsonObject value) {
        request.response().putHeader("Content-Type", "application/json").end(value.encode());
    }

    @Override
    public void stop() {
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    private record Pending(String key, Runnable reply) {
    }
}
