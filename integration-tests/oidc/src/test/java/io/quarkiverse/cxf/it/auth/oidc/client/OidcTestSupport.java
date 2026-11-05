package io.quarkiverse.cxf.it.auth.oidc.client;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;

import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/** All observations work over HTTP, including when the application runs as a native executable. */
public abstract class OidcTestSupport {
    protected String fixture;
    protected String callerA;
    protected String callerB;
    protected String replacementA;

    @BeforeEach
    void prepareCallers() {
        fixture = request(null).get("/oidc-client/fixture").then().statusCode(200).extract().path("url");
        callerA = caller("A");
        callerB = caller("B");
        replacementA = caller("A");
    }

    protected String caller(String subject) {
        return control("caller", new JsonObject().put("subject", subject)).getString("token");
    }

    protected JsonObject control(String action, JsonObject value) {
        return new JsonObject(request(null).contentType("application/json").body(value.encode())
                .post(fixture + "/control/" + action).then().statusCode(200).extract().asString());
    }

    protected JsonObject state() {
        return new JsonObject(request(null).get(fixture + "/state").then().statusCode(200).extract().asString());
    }

    protected static List<JsonObject> objects(JsonArray array) {
        return array.stream().map(value -> (JsonObject) value).toList();
    }

    protected List<JsonObject> grants() {
        return objects(state().getJsonArray("grants"));
    }

    protected List<JsonObject> grants(String client) {
        return grants().stream().filter(grant -> client.equals(grant.getString("client"))).toList();
    }

    protected List<JsonObject> posts(String id) {
        return objects(state().getJsonArray("soap")).stream().filter(post -> id.equals(post.getString("id"))).toList();
    }

    protected JsonObject grantFor(String token) {
        List<JsonObject> matching = grants().stream().filter(grant -> token.equals(grant.getString("accessToken"))).toList();
        assertThat(matching).as("The outgoing token must have been issued by the fixture").hasSize(1);
        return matching.get(0);
    }

    protected String sentToken(String id) {
        assertThat(posts(id)).as("Exactly one SOAP operation for %s", id).hasSize(1);
        List<String> headers = posts(id).get(0).getJsonArray("authorization").stream().map(Object::toString).toList();
        assertThat(headers).as("A single Authorization header").hasSize(1);
        String header = headers.get(0);
        assertThat(header).startsWith("Bearer ");
        return header.substring("Bearer ".length());
    }

    protected JsonObject invoke(String client, String style, String caller, String id) {
        return result(request(caller).queryParam("id", id).post("/oidc-client/" + client + "/" + style));
    }

    protected JsonObject ingress(String ingress, String client, String style, String caller, String id) {
        if ("rest".equals(ingress)) {
            return invoke(client, style, caller, id);
        }
        String outcome = request(caller).contentType("text/xml").body("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body><r:relay xmlns:r="urn:quarkus-cxf:oidc:relay">
                    <client>%s</client><style>%s</style><operationId>%s</operationId>
                  </r:relay></s:Body>
                </s:Envelope>
                """.formatted(client, style, id)).post("/services/oidc-relay")
                .then().statusCode(200).extract().xmlPath().getString("Envelope.Body.relayResponse.return");
        return new JsonObject(outcome);
    }

    protected JsonObject fresh(String client, String caller, String id, boolean createOnly) {
        return result(request(caller).queryParam("id", id).queryParam("createOnly", createOnly)
                .post("/oidc-client/" + client + "/fresh"));
    }

    protected JsonObject identity(String client, String kind, String style, String rawCaller, String id,
            boolean fresh, boolean createOnly) {
        return result(request(rawCaller).queryParam("id", id).queryParam("credential", callerA)
                .queryParam("other", callerB).queryParam("fresh", fresh).queryParam("createOnly", createOnly)
                .post("/oidc-client/" + client + "/identity/" + kind + "/" + style));
    }

    protected static JsonObject result(io.restassured.response.Response response) {
        return new JsonObject(response.then().statusCode(200).extract().asString());
    }

    protected static RequestSpecification request(String caller) {
        RequestSpecification request = given().config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", 30000).setParam("http.socket.timeout", 30000)));
        return caller == null ? request : request.auth().oauth2(caller);
    }

    protected static String id(String scenario) {
        return scenario + "-" + UUID.randomUUID();
    }

    protected void success(JsonObject outcome, String id) {
        assertThat(outcome.getBoolean("attempted")).isTrue();
        assertThat(outcome.getBoolean("success")).as(outcome.getString("detail")).isTrue();
        assertThat(outcome.getString("detail")).isEqualTo(id);
    }

    protected void failureBeforeSoap(JsonObject outcome, String id) {
        assertThat(outcome.getBoolean("attempted")).as("Inbound security must let the test reach the CXF driver").isTrue();
        assertThat(outcome.getBoolean("success")).isFalse();
        assertThat(outcome.getString("detail")).isNotBlank();
        assertThat(posts(id)).as("Failure must precede SOAP dispatch").isEmpty();
    }

    protected void assertExchange(String id, String subject, String selectedClient) {
        String token = sentToken(id);
        assertThat(token).isNotEqualTo(subject);
        JsonObject grant = grantFor(token);
        assertThat(grant.getString("client")).isEqualTo(selectedClient);
        assertThat(grant.getBoolean("authenticated")).isTrue();
        assertThat(grant.getJsonObject("parameters").getString("grant_type")).isEqualTo(OidcTestResource.EXCHANGE_GRANT);
        assertThat(grant.getJsonObject("parameters").getString("subject_token")).isEqualTo(subject);
    }

    protected void awaitPending(String key) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(
                () -> assertThat(state().getJsonArray("pending").stream().map(Object::toString).toList()).contains(key));
    }

    protected void release(String key) {
        control("release", new JsonObject().put("key", key));
    }

    static Stream<Arguments> modesAndStyles() {
        return Stream.of("acquireNamed", "propagate", "exchangeNamed")
                .flatMap(client -> Stream.of("sync", "async").map(style -> Arguments.of(client, style)));
    }

    static Stream<Arguments> ingressAndStyles() {
        return Stream.of("rest", "soap")
                .flatMap(ingress -> Stream.of("sync", "async").map(style -> Arguments.of(ingress, style)));
    }
}
