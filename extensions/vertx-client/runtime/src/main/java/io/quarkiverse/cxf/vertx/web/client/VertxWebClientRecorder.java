package io.quarkiverse.cxf.vertx.web.client;

import java.util.function.Consumer;

import org.apache.cxf.Bus;
import org.apache.cxf.transport.http.HTTPConduitFactory;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class VertxWebClientRecorder {

    /**
     * Customize the runtime {@link Bus} by adding a custom work queue for {@code http-conduit} and a custom
     * {@link HTTPConduitFactory}. Both are there to enable context propagation for async clients.
     *
     * @return a new {@link RuntimeValue} holding a {@link Consumer} to customize the runtime {@link Bus}
     */
    public RuntimeValue<Consumer<Bus>> customizeBus() {

        return new RuntimeValue<>(bus -> {
            bus.setExtension(new VertxWebClientHTTPConduitFactory(), HTTPConduitFactory.class);
        });
    }
}
