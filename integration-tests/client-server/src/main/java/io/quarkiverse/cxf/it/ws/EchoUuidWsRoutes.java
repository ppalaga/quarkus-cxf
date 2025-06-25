package io.quarkiverse.cxf.it.ws;

import java.nio.charset.StandardCharsets;

import io.netty.util.AsciiString;
import io.quarkus.vertx.web.Body;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class EchoUuidWsRoutes {

    @Route(path = "/echo-uuid-ws/soap-1.1", methods = HttpMethod.POST, produces = "text/xml")
    void hello(@Body String body, RoutingContext context) {
        /*
         * The request
         * <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><ns2:echoUuid
         * xmlns:ns2="http://l2x6.org/echo-uuid-ws/"><uuid>85f02a6a-063c-4d09-97ea-ff2d514fd7f7</uuid></ns2:echoUuid></soap:Body
         * ></soap:Envelope>
         */
        final String uuid = getUuid(body);
        final HttpServerResponse response = context.response();
        final String respBody = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ns2:echoUuidResponse xmlns:ns2=\"http://l2x6.org/echo-uuid-ws/\"><return>"
                + uuid + "</return></ns2:echoUuidResponse></soap:Body></soap:Envelope>";
        response.setStatusCode(200).end(respBody);
    }

    private static final CharSequence wsdl = new AsciiString("wsdl".getBytes(StandardCharsets.US_ASCII));

    @Route(path = "/echo-uuid-ws/soap-1.1", methods = HttpMethod.GET, produces = "text/xml")
    void helloWsdl(RoutingContext context) {
        final HttpServerResponse response = context.response();
        if (!context.queryParams().contains(wsdl)) {
            response.setStatusCode(404).end();
            return;
        }
        final String respBody = """
                <?xml version='1.0' encoding='UTF-8'?><wsdl:definitions xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:tns="http://l2x6.org/echo-uuid-ws/" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:ns1="http://schemas.xmlsoap.org/soap/http" name="EchoUuidWs" targetNamespace="http://l2x6.org/echo-uuid-ws/">
                  <wsdl:types>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:tns="http://l2x6.org/echo-uuid-ws/" elementFormDefault="unqualified" targetNamespace="http://l2x6.org/echo-uuid-ws/" version="1.0">

                  <xs:element name="echoUuid" type="tns:echoUuid"/>

                  <xs:element name="echoUuidResponse" type="tns:echoUuidResponse"/>

                  <xs:complexType name="echoUuid">
                    <xs:sequence>
                      <xs:element minOccurs="0" name="uuid" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>

                  <xs:complexType name="echoUuidResponse">
                    <xs:sequence>
                      <xs:element minOccurs="0" name="return" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>

                </xs:schema>
                  </wsdl:types>
                  <wsdl:message name="echoUuid">
                    <wsdl:part element="tns:echoUuid" name="parameters">
                    </wsdl:part>
                  </wsdl:message>
                  <wsdl:message name="echoUuidResponse">
                    <wsdl:part element="tns:echoUuidResponse" name="parameters">
                    </wsdl:part>
                  </wsdl:message>
                  <wsdl:portType name="EchoUuidWs">
                    <wsdl:operation name="echoUuid">
                      <wsdl:input message="tns:echoUuid" name="echoUuid">
                    </wsdl:input>
                      <wsdl:output message="tns:echoUuidResponse" name="echoUuidResponse">
                    </wsdl:output>
                    </wsdl:operation>
                  </wsdl:portType>
                  <wsdl:binding name="EchoUuidWsSoapBinding" type="tns:EchoUuidWs">
                    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
                    <wsdl:operation name="echoUuid">
                      <soap:operation soapAction="" style="document"/>
                      <wsdl:input name="echoUuid">
                        <soap:body use="literal"/>
                      </wsdl:input>
                      <wsdl:output name="echoUuidResponse">
                        <soap:body use="literal"/>
                      </wsdl:output>
                    </wsdl:operation>
                  </wsdl:binding>
                  <wsdl:service name="EchoUuidWs">
                    <wsdl:port binding="tns:EchoUuidWsSoapBinding" name="EchoUuidWsImplPort">
                      <soap:address location="http://localhost:8081/soap/echo-uuid-ws/soap-1.1"/>
                    </wsdl:port>
                  </wsdl:service>
                </wsdl:definitions>
                                        """;
        response.setStatusCode(200).end(respBody);
    }

    static String getUuid(String body) {
        return body.substring(143, 144 + 35);
    }

}
