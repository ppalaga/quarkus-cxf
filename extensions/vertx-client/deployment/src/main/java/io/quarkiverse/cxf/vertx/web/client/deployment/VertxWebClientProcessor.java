package io.quarkiverse.cxf.vertx.web.client.deployment;

import java.util.List;

import io.quarkiverse.cxf.deployment.QuarkusCxfFeature;
import io.quarkiverse.cxf.deployment.RuntimeBusCustomizerBuildItem;
import io.quarkiverse.cxf.vertx.web.client.VertxWebClientRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class VertxWebClientProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return QuarkusCxfFeature.CXF_VERX_WEB_CLIENT.asFeature();
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void setHc5Present(io.quarkiverse.cxf.CXFRecorder recorder, List<FeatureBuildItem> features) {

        if (features.stream().map(FeatureBuildItem::getName)
                .anyMatch(QuarkusCxfFeature.CXF_RT_TRANSPORTS_HTTP_HC5.name()::equals)) {
            throw new IllegalStateException(
                    "Cannot combine io.quarkiverse.cxf:quarkus-cxf-vertx-client and io.quarkiverse.cxf:quarkus-cxf-rt-transports-http-hc5; choose only one of them");
        }
        recorder.setVertxWebClientPresent();
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void customizers(
            VertxWebClientRecorder recorder,
            BuildProducer<RuntimeBusCustomizerBuildItem> customizers) {
        customizers.produce(new RuntimeBusCustomizerBuildItem(recorder.customizeBus()));
    }

}
