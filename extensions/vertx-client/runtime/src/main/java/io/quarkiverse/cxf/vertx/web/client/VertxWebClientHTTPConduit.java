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

package io.quarkiverse.cxf.vertx.web.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.HostnameVerifier;

import org.apache.cxf.Bus;
import org.apache.cxf.common.util.PropertyUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.Headers;
import org.apache.cxf.transport.http.netty.client.CxfResponseCallBack;
import org.apache.cxf.transport.http.netty.client.NettyHttpClientPipelineFactory;
import org.apache.cxf.transport.https.HttpsURLConnectionInfo;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.version.Version;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.ssl.SslHandler;
import io.quarkiverse.cxf.vertx.web.client.VertxWebClientHTTPConduitFactory.SharedClient;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 */
public class VertxWebClientHTTPConduit extends HTTPConduit {

    private final WebClient webClient;
    private final SharedClient sharedWebClient;

    public VertxWebClientHTTPConduit(Bus b, EndpointInfo ei, SharedClient sharedWebClient) throws IOException {
        super(b, ei);
        this.sharedWebClient = sharedWebClient;
        this.webClient = sharedWebClient.lease();
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {
        URI uri = address.getURI();
        message.put("http.scheme", uri.getScheme());
        final HttpMethod method;
        String rawRequestMethod = (String) message.get(Message.HTTP_REQUEST_METHOD);
        if (rawRequestMethod == null) {
            method = HttpMethod.POST;
            message.put(Message.HTTP_REQUEST_METHOD, "POST");
        } else {
            method = HttpMethod.valueOf(rawRequestMethod);
        }
        String verc = (String) message.getContextualProperty(FORCE_HTTP_VERSION);
        if (verc == null) {
            verc = csPolicy.getVersion();
        }

        final HttpRequest<Buffer> request = webClient
                .request(method, uri.toString())
                .connectTimeout(csPolicy.getConnectionTimeout());
        message.put(RequestData.class, new RequestData(request, uri, csPolicy));

    }

    @Override
    protected OutputStream createOutputStream(Message message, boolean possibleRetransmit, boolean isChunking,
            int chunkThreshold) throws IOException {
        final RequestData requestData = message.get(RequestData.class);
        return new VertxWebClientWrappedOutputStream(message, possibleRetransmit, isChunking, chunkThreshold, getConduitName(),
                requestData);
    }

    @Override
    public void close() {
        super.close();
        sharedWebClient.release();
    }

    static record RequestData(HttpRequest<Buffer> request, URI uri, HTTPClientPolicy httpClientPolicy) {
    }

    public static class VertxWebClientWrappedOutputStream extends WrappedOutputStream {

        private final RequestData requestData;
        private final Buffer buffer;
        private final Lock lock = new ReentrantLock();
        private final Condition responded = lock.newCondition();

        private HttpResponse<Buffer> response;
        private Throwable exception;

        @SuppressWarnings("unchecked")
        protected VertxWebClientWrappedOutputStream(Message message, boolean possibleRetransmit,
                boolean isChunking, int chunkThreshold, String conduitName, RequestData requestData) {
            super(message, possibleRetransmit, isChunking, chunkThreshold, conduitName, requestData.uri());
            this.requestData = requestData;
            final HTTPClientPolicy csPolicy = requestData.httpClientPolicy();
            int bufSize = csPolicy.getChunkLength() > 0 ? csPolicy.getChunkLength() : 16320;
            this.buffer = Buffer.buffer(bufSize);
            this.wrappedStream = new BufferOutputStream(buffer);
        }

        protected void connect(boolean output) {
            final HttpRequest<Buffer> req = requestData.request()
            // .idleTimeout(threshold)
            ;

            final Future<HttpResponse<Buffer>> sendBufferState = output
                    ? req.sendBuffer(buffer)
                    : req.send();

            sendBufferState
                    .onSuccess(r -> {
                        lock.lock();
                        try {
                            response = r;
                            responded.signal();
                        } finally {
                            lock.unlock();
                        }
                    })
                    .onFailure(e -> {
                        lock.lock();
                        try {
                            exception = e;
                            responded.signal();
                        } finally {
                            lock.unlock();
                        }
                    });
        }

        protected HttpResponse<Buffer> getOrAwaitResponse() throws IOException {
            lock.lock();
            try {
                while (response == null) {
                    if (exception == null) {
                        try {
                            responded.await(requestData.httpClientPolicy().getReceiveTimeout(), TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException(e);
                        }
                    }
                    if (response == null) {

                        if (exception != null) {
                            if (exception instanceof IOException) {
                                throw (IOException) exception;
                            }
                            if (exception instanceof RuntimeException) {
                                throw (RuntimeException) exception;
                            }
                            throw new IOException(exception);
                        }

                        throw new SocketTimeoutException("Read Timeout");
                    }
                }
                return response;
            } finally {
                lock.unlock();
            }
        }

        @Override
        protected void handleNoOutput() throws IOException {
            connect(false);
        }



        @Override
        protected HttpsURLConnectionInfo getHttpsURLConnectionInfo() throws IOException {
            if ("http".equals(outMessage.get("http.scheme"))) {
                return null;
            }
            connect(true);

            HostnameVerifier verifier = org.apache.cxf.transport.https.SSLUtils
                .getHostnameVerifier(findTLSClientParameters());

            if (!verifier.verify(url.getHost(), session)) {
                throw new IOException("Could not verify host " + url.getHost());
            }

            String method = (String)outMessage.get(Message.HTTP_REQUEST_METHOD);
            String cipherSuite = null;
            Certificate[] localCerts = null;
            Principal principal = null;
            Certificate[] serverCerts = null;
            Principal peer = null;
            if (session != null) {
                cipherSuite = session.getCipherSuite();
                localCerts = session.getLocalCertificates();
                principal = session.getLocalPrincipal();
                serverCerts = session.getPeerCertificates();
                peer = session.getPeerPrincipal();
            }

            return new HttpsURLConnectionInfo(url, method, cipherSuite, localCerts, principal, serverCerts, peer);
        }

        @Override
        protected void setProtocolHeaders() throws IOException {
            Headers h = new Headers(outMessage);
            setContentTypeHeader(h);
            boolean addHeaders = MessageUtils.getContextualBoolean(outMessage, Headers.ADD_HEADERS_PROPERTY, false);

            for (Map.Entry<String, List<String>> header : h.headerMap().entrySet()) {
                if (HttpHeaderHelper.CONTENT_TYPE.equalsIgnoreCase(header.getKey())) {
                    continue;
                }
                if (addHeaders || HttpHeaderHelper.COOKIE.equalsIgnoreCase(header.getKey())) {
                    for (String s : header.getValue()) {
                        entity.getRequest().headers().add(HttpHeaderHelper.COOKIE, s);
                    }
                } else if (!"Content-Length".equalsIgnoreCase(header.getKey())) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < header.getValue().size(); i++) {
                        b.append(header.getValue().get(i));
                        if (i + 1 < header.getValue().size()) {
                            b.append(',');
                        }
                    }
                    entity.getRequest().headers().set(header.getKey(), b.toString());
                }
                if (!entity.getRequest().headers().contains("User-Agent")) {
                    entity.getRequest().headers().set("User-Agent", Version.getCompleteVersionString());
                }
            }
        }

        private void setContentTypeHeader(Headers headers) {
            if (outMessage.get(Message.CONTENT_TYPE) == null) {
                // if no content type is set then check for a request body
                Object requestMethod = outMessage.get(Message.HTTP_REQUEST_METHOD);
                boolean emptyRequest = KNOWN_HTTP_VERBS_WITH_NO_CONTENT.contains(requestMethod)
                        || PropertyUtils.isTrue(outMessage.get(Headers.EMPTY_REQUEST_PROPERTY));
                // If it is not an empty request then add a content type
                if (!emptyRequest) {
                    entity.getRequest().headers().set(Message.CONTENT_TYPE, headers.determineContentType());
                }
            } else {
                entity.getRequest().headers().set(Message.CONTENT_TYPE, headers.determineContentType());
            }
        }

    }
}
