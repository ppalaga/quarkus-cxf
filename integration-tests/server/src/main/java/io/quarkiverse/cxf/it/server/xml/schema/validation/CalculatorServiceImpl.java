package io.quarkiverse.cxf.it.server.xml.schema.validation;

import jakarta.jws.WebService;

import org.apache.cxf.annotations.EndpointProperties;
import org.apache.cxf.annotations.EndpointProperty;

import io.quarkiverse.cxf.it.server.xml.schema.validation.model.CalculatorService;

@WebService(serviceName = "CalculatorService", targetNamespace = "http://www.jboss.org/eap/quickstarts/wscalculator/Calculator")
@EndpointProperties(value = {
        @EndpointProperty(key = "schema-validation-enabled", value = "true")
})
public class CalculatorServiceImpl implements CalculatorService {

    @Override
    public int add(int a, int b) {
        return a + b;
    }

}
