package io.quarkiverse.cxf.metrics.client.it;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.eap.quickstarts.wscalculator.calculator.AddResponse;
import org.jboss.eap.quickstarts.wscalculator.calculator.CalculatorService;
import org.jboss.logging.Logger;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkiverse.cxf.mutiny.CxfMutinyUtils;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.vertx.core.impl.ConcurrentHashSet;

@Path("/ClientStressResource")
public class ClientStressResource {
    @Inject
    Logger log;

    @Inject
    @CXFClient("vertxCalculator")
    CalculatorService vertxCalculator;

    @Path("/range")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Uni<Long> range(@QueryParam("requestCount") int requestCount) {

        final Set<String> threads = new ConcurrentHashSet<>();

        return Multi.createFrom().<Integer> emitter(emitter -> {
            for (int i = 0; i < requestCount; i++) {
                emitter.emit(i);
            }
            emitter.complete();
        }, BackPressureStrategy.IGNORE)

                .onItem()
                .transformToUniAndMerge(i -> {
                    //log.info("==== i = " + i);
                    final long start = System.currentTimeMillis();
                    return CxfMutinyUtils
                            .<AddResponse> toUni(handler -> vertxCalculator.addAsync(i, i, handler))
                            .map(r -> {
                                threads.add(Thread.currentThread().getName());
                                log.info("==== i finished " + i + " in " + (System.currentTimeMillis() - start) + " ms");
                                return r.getReturn();
                            });
                })
                .collect().with(Collectors.counting())
                .map(l -> {
                    log.info("=== threads " + threads.size() + ": " + new TreeSet<>(threads));
                    return l;
                });
    }

}
