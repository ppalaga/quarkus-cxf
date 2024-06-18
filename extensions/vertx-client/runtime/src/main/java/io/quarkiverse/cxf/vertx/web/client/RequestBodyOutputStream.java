package io.quarkiverse.cxf.vertx.web.client;

import java.io.IOException;
import java.io.OutputStream;

import io.quarkiverse.cxf.vertx.web.client.RequestBodyEvent.RequestBodyEventType;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * Simple {@link OutputStream} implementation that appends content
 * written in given {@link Buffer} instance.
 */
class RequestBodyOutputStream extends OutputStream {
    private Buffer buffer;
    private final int chunkSize;
    private final Handler<RequestBodyEvent> bodyHandler;
    private boolean closed = false;
    private boolean firstChunkSent = false;

    /**
     * {@code chunkSize} {@code 0} or less means no chunking - i.e. the buffer will grow
     * endlessly and the {@code bodyHandler} will be notified only once at {@link #close()}.
     *
     * @param chunkSize
     * @param bodyHandler
     */
    public RequestBodyOutputStream(int chunkSize, Handler<RequestBodyEvent> bodyHandler) {
        this.chunkSize = chunkSize;
        this.bodyHandler = bodyHandler;
        this.buffer = Buffer.buffer(chunkSize);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (chunkSize > 0) {
            int remainingCapacity;
            while ((remainingCapacity = chunkSize - buffer.length()) < len) {
                /* Split the bytes */
                buffer.appendBytes(b, off, remainingCapacity);
                off += remainingCapacity;
                len -= remainingCapacity;
                final Buffer buf = buffer;
                bodyHandler.handle(new RequestBodyEvent(buf, RequestBodyEventType.NON_FINAL_CHUNK));
                firstChunkSent = true;
                buffer = Buffer.buffer(chunkSize);
            }
        }

        if (len > 0) {
            /* Write the rest */
            buffer.appendBytes(b, off, len);
        }

    }

    @Override
    public void write(int b) throws IOException {
        if (chunkSize > 0 && buffer.length() == chunkSize) {
            final Buffer buf = buffer;
            bodyHandler.handle(new RequestBodyEvent(buf, RequestBodyEventType.NON_FINAL_CHUNK));
            firstChunkSent = true;
            buffer = Buffer.buffer(chunkSize);
        }
        buffer.appendByte((byte) b);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            super.close();
            RequestBodyEventType eventType = firstChunkSent ? RequestBodyEventType.FINAL_CHUNK
                    : RequestBodyEventType.COMPLETE_BODY;
            final Buffer buf = buffer;
            buffer = null;
            bodyHandler.handle(new RequestBodyEvent(buf, eventType));
        }
    }
}
