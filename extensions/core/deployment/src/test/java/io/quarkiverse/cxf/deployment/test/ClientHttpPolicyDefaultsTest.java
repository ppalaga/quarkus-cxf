package io.quarkiverse.cxf.deployment.test;

import javax.inject.Inject;
import javax.jws.WebMethod;
import javax.jws.WebService;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.assertj.core.api.Assertions;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkus.test.QuarkusUnitTest;

public class ClientHttpPolicyDefaultsTest {

    private static final String CUSTOM_BINDING = "http://www.w3.org/2003/05/soap/bindings/HTTP/";

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(HelloService.class, SlowHelloServiceImpl.class))
            .overrideConfigKey("quarkus.cxf.endpoint.\"/hello\".implementor",
                    SlowHelloServiceImpl.class.getName())
            .overrideConfigKey("quarkus.cxf.client.hello.client-endpoint-url", "http://localhost:8081/services/hello")
            .overrideConfigKey("quarkus.cxf.client.hello.soap-binding", CUSTOM_BINDING)
            .overrideConfigKey("quarkus.cxf.client.hello.service-interface", HelloService.class.getName());

    @CXFClient
    HelloService helloService;

    @Inject
    Logger logger;

    @Test
    void soapBindingOverride() {
        final Client client = ClientProxy.getClient(helloService);
        Assertions.assertThat(helloService.hello("Joe")).isEqualTo("Hello Joe");
        Assertions.assertThat(client.getEndpoint().getBinding().getBindingInfo().getBindingId()).isEqualTo(CUSTOM_BINDING);
    }

    @WebService
    public interface HelloService {

        @WebMethod
        String hello(String person);

    }

    @WebService(endpointInterface = "io.quarkiverse.cxf.deployment.test.ClientHttpPolicyDefaultsTest$HelloService", serviceName = "HelloService")
    public static class SlowHelloServiceImpl implements HelloService {

        @Override
        public String hello(String person) {
            return "Hello " + person;
        }
    }

}
