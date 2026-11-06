package io.quarkiverse.cxf.oidc.deployment;

import io.quarkiverse.cxf.oidc.OidcClientFactoryCustomizer;
import io.quarkiverse.cxf.oidc.OidcCredentialResolver;
import io.quarkiverse.cxf.oidc.OidcEndpointFilter;
import io.quarkiverse.cxf.oidc.OidcRecorder;
import io.quarkiverse.cxf.oidc.OidcTokenProviders;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

public class CxfOidcProcessor {
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void protectLogs(OidcRecorder recorder, ShutdownContextBuildItem shutdown, LoggingSetupBuildItem logging) {
        recorder.protectTokenRequestLogs(shutdown);
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("cxf-oidc");
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder().setUnremovable().addBeanClasses(OidcClientFactoryCustomizer.class,
                OidcCredentialResolver.class, OidcTokenProviders.class, OidcEndpointFilter.class).build();
    }
}
