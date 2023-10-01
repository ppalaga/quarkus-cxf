package io.quarkiverse.cxf.it.security.policy;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.apache.cxf.annotations.Policy;

/**
 * A service implementation with a transport policy set
 */
@WebService
@Policy(placement = Policy.Placement.BINDING, uri = "https-policy.xml")
public class HttpsPolicyHelloServiceImpl implements HttpsPolicyHelloService {

    @WebMethod
    @Override
    public String hello(String text) {
        return "Hello " + text + " from HTTPS!";
    }

}
