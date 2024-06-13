package io.quarkiverse.cxf.deployment;

import java.util.List;

import io.quarkus.deployment.builditem.FeatureBuildItem;

public enum QuarkusCxfFeature {
    CXF("cxf"),
    CXF_RT_TRANSPORTS_HTTP_HC5("cxf-rt-transports-http-hc5"),
    CXF_VERX_WEB_CLIENT("cxf-verx-web-client");

    private final String key;

    private QuarkusCxfFeature(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public FeatureBuildItem asFeature() {
        return new FeatureBuildItem(this.key);
    }

    public static boolean otherCxfClientPresent(List<FeatureBuildItem> features) {
        return features.stream()
                .map(FeatureBuildItem::getName)
                .anyMatch(feature -> feature.equals(CXF_VERX_WEB_CLIENT.getKey())
                        || feature.equals(CXF_RT_TRANSPORTS_HTTP_HC5.getKey()));
    }

}
