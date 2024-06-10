package io.quarkiverse.cxf.vertx.web.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.vertx.core.buffer.Buffer;

/**
 * Simple {@link OutputStream} implementation that appends content
 * written in given {@link Buffer} instance.
 */
public class BufferInputStream extends InputStream {

    private final Buffer buffer;

    private int pos;

    public BufferInputStream(final Buffer buffer) {
        this.buffer = buffer;
        pos = 0;
    }

    @Override
    public int read() throws IOException {
        if (pos == buffer.length()) {
            return -1;
        }
        return buffer.getByte(pos++) & 0xFF;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {

        final int size = Math.min(b.length, buffer.length() - pos);
        if (size == 0) {
            return -1;
        }
        buffer.getBytes(pos, pos + size, b, off);
        pos += size;
        return size;
    }

}