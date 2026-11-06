package io.quarkiverse.cxf.deployment.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkiverse.cxf.ClientCreationContext;
import io.quarkiverse.cxf.transport.VertxDestinationFactory;

public class ClientCreationContextTest {
    @Test
    @Tag("T-020")
    void concurrentWsdlCreationKeepsFactoriesSeparate() throws Exception {
        HTTPConduitFactory fallback = (transport, bus, endpoint, target) -> null;
        HTTPConduitFactory first = (transport, bus, endpoint, target) -> null;
        HTTPConduitFactory second = (transport, bus, endpoint, target) -> null;
        var bus = new ExtensionManagerBus(Map.of(HTTPConduitFactory.class, fallback), Map.of());
        var transport = new Transport();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var calls = java.util.List.of(first, second).stream().map(factory -> workers.submit(() -> {
                try (var ignored = new ClientCreationContext(factory, null)) {
                    ready.countDown();
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                    assertThat(transport.resolve(bus)).isSameAs(factory);
                    assertThat(bus.getExtension(HTTPConduitFactory.class)).isSameAs(fallback);
                }
                assertThat(bus.getExtension(HTTPConduitFactory.class)).isSameAs(fallback);
                assertThat(transport.resolve(bus)).isSameAs(fallback);
                assertThat(ClientCreationContext.current()).isNull();
                return null;
            })).toList();
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(bus.getExtension(HTTPConduitFactory.class)).isSameAs(fallback);
            } finally {
                release.countDown();
            }
            for (var call : calls) {
                call.get(10, TimeUnit.SECONDS);
            }
        } finally {
            bus.shutdown(true);
        }
    }

    @Test
    @Tag("T-020")
    void failedNestedCreationRestoresTheOuterScope() {
        HTTPConduitFactory outer = (transport, bus, endpoint, target) -> null;
        HTTPConduitFactory inner = (transport, bus, endpoint, target) -> null;
        var bus = new ExtensionManagerBus(Map.of(), Map.of());
        var transport = new Transport();
        try {
            try (var ignored = new ClientCreationContext(outer, null)) {
                assertThatThrownBy(() -> {
                    try (var nested = new ClientCreationContext(inner, null)) {
                        assertThat(transport.resolve(bus)).isSameAs(inner);
                        throw new IllegalStateException("WSDL unavailable");
                    }
                }).hasMessage("WSDL unavailable");
                assertThat(transport.resolve(bus)).isSameAs(outer);
            }
            assertThat(bus.getExtension(HTTPConduitFactory.class)).isNull();
            assertThat(ClientCreationContext.current()).isNull();
        } finally {
            bus.shutdown(true);
        }
    }

    static class Transport extends VertxDestinationFactory {
        HTTPConduitFactory resolve(Bus bus) {
            return findFactory(new EndpointInfo(), bus);
        }
    }
}
