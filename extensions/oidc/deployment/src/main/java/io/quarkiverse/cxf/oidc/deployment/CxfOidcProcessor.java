package io.quarkiverse.cxf.oidc.deployment;

import io.quarkiverse.cxf.oidc.QuarkusCxfOidcRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class CxfOidcProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("cxf-oidc");
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void protectLogs(QuarkusCxfOidcRecorder recorder) {
        recorder.init();
    }

}
