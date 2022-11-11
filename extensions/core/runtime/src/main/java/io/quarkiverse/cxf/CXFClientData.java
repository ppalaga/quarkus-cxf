package io.quarkiverse.cxf;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Provides runtime metadata for a CXF client.
 *
 * <p>
 * This class contains extracted from a SEI. It contains basic data to
 * setup a proxy client for a given SEI.
 * </p>
 */
public class CXFClientData implements Serializable {
    private List<String> classNames;
    private String soapBinding;
    private String sei;
    private String wsName;
    private String wsNamespace;
    private boolean proxyRuntimeInitialized;

    public CXFClientData() {
    }

    public CXFClientData(
            String soapBinding,
            String sei,
            String wsName,
            String wsNamespace,
            boolean proxyRuntimeInitialized,
            List<String> classNames) {
        this.soapBinding = soapBinding;
        this.sei = sei;
        this.wsName = wsName;
        this.wsNamespace = wsNamespace;
        this.proxyRuntimeInitialized = proxyRuntimeInitialized;
        this.classNames = Collections.unmodifiableList(classNames);
    }

    public List<String> getClassNames() {
        return classNames;
    }

    public String getSoapBinding() {
        return soapBinding;
    }

    public String getSei() {
        return sei;
    }

    public String getWsName() {
        return wsName;
    }

    public String getWsNamespace() {
        return wsNamespace;
    }

    public boolean isProxyRuntimeInitialized() {
        return proxyRuntimeInitialized;
    }

    public void setClassNames(List<String> classNames) {
        this.classNames = Collections.unmodifiableList(classNames);
    }

    public void setSoapBinding(String soapBinding) {
        this.soapBinding = soapBinding;
    }

    public void setSei(String sei) {
        this.sei = sei;
    }

    public void setWsName(String wsName) {
        this.wsName = wsName;
    }

    public void setWsNamespace(String wsNamespace) {
        this.wsNamespace = wsNamespace;
    }

    public void setProxyRuntimeInitialized(boolean proxyRuntimeInitialized) {
        this.proxyRuntimeInitialized = proxyRuntimeInitialized;
    }
}
