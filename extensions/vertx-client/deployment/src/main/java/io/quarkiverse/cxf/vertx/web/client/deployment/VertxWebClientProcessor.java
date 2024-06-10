package io.quarkiverse.cxf.vertx.web.client.deployment;

import java.util.List;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class VertxWebClientProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("cxf-verx-web-client");
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void setHc5Present(io.quarkiverse.cxf.CXFRecorder recorder, List<FeatureBuildItem> features) {

        if (features.stream().map(FeatureBuildItem::getName).anyMatch("cxf-rt-transports-http-hc5"::equals)) {
            throw new IllegalStateException("Cannot combine io.quarkiverse.cxf:quarkus-cxf-vertx-client and io.quarkiverse.cxf:quarkus-cxf-rt-transports-http-hc5; choose only one of them");
        }
        recorder.setVertxWebClientPresent();
    }

}
