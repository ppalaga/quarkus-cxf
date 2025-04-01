package io.quarkiverse.cxf.it.server;

import jakarta.inject.Inject;
import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.eclipse.microprofile.context.ManagedExecutor;

import io.quarkiverse.cxf.annotation.CXFEndpoint;

/**
 * The simplest Hello service implementation.
 */
@WebService(serviceName = "HelloService")
@CXFEndpoint("/SlowHelloServiceImpl")
public class SlowHelloServiceImpl implements HelloService {

    @Inject
    ManagedExecutor exec;

    @WebMethod
    @Override
    public String hello(String text) {
        //Log.info("= Exec = " + exec);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return "Hello Slow " + text + "!";
    }

}
