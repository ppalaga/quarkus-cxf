package io.quarkiverse.cxf.it.auth.oidc.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.ws.BindingProvider;

import org.apache.cxf.frontend.ClientProxy;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.cxf.it.auth.oidc.client.model.EchoResponse;
import io.quarkiverse.cxf.it.auth.oidc.client.model.OidcEcho;
import io.quarkiverse.cxf.mutiny.CxfMutinyUtils;
import io.quarkus.arc.Arc;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/** HTTP driver for JVM and native tests; authentication is performed by the real Quarkus OIDC extension. */
@Path("/oidc-client")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class OidcClientResource {

    @Inject
    OidcClients clients;

    @Inject
    CurrentIdentityAssociation identity;

    @ConfigProperty(name = "oidc.fixture.http")
    String fixtureUrl;

    // Deliberately unmanaged: T-006 must not inherit a request or security context.
    private final ExecutorService contextless = Executors.newCachedThreadPool();

    @GET
    @Path("/fixture")
    public String fixture() {
        return new JsonObject().put("url", fixtureUrl).encode();
    }

    @POST
    @Blocking
    @Path("/{client}/{style}")
    public Response invoke(@PathParam("client") String client, @PathParam("style") String style,
            @QueryParam("id") String id) {
        return execute(client, style, id, false, false);
    }

    @POST
    @NonBlocking
    @Path("/{client}/event-loop")
    public Uni<Response> eventLoop(@PathParam("client") String client, @QueryParam("id") String id) {
        boolean eventLoop = Vertx.currentContext() != null && Vertx.currentContext().isEventLoopContext();
        if (!eventLoop) {
            return Uni.createFrom().item(outcome(false, "The fixture did not enter on the event loop", false, true));
        }
        try {
            OidcEcho proxy = clients.shared(client);
            return CxfMutinyUtils.<EchoResponse> toUni(handler -> proxy.echoAsync(id, handler))
                    .map(response -> outcome(true, response.getReturn(), true, true))
                    .onFailure().recoverWithItem(error -> outcome(false, causes(error), true, true));
        } catch (Exception error) {
            return Uni.createFrom().item(outcome(false, causes(error), true, true));
        }
    }

    @POST
    @Blocking
    @Path("/{client}/identity/{kind}/{style}")
    public Response syntheticIdentity(@PathParam("client") String client, @PathParam("kind") String kind,
            @PathParam("style") String style, @QueryParam("id") String id,
            @QueryParam("credential") String credential, @QueryParam("other") String other,
            @QueryParam("fresh") @DefaultValue("false") boolean fresh,
            @QueryParam("createOnly") @DefaultValue("false") boolean createOnly) throws Exception {
        if ("contextless".equals(kind)) {
            // Resolve the shared dependent proxy while the request is still active, then invoke it on a plain executor.
            // Fresh WSDL clients must instead be created inside the contextless task itself.
            OidcEcho proxy = fresh ? null : clients.shared(client);
            return CompletableFuture.supplyAsync(() -> {
                if (Arc.container().requestContext().isActive()) {
                    throw new IllegalStateException("The contextless fixture inherited a request context");
                }
                return fresh ? execute(client, style, id, true, createOnly) : invokeProxy(proxy, style, id);
            }, contextless).get(20, TimeUnit.SECONDS);
        }
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "synthetic-caller").setAnonymous("anonymous".equals(kind));
        if ("single".equals(kind) || "ambiguous".equals(kind)) {
            builder.addCredential(new AccessTokenCredential(credential));
        }
        if ("ambiguous".equals(kind)) {
            builder.addCredential(new AccessTokenCredential(other));
        }
        identity.setIdentity(builder.build());
        return execute(client, style, id, fresh, createOnly);
    }

    @POST
    @Blocking
    @Path("/{client}/fresh")
    public Response fresh(@PathParam("client") String client, @QueryParam("id") String id,
            @QueryParam("createOnly") @DefaultValue("false") boolean createOnly) {
        return execute(client, "sync", id, true, createOnly);
    }

    private Response execute(String name, String style, String id, boolean fresh, boolean createOnly) {
        OidcEcho proxy = null;
        try {
            proxy = fresh ? clients.fresh(name) : clients.shared(name);
            if (createOnly) {
                return outcome(true, ClientProxy.getClient(proxy).getConduit().getClass().getName(), false, true);
            }
            return invokeProxy(proxy, style, id);
        } catch (Exception error) {
            return outcome(false, causes(error), false, true);
        } finally {
            if (fresh && proxy != null) {
                clients.destroy(name, proxy);
            }
        }
    }

    static Response invokeProxy(OidcEcho proxy, String style, String id) {
        try {
            // Allow the destination to be reached so redirect tests measure header forwarding, not a host allow-list.
            ((BindingProvider) proxy).getRequestContext().put("http.redirect.same.host.only", false);
            String result;
            if ("async".equals(style)) {
                CompletableFuture<String> completion = new CompletableFuture<>();
                proxy.echoAsync(id, response -> {
                    try {
                        completion.complete(response.get().getReturn());
                    } catch (Exception error) {
                        completion.completeExceptionally(error);
                    }
                });
                result = completion.get(20, TimeUnit.SECONDS);
            } else {
                result = proxy.echo(id);
            }
            return outcome(true, result, false, true);
        } catch (TimeoutException error) {
            // A test-harness timeout is not a successful assertion of the product's authentication failure contract.
            throw new AssertionError("The async SOAP callback did not complete within the test harness bound", error);
        } catch (Exception error) {
            return outcome(false, causes(error), false, true);
        }
    }

    private static Response outcome(boolean success, String detail, boolean eventLoop, boolean attempted) {
        return Response.ok(new JsonObject().put("success", success).put("detail", detail)
                .put("eventLoop", eventLoop).put("attempted", attempted).encode()).build();
    }

    static String causes(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            result.append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append('\n');
        }
        return result.toString();
    }

    @PreDestroy
    void stop() {
        contextless.shutdownNow();
    }
}
