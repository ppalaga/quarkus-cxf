package io.quarkiverse.cxf.it.security.policy;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

/**
 * A service implementation with an encryption policy set
 */
@WebService
public class EncryptSignPolicyHelloServiceImpl implements EncryptSignPolicyHelloService {

    @WebMethod
    @Override
    public String hello(String text) {
        return "Hello " + text + " from EncryptSign!";
    }

}
