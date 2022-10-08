package io.quarkiverse.cxf.deployment;

import org.apache.cxf.Bus;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.RuntimeValue;

/**
 * Holds the fully configured runtime {@link Bus} instance. It is not foreseen that consumers will customize it further.
 */
public final class RuntimeCxfBusBuildItem extends SimpleBuildItem {
    private final RuntimeValue<Bus> bus;

    public RuntimeValue<Bus> getBus() {
        return bus;
    }

    public RuntimeCxfBusBuildItem(RuntimeValue<Bus> bus) {
        super();
        this.bus = bus;
    }

}