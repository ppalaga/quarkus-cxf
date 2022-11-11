package io.quarkiverse.cxf.deployment;

/**
 * Holds a client endpoint metadata.
 */
public final class CxfClientBuildItem extends AbstractEndpointBuildItem {

    private final String sei;
    private final boolean proxyRuntimeInitialized;

    public CxfClientBuildItem(String sei, String soapBinding, String wsNamespace,
            String wsName, boolean proxyRuntimeInitialized) {
        super(soapBinding, wsNamespace, wsName);
        this.sei = sei;
        this.proxyRuntimeInitialized = proxyRuntimeInitialized;
    }

    public String getSei() {
        return sei;
    }

    public boolean isProxyRuntimeInitialized() {
        return proxyRuntimeInitialized;
    }
}
