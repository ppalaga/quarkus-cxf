package io.quarkiverse.cxf.perf.uuid.client;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkiverse.cxf.mutiny.CxfMutinyUtils;
import io.quarkiverse.cxf.perf.uuid.client.generated.EchoUuidResponse;
import io.quarkiverse.cxf.perf.uuid.client.generated.EchoUuidWs;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.vertx.core.impl.ConcurrentHashSet;

@Path("/clients")
public class EchoUuidClientResource {
    @Inject
    Logger log;

    @CXFClient("echoUuidWsVertx")
    EchoUuidWs echoUuidWsVertx;

    @CXFClient("echoUuidWsUrlConnection")
    EchoUuidWs echoUuidWsUrlConnection;

    @POST
    @Path("/echoUuidWsUrlConnection/sync")
    @Produces(MediaType.TEXT_PLAIN)
    public String echoUuidWsUrlConnectionSync(String uuid) throws IOException {
        return echoUuidWsUrlConnection.echoUuid(uuid);
    }

    @POST
    @Path("/echoUuidWsVertx/sync")
    @Produces(MediaType.TEXT_PLAIN)
    public String echoUuidWsSync(String uuid) throws IOException {
        return echoUuidWsVertx.echoUuid(uuid);
    }

    @POST
    @Path("/echoUuidWsVertx/async")
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<String> echoUuidWsAsync(String uuid) throws IOException {
        return CxfMutinyUtils
                .<EchoUuidResponse> toUni(handler -> echoUuidWsVertx.echoUuidAsync(uuid, handler))
                .map(EchoUuidResponse::getReturn);
    }

    @POST
    @Path("/echoUuidWsVertx/async/bulk")
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<Long> echoUuidWsAsyncBulk(String rawRequestCount) {

        final int requestCount = Integer.parseInt(rawRequestCount);
        final Set<String> threads = new ConcurrentHashSet<>();
        final long start = System.currentTimeMillis();

        return Multi.createFrom().<Integer> emitter(emitter -> {
            for (int i = 0; i < requestCount; i++) {
                emitter.emit(i);
            }
            emitter.complete();
        }, BackPressureStrategy.BUFFER)
                .emitOn(Infrastructure.getDefaultExecutor())
                .onItem()
                .transformToUniAndMerge(i -> {
                    //log.info("==== i = " + i);
                    //final long start = System.currentTimeMillis();
                    return CxfMutinyUtils
                            .<EchoUuidResponse> toUni(
                                    handler -> echoUuidWsVertx.echoUuidAsync(UUID.randomUUID().toString(), handler))
                            .map(r -> {
                                threads.add(Thread.currentThread().getName());
                                //log.info("==== i finished " + i + " in " + (System.currentTimeMillis() - start) + " ms");
                                return r.getReturn();
                            });
                })
                .collect().with(Collectors.counting())
                .map(l -> {
                    log.info("==== Time " + (System.currentTimeMillis() - start) + " ms");
                    log.info("=== threads " + threads.size() + ": " + new TreeSet<>(threads));
                    return l;
                });
    }
}
