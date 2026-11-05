package io.quarkiverse.cxf.it.auth.oidc.client;

import jakarta.inject.Inject;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;
import jakarta.ws.rs.core.Response;

import io.quarkus.security.Authenticated;

/** SOAP ingress exercises exactly the same shared injected proxies as REST ingress. */
@WebService(serviceName = "OidcRelayService", targetNamespace = "urn:quarkus-cxf:oidc:relay")
@Authenticated
public class OidcRelayService {

    @Inject
    OidcClients clients;

    @WebMethod
    public String relay(@WebParam(name = "client") String client, @WebParam(name = "style") String style,
            @WebParam(name = "operationId") String operationId) {
        try (Response response = OidcClientResource.invokeProxy(clients.shared(client), style, operationId)) {
            return (String) response.getEntity();
        }
    }
}
