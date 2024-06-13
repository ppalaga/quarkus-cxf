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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.cxf.Bus;
import org.apache.cxf.common.util.PropertyUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.io.CacheAndWriteOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.Cookies;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.Headers;
import org.apache.cxf.transport.https.HttpsURLConnectionInfo;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.version.Version;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.impl.ClientPhase;
import io.vertx.ext.web.client.impl.HttpContext;
import io.vertx.ext.web.client.impl.WebClientInternal;

/**
 */
public class VertxWebClientHTTPConduit extends HTTPConduit {

    private final HttpClient httpClient;

    public VertxWebClientHTTPConduit(Bus b, EndpointInfo ei, HttpClient httpClient) throws IOException {
        super(b, ei);
        this.httpClient = httpClient;
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {

        final URI uri = address.getURI();
        final String scheme = uri.getScheme();
        message.put("http.scheme", scheme);

        // check tlsClientParameters from message header
        TLSClientParameters clientParameters = message.get(TLSClientParameters.class);
        if (clientParameters == null) {
            clientParameters = tlsClientParameters;
        }
        if ("https".equals(uri.getScheme())
                && clientParameters != null
                && clientParameters.getSSLSocketFactory() != null) {
            throw new IllegalStateException("Cannot use SSLSocketFactory set via TLSClientParameters");
        }
        message.put("use.async.http.conduit", Boolean.TRUE);

        final HttpMethod method;
        String rawRequestMethod = (String) message.get(Message.HTTP_REQUEST_METHOD);
        if (rawRequestMethod == null) {
            method = HttpMethod.POST;
            message.put(Message.HTTP_REQUEST_METHOD, "POST");
        } else {
            method = HttpMethod.valueOf(rawRequestMethod);
        }
        final WebClientOptions opts = new WebClientOptions();

        String verc = (String) message.getContextualProperty(FORCE_HTTP_VERSION);
        if (verc == null) {
            verc = csPolicy.getVersion();
        }
        if (verc != null && "2".equals(verc)) {
            opts.setProtocolVersion(HttpVersion.HTTP_2);
        }

        final WebClientInternal webClient = (WebClientInternal) WebClient.wrap(httpClient, opts);

        final SslSessionHolder sslSessionHolder = new SslSessionHolder(csPolicy.getConnectionTimeout());
        if ("https".equals(scheme)) {
            webClient.addInterceptor(sslSessionHolder);
        }

        final HttpRequest<Buffer> request = webClient
                .request(method, uri.toString())
                .connectTimeout(determineConnectionTimeout(message, csPolicy));
        message.put(RequestContext.class, sslSessionHolder.createRequestContext(request, uri, csPolicy));

    }

    @Override
    protected OutputStream createOutputStream(Message message, boolean possibleRetransmit, boolean isChunking,
            int chunkThreshold) throws IOException {
        final RequestContext requestContext = message.get(RequestContext.class);
        return new VertxWebClientWrappedOutputStream(message, possibleRetransmit, isChunking, chunkThreshold, getConduitName(),
                requestContext);
    }

    static record RequestContext(HttpRequest<Buffer> request, URI uri, HTTPClientPolicy httpClientPolicy,
            SslSessionHolder sslSessionHolder) {

        public Buffer createBuffer() {
            int bufSize = httpClientPolicy.getChunkLength() > 0 ? httpClientPolicy.getChunkLength() : 16320;
            return Buffer.buffer(bufSize);
        }
    }

    static class SslSessionHolder implements Handler<HttpContext<?>> {

        private final long connectTimeout;
        private final Lock lock = new ReentrantLock();
        private final Condition connected = lock.newCondition();
        private SSLSession sslSession;

        public SSLSession getOrAwaitSslSession() throws IOException {
            lock.lock();
            try {
                if (sslSession == null) {
                    try {
                        connected.await(connectTimeout, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                    if (sslSession == null) {
                        throw new SocketTimeoutException("Timeout waiting for SSL Session");
                    }
                }
                return sslSession;
            } finally {
                lock.unlock();
            }
        }

        public RequestContext createRequestContext(HttpRequest<Buffer> request, URI uri, HTTPClientPolicy csPolicy) {
            return new RequestContext(request, uri, csPolicy, this);
        }

        public SslSessionHolder(long connectTimeout) {
            super();
            this.connectTimeout = connectTimeout;
        }

        @Override
        public void handle(HttpContext<?> ctx) {
            if (ctx.phase() == ClientPhase.RECEIVE_RESPONSE) {
                final SSLSession sslSession = ctx.clientResponse().netSocket().sslSession();
                lock.lock();
                try {
                    this.sslSession = sslSession;
                    connected.notify();
                } finally {
                    lock.unlock();
                }
            }
        }

    }

    public class VertxWebClientWrappedOutputStream extends WrappedOutputStream {

        private RequestContext requestContext;
        private Buffer buffer;
        private final Lock lock;
        private final Condition bodyReceived;

        private HttpResponse<Buffer> response;
        private Throwable exception;
        private Cookies cookies;

        @SuppressWarnings("unchecked")
        protected VertxWebClientWrappedOutputStream(Message message, boolean possibleRetransmit,
                boolean isChunking, int chunkThreshold, String conduitName, RequestContext requestContext) {
            super(message, possibleRetransmit, isChunking, chunkThreshold, conduitName, requestContext.uri());
            this.requestContext = requestContext;
            this.lock = requestContext.sslSessionHolder.lock; // must be the same lock
            this.bodyReceived = lock.newCondition();
            this.buffer = requestContext.createBuffer();
            this.wrappedStream = new BufferOutputStream(buffer);
        }

        protected HttpResponse<Buffer> getOrAwaitResponse() throws IOException {
            lock.lock();
            try {
                while (response == null) {
                    if (exception == null) {
                        try {
                            bodyReceived.await(
                                    determineReceiveTimeout(
                                            outMessage,
                                            requestContext.httpClientPolicy()),
                                    TimeUnit.MILLISECONDS);
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
        protected void setupWrappedStream() throws IOException {
            connect(true);

            // If we need to cache for retransmission, store data in a
            // CacheAndWriteOutputStream. Otherwise write directly to the output stream.
            if (cachingForRetransmission) {
                cachedStream = new CacheAndWriteOutputStream(wrappedStream);
                wrappedStream = cachedStream;
            }
        }

        @Override
        protected void handleNoOutput() throws IOException {
            connect(false);
        }

        protected TLSClientParameters findTLSClientParameters() {
            TLSClientParameters clientParameters = outMessage.get(TLSClientParameters.class);
            if (clientParameters == null) {
                clientParameters = getTlsClientParameters();
            }
            if (clientParameters == null) {
                clientParameters = new TLSClientParameters();
            }
            return clientParameters;
        }

        protected void connect(boolean output) {
            final HttpRequest<Buffer> req = requestContext.request()
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
                            bodyReceived.signal();
                        } finally {
                            lock.unlock();
                        }
                    })
                    .onFailure(e -> {
                        lock.lock();
                        try {
                            exception = e;
                            bodyReceived.signal();
                        } finally {
                            lock.unlock();
                        }
                    });
        }

        @Override
        protected HttpsURLConnectionInfo getHttpsURLConnectionInfo() throws IOException {
            if ("http".equals(outMessage.get("http.scheme"))) {
                return null;
            }
            connect(true);

            HostnameVerifier verifier = org.apache.cxf.transport.https.SSLUtils
                    .getHostnameVerifier(findTLSClientParameters());

            final SSLSession session = requestContext.sslSessionHolder.getOrAwaitSslSession();
            if (!verifier.verify(url.getHost(), session)) {
                throw new IOException("Could not verify host " + url.getHost());
            }

            String method = (String) outMessage.get(Message.HTTP_REQUEST_METHOD);
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
            final HttpRequest<Buffer> req = requestContext.request();

            for (Map.Entry<String, List<String>> header : h.headerMap().entrySet()) {
                if (HttpHeaderHelper.CONTENT_TYPE.equalsIgnoreCase(header.getKey())) {
                    continue;
                }
                MultiMap headers = req.headers();
                if (addHeaders || HttpHeaderHelper.COOKIE.equalsIgnoreCase(header.getKey())) {
                    for (String s : header.getValue()) {
                        headers.add(HttpHeaderHelper.COOKIE, s);
                    }
                } else if (!"Content-Length".equalsIgnoreCase(header.getKey())) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < header.getValue().size(); i++) {
                        b.append(header.getValue().get(i));
                        if (i + 1 < header.getValue().size()) {
                            b.append(',');
                        }
                    }
                    headers.set(header.getKey(), b.toString());
                }
                if (!headers.contains("User-Agent")) {
                    headers.set("User-Agent", Version.getCompleteVersionString());
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
                    requestContext.request().headers().set(Message.CONTENT_TYPE, headers.determineContentType());
                }
            } else {
                requestContext.request().headers().set(Message.CONTENT_TYPE, headers.determineContentType());
            }
        }

        @Override
        protected void setFixedLengthStreamingMode(int i) {
            // Here we can set the Content-Length
            HttpRequest<Buffer> request = requestContext.request();
            request.headers().set("Content-Length", String.valueOf(i));
        }

        @Override
        protected int getResponseCode() throws IOException {
            return getOrAwaitResponse().statusCode();
        }

        @Override
        protected String getResponseMessage() throws IOException {
            return getOrAwaitResponse().statusMessage();
        }

        @Override
        protected void updateResponseHeaders(Message inMessage) throws IOException {
            Headers h = new Headers(inMessage);
            inMessage.put(Message.CONTENT_TYPE, readHeaders(h));
            cookies.readFromHeaders(h);
        }

        private String readHeaders(Headers h) throws IOException {
            MultiMap responseHeaders = getOrAwaitResponse().headers();
            Set<String> headerNames = responseHeaders.names();
            String ct = null;
            for (String name : headerNames) {
                List<String> s = responseHeaders.getAll(name);
                h.headerMap().put(name, s);
                if (Message.CONTENT_TYPE.equalsIgnoreCase(name)) {
                    ct = responseHeaders.get(name);
                }
            }
            return ct;
        }

        @Override
        protected void handleResponseAsync() throws IOException {
            // TODO: make sure that we are always async
        }

        @Override
        protected void closeInputStream() throws IOException {
            // Vert.x Buffers cannot be closed (Unless they are backed by Netty ByteBufs?)
        }

        @Override
        protected boolean usingProxy() {
            // TODO we need to support it
            return false;
        }

        @Override
        protected InputStream getInputStream() throws IOException {
            return new BufferInputStream(getOrAwaitResponse().body());
        }

        @Override
        protected InputStream getPartialResponse() throws IOException {
            InputStream in = null;
            final HttpResponse<Buffer> resp = getOrAwaitResponse();
            int responseCode = resp.statusCode();
            if (responseCode == HttpURLConnection.HTTP_ACCEPTED
                    || responseCode == HttpURLConnection.HTTP_OK) {

                final MultiMap headers = resp.headers();
                String head = headers.get(HttpHeaderHelper.CONTENT_LENGTH);
                int cli = 0;
                if (head != null) {
                    cli = Integer.parseInt(head);
                }
                head = headers.get(HttpHeaderHelper.TRANSFER_ENCODING);
                boolean isChunked = head != null && HttpHeaderHelper.CHUNKED.equalsIgnoreCase(head);
                head = headers.get(HttpHeaderHelper.CONNECTION);
                boolean isEofTerminated = head != null && HttpHeaderHelper.CLOSE.equalsIgnoreCase(head);
                if (cli > 0) {
                    in = getInputStream();
                } else if (isChunked || isEofTerminated) {
                    // ensure chunked or EOF-terminated response is non-empty
                    try {
                        PushbackInputStream pin = new PushbackInputStream(getInputStream());
                        int c = pin.read();
                        if (c != -1) {
                            pin.unread((byte) c);
                            in = pin;
                        }
                    } catch (IOException ioe) {
                        // ignore
                    }
                }
            }
            return in;
        }

        @Override
        protected void setupNewConnection(String newURL) throws IOException {
            lock.lock();
            try {
                response = null;
                // isAsync = false;
                exception = null;
                bodyReceived.signal();
            } finally {
                lock.unlock();
            }

            try {
                final Address address;
                if (defaultAddress.getString().equals(newURL)) {
                    address = defaultAddress;
                    this.url = defaultAddress.getURI();
                } else {
                    this.url = new URI(newURL);
                    address = new Address(newURL, this.url);
                }
                setupConnection(outMessage, address, requestContext.httpClientPolicy());
                requestContext = outMessage.get(RequestContext.class);
                // TODO: Once we make sure the buffer cannot be written from the previous request at this point and once
                // we use a Buffer implementation that allows resetting, we may just reset the buffer instead of
                // creating a new one.
                this.buffer = requestContext.createBuffer();
                this.wrappedStream = new BufferOutputStream(buffer);

            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }

        @Override
        protected void retransmitStream() throws IOException {
            cachingForRetransmission = false; // already cached
            setupWrappedStream();
            cachedStream.writeCacheTo(wrappedStream);
            wrappedStream.flush();
            wrappedStream.close();
        }

        @Override
        protected void updateCookiesBeforeRetransmit() throws IOException {
            Headers h = new Headers();
            readHeaders(h);
            cookies.readFromHeaders(h);
        }

        @Override
        public void thresholdReached() throws IOException {
            // TODO do we need to force chunked somehow?
        }
    }
}
