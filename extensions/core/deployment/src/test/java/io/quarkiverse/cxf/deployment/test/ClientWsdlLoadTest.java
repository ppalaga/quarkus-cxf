package io.quarkiverse.cxf.deployment.test;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.apache.cxf.service.factory.ServiceConstructionException;
import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.cxf.FailureRemedy;
import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public abstract class ClientWsdlLoadTest {

    static QuarkusUnitTest createApp(FailureRemedy remedy, int wsdlReponseCode) {
        return new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addClasses(HelloService.class, HelloServiceImpl.class))

                .overrideConfigKey("quarkus.cxf.client.wsdl.on-load-failure", remedy.name())
                //.overrideConfigKey("quarkus.cxf.http-conduit-factory", "HttpClientHTTPConduitFactory")

                /* Service */
                .overrideConfigKey("quarkus.cxf.endpoint.\"/hello\".implementor",
                        HelloServiceImpl.class.getName())
                .overrideConfigKey("quarkus.cxf.endpoint.\"/hello\".logging.enabled", "true")

                /* Client */
                .overrideConfigKey("quarkus.cxf.client.hello.client-endpoint-url", "http://localhost:8081/services/hello")
                .overrideConfigKey("quarkus.cxf.client.hello.wsdl", "http://localhost:8081/vertx/wsdl/" + wsdlReponseCode)
                // Not needed when the WSDL is set and HelloService has both serviceName and targetNamespace set
                .overrideConfigKey("quarkus.cxf.client.hello.service-interface", HelloService.class.getName())
                .overrideConfigKey("quarkus.cxf.client.hello.logging.enabled", "true");
    }

    private static final String WSDL = """
            <?xml version='1.0' encoding='UTF-8'?><wsdl:definitions xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:tns="http://test.deployment.cxf.quarkiverse.io/" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:ns1="http://schemas.xmlsoap.org/soap/http" name="HelloService" targetNamespace="http://test.deployment.cxf.quarkiverse.io/">
              <wsdl:types>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:tns="http://test.deployment.cxf.quarkiverse.io/" elementFormDefault="unqualified" targetNamespace="http://test.deployment.cxf.quarkiverse.io/" version="1.0">
              <xs:element name="hello" type="tns:hello"/>
              <xs:element name="helloResponse" type="tns:helloResponse"/>
              <xs:complexType name="hello">
                <xs:sequence>
                  <xs:element minOccurs="0" name="arg0" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
              <xs:complexType name="helloResponse">
                <xs:sequence>
                  <xs:element minOccurs="0" name="return" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
              </wsdl:types>
              <wsdl:message name="helloResponse">
                <wsdl:part element="tns:helloResponse" name="parameters">
                </wsdl:part>
              </wsdl:message>
              <wsdl:message name="hello">
                <wsdl:part element="tns:hello" name="parameters">
                </wsdl:part>
              </wsdl:message>
              <wsdl:portType name="HelloService">
                <wsdl:operation name="hello">
                  <wsdl:input message="tns:hello" name="hello">
                </wsdl:input>
                  <wsdl:output message="tns:helloResponse" name="helloResponse">
                </wsdl:output>
                </wsdl:operation>
              </wsdl:portType>
              <wsdl:binding name="HelloServiceSoapBinding" type="tns:HelloService">
                <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
                <wsdl:operation name="hello">
                  <soap:operation soapAction="" style="document"/>
                  <wsdl:input name="hello">
                    <soap:body use="literal"/>
                  </wsdl:input>
                  <wsdl:output name="helloResponse">
                    <soap:body use="literal"/>
                  </wsdl:output>
                </wsdl:operation>
              </wsdl:binding>
              <wsdl:service name="HelloService">
                <wsdl:port binding="tns:HelloServiceSoapBinding" name="HelloServiceImplPort">
                  <soap:address location="http://localhost:8081/services/hello"/>
                </wsdl:port>
              </wsdl:service>
            </wsdl:definitions>
                        """;

    @CXFClient("hello")
    // Use Instance to avoid greedy initialization
    Instance<HelloService> hello;

    void init(@Observes Router router) {
        router.route().handler(BodyHandler.create());
        router.get("/vertx/wsdl/:responseCode").blockingHandler(ctx -> {
            final int code = Integer.parseInt(ctx.pathParam("responseCode"));
            final HttpServerResponse resp = ctx.response().setStatusCode(code);
            System.out.println("wsdl returning " + code);
            if (code == 200) {
                resp.end(WSDL);
            } else {
                resp.end();
            }
        });
    }

    @Nested
    public static class ClientWsdlLoadFail500Test extends ClientWsdlLoadTest {
        @RegisterExtension
        public static final QuarkusUnitTest test = createApp(FailureRemedy.fail, 500);

        @Test
        void fail() {
            Assertions.assertThatThrownBy(() -> hello.get())
                    .isInstanceOf(ServiceConstructionException.class)
                    .hasMessage("Failed to create service.")
                    .hasCauseInstanceOf(javax.wsdl.WSDLException.class);
        }
    }

    @Nested
    public static class ClientWsdlLoadFail404Test extends ClientWsdlLoadTest {
        @RegisterExtension
        public static final QuarkusUnitTest test = createApp(FailureRemedy.fail, 404);

        @Test
        void fail() {
            Assertions.assertThatThrownBy(() -> hello.get())
                    .isInstanceOf(ServiceConstructionException.class)
                    .hasMessage("Failed to create service.")
                    .hasCauseInstanceOf(javax.wsdl.WSDLException.class);
        }
    }

    @Nested
    public static class ClientWsdlLoadWarnTest extends ClientWsdlLoadTest {
        @RegisterExtension
        public static final QuarkusUnitTest test = createApp(FailureRemedy.warn, 404);

        @Test
        void warn() {
            HelloService cl = hello.get();
            System.out.println("==== cl " + cl);
            Assertions.assertThat(cl).isNotNull();
            //Assertions.assertThat(hello.get().hello("foo")).isEqualTo("Hello foo");
        }
    }

    @Nested
    public static class ClientWsdlLoadPassTest extends ClientWsdlLoadTest {
        @RegisterExtension
        public static final QuarkusUnitTest test = createApp(FailureRemedy.fail, 200);

        @Test
        void pass() {
            Assertions.assertThat(hello.get().hello("foo")).isEqualTo("Hello foo");
        }
    }

    @WebService(serviceName = "HelloService", targetNamespace = "http://test.deployment.cxf.quarkiverse.io/")
    public interface HelloService {

        @WebMethod
        String hello(String person);

    }

    @WebService(serviceName = "HelloService")
    public static class HelloServiceImpl implements HelloService {

        @Override
        public String hello(String person) {
            return "Hello " + person;
        }
    }

}
