package io.quarkiverse.cxf.transport.http.hc5;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.microprofile.context.ThreadContext;

import io.quarkiverse.cxf.vertx.client.AsyncHttpResponseWrapper;
import io.quarkiverse.cxf.vertx.client.HttpResponse;
import io.quarkus.arc.Arc;

public class QuarkusAsyncHttpResponseWrapperFactory {

    public AsyncHttpResponseWrapper create() {
        ThreadContext threadContext = Arc.container().select(ThreadContext.class).get();
        /*
         * We need to call this threadContext.contextualConsumer() here in the constructor to store the context
         * because consumeResponse() is called from another thread where the context is not available anymore
         */
        final BiConsumer<io.quarkiverse.cxf.vertx.client.HttpResponse, Consumer<HttpResponse>> contextualConsumer = threadContext.contextualConsumer(
                (HttpResponse response, Consumer<HttpResponse> delegate) -> delegate.accept(response));
        return new MyAsyncResponseCallback(contextualConsumer);
    }

    public static class MyAsyncResponseCallback implements AsyncHttpResponseWrapper {

        final BiConsumer<HttpResponse, Consumer<HttpResponse>> contextualConsumer;

        public MyAsyncResponseCallback(BiConsumer<HttpResponse, Consumer<HttpResponse>> contextualConsumer) {
            super();
            this.contextualConsumer = contextualConsumer;
        }

        @Override
        public void responseReceived(HttpResponse response, Consumer<HttpResponse> delegate) {
            contextualConsumer.accept(response, delegate);
        }

    }
}
