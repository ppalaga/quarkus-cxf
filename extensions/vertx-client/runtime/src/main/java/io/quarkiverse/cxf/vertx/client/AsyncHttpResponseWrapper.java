package io.quarkiverse.cxf.vertx.client;

import java.util.function.Consumer;

/**
 * The wrapper around the response that will be called by the {@link AsyncHTTPConduit}
 * instance once the response is received.
 */
public interface AsyncHttpResponseWrapper {
    /**
     * The callback which is called by the {@link AsyncHTTPConduit} instance once
     * the response is received. The delegating response handler is passed as the
     * an argument and has to be called.
     *
     * @param response the response received
     * @param delegate delegating response handler
     */
    default void responseReceived(HttpResponse response, Consumer<HttpResponse> delegate) {
        delegate.accept(response);
    }
}