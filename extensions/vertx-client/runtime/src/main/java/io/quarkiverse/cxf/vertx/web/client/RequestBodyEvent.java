package io.quarkiverse.cxf.vertx.web.client;

import io.vertx.core.buffer.Buffer;

public record RequestBodyEvent(Buffer buffer, RequestBodyEventType eventType) {
    public enum RequestBodyEventType {
        NON_FINAL_CHUNK,
        FINAL_CHUNK,
        COMPLETE_BODY
    };
}
