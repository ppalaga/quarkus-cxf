package io.quarkiverse.cxf.it.security.policy;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.cxf.annotation.CXFClient;

@Path("/cxf/security-policy")
public class SecurityPolicyResource {

    @Inject
    @CXFClient("helloCustomEncryptSign")
    CustomEncryptSignPolicyHelloService helloCustomEncryptSign;

    @Inject
    @CXFClient("helloCustomEncryptSignWrong01")
    CustomEncryptSignPolicyHelloService helloCustomEncryptSignWrong01;

    @Inject
    @CXFClient("helloCustomEncryptSignWrong02")
    CustomEncryptSignPolicyHelloService helloCustomEncryptSignWrong02;

    @Inject
    @CXFClient("helloCustomizedEncryptSign")
    CustomEncryptSignPolicyHelloService helloCustomizedEncryptSign;

    @POST
    @Path("/{client}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response hello(@PathParam("client") String client, String body) {
        final AbstractHelloService service;
        switch (client) {
            case "helloCustomizedEncryptSign":
                service = helloCustomizedEncryptSign;
                break;
            case "helloCustomEncryptSign":
                service = helloCustomEncryptSign;
                break;
            case "helloCustomEncryptSignWrong01":
                service = helloCustomEncryptSignWrong01;
                break;
            case "helloCustomEncryptSignWrong02":
                service = helloCustomEncryptSignWrong02;
                break;
            default:
                throw new IllegalStateException("Unexpected client " + client);
        }
        try {
            return Response.ok(service.hello(body)).build();
        } catch (Exception e) {
            final StringWriter w = new StringWriter();
            final PrintWriter pw = new PrintWriter(w);
            e.printStackTrace(pw);
            return Response.status(500).entity(w.toString()).build();
        }
    }
}
