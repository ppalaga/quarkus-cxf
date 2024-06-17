package io.quarkiverse.cxf.vertx.web.client;

import java.io.IOException;
import java.io.OutputStream;

import org.jboss.logging.Logger;

import io.vertx.core.buffer.Buffer;

/**
 * Simple {@link OutputStream} implementation that appends content
 * written in given {@link Buffer} instance.
 */
public class BufferOutputStream extends OutputStream {
    private static final Logger log = Logger.getLogger(VertxWebClientHTTPConduit.class);

    private final Buffer buffer;

    public BufferOutputStream(Buffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        log.warn("=== writing " + new String(b), new RuntimeException());
        buffer.appendBytes(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        buffer.appendInt(b);
    }
}
