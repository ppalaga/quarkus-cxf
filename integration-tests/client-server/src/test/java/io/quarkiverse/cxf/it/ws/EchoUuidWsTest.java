package io.quarkiverse.cxf.it.ws;

import java.io.IOException;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class EchoUuidWsTest {

    @CXFClient("echoUuidWs")
    EchoUuidWs echoUuidWs;

    @CXFClient("echoUuidWsVtx")
    EchoUuidWs echoUuidWsVtx;

    @Test
    void echoSoap11() throws IOException {
        String uuid = UUID.randomUUID().toString();
        Assertions.assertThat(echoUuidWs.echoUuid(uuid)).isEqualTo(uuid);

        //        String wsdl = RestAssured.get("/soap/echo-uuid-ws/soap-1.1?wsdl")
        //                .then()
        //                .statusCode(200)
        //                .extract().body().asString();
        //        Files.write(Path.of("/home/ppalaga/orgs/cxf/echo-uuid-ws/src/main/resources/echoUuidWs.wsdl"),
        //                wsdl.getBytes(StandardCharsets.UTF_8));
        //
        //        String wsdl2 = RestAssured.get("/echo-uuid-ws/soap-1.1?wsdl")
        //                .then()
        //                .statusCode(200)
        //                .extract().body().asString();
        //Assertions.assertThat(wsdl.trim()).isEqualTo(wsdl2.trim());

        Assertions.assertThat(echoUuidWsVtx.echoUuid(uuid)).isEqualTo(uuid);
    }

    @Test
    void echoSoap11Vtx() throws IOException {

        /* Ensure the WSDL is served */
        RestAssured.get("/echo-uuid-ws/soap-1.1?wsdl")
                .then()
                .statusCode(200)
                .body(
                        Matchers.containsString("""
                                <xs:element minOccurs="0" name="uuid" type="xs:string"/>
                                """),
                        Matchers.containsString("""
                                <wsdl:operation name="echoUuid">
                                """));

        RestAssured.get("/echo-uuid-ws/soap-1.1")
                .then()
                .statusCode(404);

        final String uuid = UUID.randomUUID().toString();
        /* Ensure the service works */
        RestAssured.given()
                .contentType("text/xml")
                .accept("*/*")
                .header("Connection", "Keep-Alive")
                .body(
                        String.format(
                                """
                                        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><ns2:echoUuid xmlns:ns2="http://l2x6.org/echo-uuid-ws/"><uuid>%s</uuid></ns2:echoUuid></soap:Body></soap:Envelope>
                                        """,
                                uuid))
                .post("/echo-uuid-ws/soap-1.1")
                .then()
                .statusCode(200)
                .contentType("text/xml")
                .body(
                        Matchers.is(String.format(
                                """
                                        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><ns2:echoUuidResponse xmlns:ns2="http://l2x6.org/echo-uuid-ws/"><return>%s</return></ns2:echoUuidResponse></soap:Body></soap:Envelope>
                                        """,
                                uuid).trim()));

    }
}
