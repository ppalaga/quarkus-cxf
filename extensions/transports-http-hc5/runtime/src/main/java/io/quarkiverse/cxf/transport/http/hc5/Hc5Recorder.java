package io.quarkiverse.cxf.transport.http.hc5;

import java.util.function.Consumer;

import org.apache.cxf.Bus;
import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.apache.cxf.workqueue.WorkQueueManager;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class Hc5Recorder {

    /**
     * Customize the runtime {@link Bus} by adding a custom work queue for {@code http-conduit} and a custom
     * {@link HTTPConduitFactory}. Both are there to enable context propagation for async clients.
     *
     * @return a new {@link RuntimeValue} holding a {@link Consumer} to customize the runtime {@link Bus}
     */
    public RuntimeValue<Consumer<Bus>> customizeBus() {

        return new RuntimeValue<>(bus -> {
            final WorkQueueManager wqm = bus.getExtension(WorkQueueManager.class);
            wqm.addNamedWorkQueue("http-conduit", new QuarkusWorkQueueImpl("http-conduit"));

            bus.setExtension(new QuarkusAsyncHTTPConduitFactory(bus), HTTPConduitFactory.class);
        });
    }
}
