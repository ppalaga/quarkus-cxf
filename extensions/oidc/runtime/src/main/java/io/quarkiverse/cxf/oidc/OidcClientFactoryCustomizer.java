package io.quarkiverse.cxf.oidc;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;

import io.quarkiverse.cxf.CXFClientInfo;
import io.quarkiverse.cxf.CxfClientProducer;

@ApplicationScoped
public class OidcClientFactoryCustomizer implements CxfClientProducer.ClientFactoryCustomizer {

    @Override
    public void customize(CXFClientInfo info, JaxWsProxyFactoryBean factory) {

    }
}
