/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.quarkiverse.cxf.vertx.client;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.cxf.io.CachedOutputStream;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.impl.future.FailedFuture;
import io.vertx.core.impl.future.SucceededFuture;
import io.vertx.ext.web.handler.HttpException;

public class CXFHttpAsyncRequestProducer  {
    private final CXFHttpRequest request;
    private final SharedOutputBuffer buf;
    private volatile CachedOutputStream content;
    private volatile Buffer buffer;
    private volatile InputStream fis;
    private volatile ReadableByteChannel chan;
    private final Vertx vertx;
    private FileSystem fs;

    public CXFHttpAsyncRequestProducer(Vertx vertx, final CXFHttpRequest request, final SharedOutputBuffer buf) {
        super();
        this.vertx = vertx;
        this.buf = buf;
        this.request = request;
    }

    public void produce(HttpClientRequest request, Handler<AsyncResult<Void>> callerCallback) {
        if (content != null) {
            if (buffer == null) {
                if (content.getTempFile() == null) {
                    buffer = Buffer.buffer(content.getBytes());
                    request.end(buffer);
                    callerCallback.handle(SucceededFuture.EMPTY);
                } else {
                    fs = vertx.fileSystem();
                    // FIXME: we need to chunk for large files
                    fs.readFile(content.getTempFile().toString(), result -> {
                        if (result.succeeded()) {
                            Buffer buffer = result.result();
                            request.end(buffer);
                            callerCallback.handle(SucceededFuture.EMPTY);
                        } else {
                            callerCallback.handle(new FailedFuture<>(result.cause()));
                        }
                    });
                }
            }
            int i = -1;
            //((Buffer) buffer).rewind();
            if (fs != null) {

                i = chan.read(buffer);
                buffer.flip();
            }
            request.write(buffer);
            if (!buffer.hasRemaining() && i == -1) {
                request.endStream();
            }
        } else {
            buf.produceContent(request);
        }
    }

    @Override
    public void failed(final Exception ex) {
        buf.shutdown();
    }

    @Override
    public boolean isRepeatable() {
        return request.getOutputStream().retransmitable();
    }

    private void resetRequest() {
        if (request.getOutputStream().retransmitable()) {
            content = request.getOutputStream().getCachedStream();
        }
    }

    @Override
    public int available() {
        return 0;
    }

    @Override
    public void releaseResources() {
        buf.close();
        if (fis != null) {
            try {
                fis.close();
            } catch (IOException io) {
                //ignore
            }
            chan = null;
            fis = null;
        }
        buffer = null;
        resetRequest();
    }

    @Override
    public void sendRequest(RequestChannel channel, HttpContext context) throws HttpException, IOException {
        channel.sendRequest(request, request.getEntity(), context);
    }
}
