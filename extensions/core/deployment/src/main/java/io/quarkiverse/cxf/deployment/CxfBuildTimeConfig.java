package io.quarkiverse.cxf.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "cxf", phase = ConfigPhase.BUILD_TIME)
public class CxfBuildTimeConfig {
    /**
     * The default path for CXF resources
     */
    @ConfigItem(defaultValue = "/")
    String path;

    /**
     * The comma-separated list of WSDL resource paths used by CXF
     */
    @ConfigItem
    Optional<List<String>> wsdlPath;
}
