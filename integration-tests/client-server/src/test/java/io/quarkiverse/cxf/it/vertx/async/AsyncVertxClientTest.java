package io.quarkiverse.cxf.it.vertx.async;

import static org.hamcrest.CoreMatchers.is;

import org.assertj.core.api.Assumptions;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import io.quarkiverse.cxf.HTTPConduitImpl;
import io.quarkiverse.cxf.it.large.slow.LargeSlowServiceImpl;
import io.quarkus.runtime.configuration.MemorySizeConverter;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class AsyncVertxClientTest {

    @Test
    void helloWithWsdl() {
        /* URLConnectionHTTPConduitFactory does not support async */
        Assumptions.assumeThat(HTTPConduitImpl.findDefaultHTTPConduitImpl())
                .isNotEqualTo(HTTPConduitImpl.URLConnectionHTTPConduitFactory);

        RestAssured.given()
                .body("Joe")
                .post("/RestAsyncWithWsdl/helloWithWsdl")
                .then()
                .statusCode(500)
                .body(CoreMatchers.containsString(
                        "You have attempted to perform a blocking operation on an IO thread."));

    }

    @Test
    void helloWithWsdlWithBlocking() {
        /* URLConnectionHTTPConduitFactory does not support async */
        Assumptions.assumeThat(HTTPConduitImpl.findDefaultHTTPConduitImpl())
                .isNotEqualTo(HTTPConduitImpl.URLConnectionHTTPConduitFactory);

        RestAssured.given()
                .body("Joe")
                .post("/RestAsyncWithWsdlWithBlocking/helloWithWsdlWithBlocking")
                .then()
                .statusCode(200)
                .body(is("Hello Joe from HelloWithWsdlWithBlocking"));
    }

    @Test
    void helloWithWsdlWithEagerInit() {
        /* URLConnectionHTTPConduitFactory does not support async */
        Assumptions.assumeThat(HTTPConduitImpl.findDefaultHTTPConduitImpl())
                .isNotEqualTo(HTTPConduitImpl.URLConnectionHTTPConduitFactory);

        RestAssured.given()
                .body("Max")
                .post("/RestAsyncWithWsdlWithEagerInit/helloWithWsdlWithEagerInit")
                .then()
                .statusCode(200)
                .body(is("Hello Max from HelloWithWsdlWithEagerInit"));
    }

    @Test
    void helloWithoutWsdl() {
        /* URLConnectionHTTPConduitFactory does not support async */
        Assumptions.assumeThat(HTTPConduitImpl.findDefaultHTTPConduitImpl())
                .isNotEqualTo(HTTPConduitImpl.URLConnectionHTTPConduitFactory);

        RestAssured.given()
                .body("Joe")
                .post("/RestAsyncWithoutWsdl/helloWithoutWsdl")
                .then()
                .statusCode(200)
                .body(is("Hello Joe from HelloWithoutWsdl"));
    }

    @Test
    void helloWithoutWsdlWithBlocking() {
        /* URLConnectionHTTPConduitFactory does not support async */
        Assumptions.assumeThat(HTTPConduitImpl.findDefaultHTTPConduitImpl())
                .isNotEqualTo(HTTPConduitImpl.URLConnectionHTTPConduitFactory);

        RestAssured.given()
                .body("Joe")
                .post("/RestAsyncWithoutWsdlWithBlocking/helloWithoutWsdlWithBlocking")
                .then()
                .statusCode(200)
                .body(is("Hello Joe from HelloWithoutWsdlWithBlocking"));
    }

    static String body() {
        final MemorySizeConverter converter = new MemorySizeConverter();
        final int payloadLen = (int) converter.convert("9M").asLongValue();
        return LargeSlowServiceImpl.largeString(payloadLen);
    }

}
