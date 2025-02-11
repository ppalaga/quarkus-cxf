package io.quarkiverse.cxf.it.vertx.async;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkiverse.cxf.deployment.test.HelloResponse;
import io.quarkiverse.cxf.deployment.test.HelloService;
import io.quarkiverse.cxf.mutiny.CxfMutinyUtils;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;

@Path("/RestAsyncWithoutWsdlWithBlocking")
public class RestAsyncWithoutWsdlWithBlocking {

    @CXFClient("helloWithoutWsdlWithBlocking")
    HelloService helloWithoutWsdlWithBlocking;

    @Path("/helloWithoutWsdlWithBlocking")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public Uni<String> helloWithoutWsdlWithBlocking(String person) {
        /* Without WSDL and with @Blocking should work */
        return CxfMutinyUtils
                .<HelloResponse> toUni(handler -> helloWithoutWsdlWithBlocking.helloAsync(person, handler))
                .map(HelloResponse::getReturn);
    }

}
