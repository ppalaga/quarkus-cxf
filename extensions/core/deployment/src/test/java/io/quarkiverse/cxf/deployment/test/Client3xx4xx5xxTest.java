package io.quarkiverse.cxf.deployment.test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;
import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class Client3xx4xx5xxTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(HelloService.class, HelloServiceImpl.class))

            /* Service */
            .overrideConfigKey("quarkus.cxf.endpoint.\"/hello\".implementor",
                    HelloServiceImpl.class.getName())
            .overrideConfigKey("quarkus.cxf.endpoint.\"/hello\".logging.enabled", "true")

            /* Clients */
            .overrideConfigKey("quarkus.cxf.client.wsdlUri200.client-endpoint-url", "http://localhost:8081/services/hello")
            .overrideConfigKey("quarkus.cxf.client.wsdlUri200.wsdl", "http://localhost:8081/services/hello?wsdl")
            // Not needed when the WSDL is set and HelloService has both serviceName and targetNamespace set
            //.overrideConfigKey("quarkus.cxf.client.wsdlUri404.service-interface", HelloService.class.getName())
            .overrideConfigKey("quarkus.cxf.client.wsdlUri200.logging.enabled", "true")

            /* Bad WSDL URI */
            .overrideConfigKey("quarkus.cxf.client.wsdlUri404.client-endpoint-url",
                    "http://localhost:8081/services/no-such-service")
            .overrideConfigKey("quarkus.cxf.client.wsdlUri404.wsdl", "http://localhost:8081/services/hello?wsdl")
            .overrideConfigKey("quarkus.cxf.client.wsdlUri404.logging.enabled", "true")

            .overrideConfigKey("quarkus.cxf.client.endpointUri404.client-endpoint-url",
                    "http://localhost:8081/services/no-such-service")
            .overrideConfigKey("quarkus.cxf.client.endpointUri404.service-interface", HelloService.class.getName())
            .overrideConfigKey("quarkus.cxf.client.endpointUri404.logging.enabled", "true")

    ;

    @CXFClient("wsdlUri200")
    HelloService wsdlUri200;

    @CXFClient("wsdlUri404")
    HelloService wsdlUri404;

    @CXFClient("endpointUri404")
    HelloService endpointUri404;

    @Inject
    Vertx vertx;

    @Test
    void wsdlUri200() {
        Assertions.assertThat(wsdlUri200.hello("foo")).isEqualTo("Hello foo");
    }

    @Test
    void wsdlUri200OnEventLoop() {
        vertx.runOnContext(event -> {});
    }

    @Test
    void wsdlUri404() {
        Assertions.assertThatThrownBy(() -> wsdlUri404.hello("foo")).hasRootCauseMessage(
                "HTTP response '404: Not Found' when communicating with http://localhost:8081/services/no-such-service");
    }

    @Test
    void endpointUri404() throws IOException, InterruptedException, ExecutionException {
        Assertions.assertThatThrownBy(() -> endpointUri404.hello("foo")).hasRootCauseMessage(
                "HTTP response '404: Not Found' when communicating with http://localhost:8081/services/no-such-service");
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
