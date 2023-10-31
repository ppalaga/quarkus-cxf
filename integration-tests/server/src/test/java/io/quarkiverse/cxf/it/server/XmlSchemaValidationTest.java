package io.quarkiverse.cxf.it.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.cxf.it.server.xml.schema.validation.model.CalculatorService;
import io.quarkiverse.cxf.test.QuarkusCxfClientTestUtil;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class XmlSchemaValidationTest {

    @Test
    public void addSmall() {
        final CalculatorService client = getClient();

        Assertions.assertThat(client.add(2, 7)).isEqualTo(9);
    }

    @Test
    public void addBig() {
        final CalculatorService client = getClient();

        Assertions.assertThat(client.add(1028, 2)).isEqualTo(1030);
    }

    private CalculatorService getClient() {
        final CalculatorService client = QuarkusCxfClientTestUtil.getClient(
                "http://www.jboss.org/eap/quickstarts/wscalculator/Calculator",
                CalculatorService.class,
                "/soap/schema-validated-calculator");
        return client;
    }

}
