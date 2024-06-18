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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.apache.cxf.Bus;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.ClientCallback;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.Cookies;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.Headers;
import org.apache.cxf.transport.http.MessageTrustDecider;
import org.apache.cxf.transport.http.UntrustedURLConnectionIOException;
import org.apache.cxf.transport.https.HttpsURLConnectionInfo;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.jboss.logging.Logger;

import io.quarkiverse.cxf.vertx.web.client.RequestBodyEvent.RequestBodyEventType;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.impl.future.SucceededFuture;

/**
 */
public class VertxWebClientHTTPConduit extends HTTPConduit {

    private static final Logger log = Logger.getLogger(VertxWebClientHTTPConduit.class);

    private final HttpClientPool httpClientPool;

    public VertxWebClientHTTPConduit(Bus b, EndpointInfo ei, HttpClientPool httpClientPool) throws IOException {
        super(b, ei);
        this.httpClientPool = httpClientPool;
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {
        final RequestOptions requestOptions = new RequestOptions();

        final URI uri = address.getURI();
        final String scheme = uri.getScheme();
        message.put("http.scheme", scheme);

        final HttpMethod method = getMethod(message);

        // check tlsClientParameters from message header
        TLSClientParameters clientParameters = message.get(TLSClientParameters.class);
        if (clientParameters == null) {
            clientParameters = tlsClientParameters;
        }
        boolean isHttps = "https".equals(uri.getScheme());
        if (isHttps
                && clientParameters != null
                && clientParameters.getSSLSocketFactory() != null) {
            throw new IllegalStateException("Cannot use SSLSocketFactory set via TLSClientParameters");
        }
        message.put("use.async.http.conduit", Boolean.TRUE);

        final HttpVersion version = getVersion(message, csPolicy);

        final TrustHandler trustHandler;
        if ("https".equals(scheme)) {
            final List<MessageTrustDecider> trustDeciders;
            final MessageTrustDecider decider2;
            if (isHttps
                    && ((decider2 = message.get(MessageTrustDecider.class)) != null
                            || this.trustDecider != null)) {
                trustDeciders = new ArrayList<>(2);
                if (this.trustDecider != null) {
                    trustDeciders.add(this.trustDecider);
                }
                if (decider2 != null) {
                    trustDeciders.add(decider2);
                }
            } else {
                trustDeciders = Collections.emptyList();
            }
            final HostnameVerifier verifier = org.apache.cxf.transport.https.SSLUtils
                    .getHostnameVerifier(findTLSClientParameters(message));
            trustHandler = new TrustHandler(
                    message,
                    uri,
                    method,
                    getConduitName(),
                    trustDeciders,
                    verifier);
        } else {
            trustHandler = null;
        }

        final String query = uri.getQuery();
        final String pathAndQuery = query != null && !query.isEmpty()
                ? uri.getPath() + "?" + query
                : uri.getPath();
        requestOptions
                .setMethod(method)
                .setPort(uri.getPort())
                .setHost(uri.getHost())
                .setURI(pathAndQuery)
                .setConnectTimeout(determineConnectionTimeout(message, csPolicy));

        final RequestContext requestContext = new RequestContext(
                uri,
                requestOptions,
                version,
                determineReceiveTimeout(message, csPolicy),
                trustHandler);
        message.put(RequestContext.class, requestContext);

    }

    @Override
    protected OutputStream createOutputStream(
            Message message,
            boolean possibleRetransmit,
            boolean isChunking,
            int chunkThreshold) throws IOException {
        final RequestContext requestContext = message.get(RequestContext.class);
        final Handler<ResponseEvent> responseHandler = new ResponseHandler(
                requestContext.uri,
                message,
                cookies,
                incomingObserver);
        final Handler<RequestBodyEvent> requestBodyHandler = new RequestBodyHandler(
                httpClientPool,
                requestContext.requestOptions,
                requestContext.version,
                requestContext.receiveTimeoutMs,
                requestContext.trustHandler,
                responseHandler);
        return new RequestBodyOutputStream(chunkThreshold, requestBodyHandler);
    }

    static HttpVersion getVersion(Message message, HTTPClientPolicy csPolicy) {
        String verc = (String) message.getContextualProperty(FORCE_HTTP_VERSION);
        if (verc == null) {
            verc = csPolicy.getVersion();
        }
        if (verc == null) {
            verc = "1.1";
        }
        final HttpVersion v = switch (verc) {
            case "2": {
                yield HttpVersion.HTTP_2;
            }
            case "auto":
            case "1.1": {
                yield HttpVersion.HTTP_1_1;
            }
            case "1.0": {
                yield HttpVersion.HTTP_1_0;
            }
            default:
                throw new IllegalArgumentException("Unexpected HTTP protocol version " + verc);
        };
        return v;
    }

    static HttpMethod getMethod(Message message) {
        final HttpMethod method;
        String rawRequestMethod = (String) message.get(Message.HTTP_REQUEST_METHOD);
        if (rawRequestMethod == null) {
            method = HttpMethod.POST;
            message.put(Message.HTTP_REQUEST_METHOD, "POST");
        } else {
            method = HttpMethod.valueOf(rawRequestMethod);
        }
        return method;
    }

    TLSClientParameters findTLSClientParameters(Message message) {
        TLSClientParameters clientParameters = message.get(TLSClientParameters.class);
        if (clientParameters == null) {
            clientParameters = getTlsClientParameters();
        }
        if (clientParameters == null) {
            clientParameters = new TLSClientParameters();
        }
        return clientParameters;
    }

    static record RequestContext(
            URI uri,
            RequestOptions requestOptions,
            HttpVersion version,
            long receiveTimeoutMs,
            Handler<SSLSession> trustHandler) {
    }

    static class RequestBodyHandler implements Handler<RequestBodyEvent> {
        private final HttpClientPool clientPool;
        private final RequestOptions requestOptions;
        private final HttpVersion version;
        private final long receiveTimeoutMs;
        private final Handler<SSLSession> trustHandler;
        private final Handler<ResponseEvent> responseHandler;

        private Future<HttpClientRequest> requestState;

        private AsyncResult<HttpClientResponse> response;
        private final Lock lock = new ReentrantLock();
        private final Condition responseReceived = lock.newCondition();

        public RequestBodyHandler(
                HttpClientPool clientPool,
                RequestOptions requestOptions,
                HttpVersion version,
                long receiveTimeoutMs,
                Handler<SSLSession> trustHandler,
                Handler<ResponseEvent> responseHandler) {
            super();
            this.clientPool = clientPool;
            this.requestOptions = requestOptions;
            this.version = version;
            this.receiveTimeoutMs = receiveTimeoutMs;
            this.trustHandler = trustHandler;
            this.responseHandler = responseHandler;
        }

        @Override
        public void handle(RequestBodyEvent event) {
            final boolean firstChunk;
            if (requestState == null) {
                HttpClient client = clientPool.getClient(version);

                switch (event.eventType()) {
                    case NON_FINAL_CHUNK:
                    case FINAL_CHUNK: {
                        break;
                    }
                    case COMPLETE_BODY: {
                        requestOptions.putHeader("Content-Length", String.valueOf(event.buffer().length()));
                        break;
                    }
                    default:
                        throw new IllegalArgumentException(
                                "Unexpected " + RequestBodyEventType.class.getName() + ": " + event.eventType());
                }

                this.requestState = client.request(requestOptions);
                if (trustHandler != null) {
                    this.requestState = this.requestState.compose(req -> {
                        trustHandler.handle(req.connection().sslSession());
                        return new SucceededFuture<HttpClientRequest>(req);
                    });
                }

                firstChunk = true;
            } else {
                firstChunk = false;
            }

            switch (event.eventType()) {
                case NON_FINAL_CHUNK: {
                    this.requestState = this.requestState.compose(req -> {
                        if (firstChunk) {
                            req.setChunked(true);
                        }
                        req.write(event.buffer());
                        return new SucceededFuture<HttpClientRequest>(req);
                    });
                    break;
                }
                case FINAL_CHUNK:
                case COMPLETE_BODY: {
                    try {
                        final PipedOutputStream pipedOutputStream = new PipedOutputStream();
                        final ExceptionAwarePipedInputStream pipedInputStream = new ExceptionAwarePipedInputStream(
                                pipedOutputStream);
                        this.requestState = this.requestState.compose(req -> {
                            req.end(event.buffer());

                            req.response()
                                    .onComplete(ar -> {
                                        if (ar.succeeded()) {
                                            pipe(ar.result(), pipedOutputStream, pipedInputStream);
                                        } else {
                                            if (ar.cause() instanceof IOException) {
                                                pipedInputStream.setException((IOException) ar.cause());
                                            } else {
                                                pipedInputStream.setException(new IOException(ar.cause()));
                                            }
                                        }
                                        lock.lock();
                                        try {
                                            response = ar;
                                            responseReceived.signal();
                                        } finally {
                                            lock.unlock();
                                        }
                                    });
                            return new SucceededFuture<HttpClientRequest>(req);
                        });
                        responseHandler.handle(new ResponseEvent(awaitResponse(), pipedInputStream));
                    } catch (IOException e) {
                        throw new VertxHttpException(e);
                    }

                    break;
                }
                default:
                    throw new IllegalArgumentException(
                            "Unexpected " + RequestBodyEventType.class.getName() + ": " + event.eventType());
            }
        }

        HttpClientResponse awaitResponse() {
            /* This should be called from the same worker thread as handle() */
            if (response == null) {
                lock.lock();
                try {
                    if (response == null) {
                        responseReceived.await(receiveTimeoutMs, TimeUnit.MILLISECONDS);
                        if (response == null) {
                            throw new VertxHttpException("Timeout waiting for HTTP response");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new VertxHttpException(e);
                } finally {
                    lock.unlock();
                }
            }
            if (response.succeeded()) {
                return response.result();
            } else {
                final Throwable e = response.cause();
                throw new VertxHttpException(e);
            }
        }

        static void pipe(
                HttpClientResponse response,
                PipedOutputStream pipedOutputStream,
                ExceptionAwarePipedInputStream pipedInputStream

        ) {

            response.handler(buffer -> {
                try {
                    pipedOutputStream.write(buffer.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            response.endHandler(v -> {
                try {
                    pipedOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            response.exceptionHandler(e -> {
                final IOException ioe = e instanceof IOException
                        ? (IOException) e
                        : new IOException(e);
                pipedInputStream.setException(ioe);
            });
        }
    }

    static record ResponseEvent(HttpClientResponse response, InputStream responseBodyInputStream) {
    }

    static class ResponseHandler implements Handler<ResponseEvent> {
        private final URI url;
        private final Message outMessage;
        private final Cookies cookies;
        private final MessageObserver incomingObserver;

        public ResponseHandler(URI url, Message outMessage, Cookies cookies, MessageObserver incomingObserver) {
            super();
            this.url = url;
            this.outMessage = outMessage;
            this.cookies = cookies;
            this.incomingObserver = incomingObserver;
        }

        @Override
        public void handle(ResponseEvent responseEvent) {
            final HttpClientResponse response = responseEvent.response;
            final Exchange exchange = outMessage.getExchange();
            final int responseCode = response.statusCode();

            InputStream in = null;
            // oneway or decoupled twoway calls may expect HTTP 202 with no content

            Message inMessage = new MessageImpl();
            inMessage.setExchange(exchange);
            updateResponseHeaders(response, inMessage, cookies);
            inMessage.put(Message.RESPONSE_CODE, responseCode);
            if (MessageUtils.getContextualBoolean(outMessage, SET_HTTP_RESPONSE_MESSAGE, false)) {
                inMessage.put(HTTP_RESPONSE_MESSAGE, response.statusMessage());
            }
            propagateConduit(exchange, inMessage);

            if ((!doProcessResponse(outMessage, responseCode)
                    || HttpURLConnection.HTTP_ACCEPTED == responseCode)
                    && MessageUtils.getContextualBoolean(outMessage,
                            Message.PROCESS_202_RESPONSE_ONEWAY_OR_PARTIAL, true)) {
                in = getPartialResponse(response, responseEvent.responseBodyInputStream);
                if (in == null
                        || !MessageUtils.getContextualBoolean(outMessage, Message.PROCESS_ONEWAY_RESPONSE, false)) {
                    // oneway operation or decoupled MEP without
                    // partial response
                    if (isOneway(exchange) && responseCode > 300) {
                        final String msg = "HTTP response '" + responseCode + ": "
                                + response.statusMessage() + "' when communicating with " + url.toString();
                        throw new VertxHttpException(msg);
                    }
                    // REVISIT move the decoupled destination property name into api
                    Endpoint ep = exchange.getEndpoint();
                    if (null != ep && null != ep.getEndpointInfo() && null == ep.getEndpointInfo()
                            .getProperty("org.apache.cxf.ws.addressing.MAPAggregator.decoupledDestination")) {
                        // remove callback so that it won't be invoked twice
                        ClientCallback cc = exchange.remove(ClientCallback.class);
                        if (null != cc) {
                            cc.handleResponse(null, null);
                        }
                    }
                    exchange.put("IN_CHAIN_COMPLETE", Boolean.TRUE);

                    exchange.setInMessage(inMessage);
                    if (MessageUtils.getContextualBoolean(outMessage,
                            Message.PROPAGATE_202_RESPONSE_ONEWAY_OR_PARTIAL, false)) {
                        incomingObserver.onMessage(inMessage);
                    }

                    return;
                }
            } else {
                // not going to be resending or anything, clear out the stuff in the out message
                // to free memory
                outMessage.removeContent(OutputStream.class);
                //                if (cachingForRetransmission && cachedStream != null) {
                //                    cachedStream.close();
                //                }
                //                cachedStream = null;
            }

            final String charset = HttpHeaderHelper.findCharset((String) inMessage.get(Message.CONTENT_TYPE));
            final String normalizedEncoding = HttpHeaderHelper.mapCharset(charset);
            if (normalizedEncoding == null) {
                final String m = new org.apache.cxf.common.i18n.Message("INVALID_ENCODING_MSG",
                        LOG, charset).toString();
                throw new VertxHttpException(m);
            }
            inMessage.put(Message.ENCODING, normalizedEncoding);
            if (in == null) {
                in = responseEvent.responseBodyInputStream;
            }
            inMessage.setContent(InputStream.class, in);

            incomingObserver.onMessage(inMessage);

        }

        static void updateResponseHeaders(HttpClientResponse response, Message inMessage, Cookies cookies) {
            Headers h = new Headers(inMessage);
            inMessage.put(Message.CONTENT_TYPE, readHeaders(response, h));
            cookies.readFromHeaders(h);
        }

        static InputStream getPartialResponse(HttpClientResponse response, InputStream responseBodyInputStream) {
            InputStream in = null;
            int responseCode = response.statusCode();
            if (responseCode == 202 || responseCode == 200) {

                final MultiMap headers = response.headers();
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
                    in = responseBodyInputStream;
                } else if (isChunked || isEofTerminated) {
                    // ensure chunked or EOF-terminated response is non-empty
                    try {
                        PushbackInputStream pin = new PushbackInputStream(responseBodyInputStream);
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

        static String readHeaders(HttpClientResponse response, Headers h) {

            final Map<String, List<String>> dest = h.headerMap();
            String ct = null;
            for (Entry<String, String> en : response.headers().entries()) {
                final String key = en.getKey();
                dest.computeIfAbsent(key, k -> new ArrayList<>()).add(en.getValue());
                if (Message.CONTENT_TYPE.equalsIgnoreCase(key)) {
                    ct = en.getValue();
                }
            }
            return ct;
        }

        static void propagateConduit(Exchange exchange, Message in) {
            if (exchange != null) {
                Message out = exchange.getOutMessage();
                if (out != null) {
                    in.put(Conduit.class, out.get(Conduit.class));
                }
            }
        }

        static boolean doProcessResponse(Message message, int responseCode) {
            // 1. Not oneWay
            if (!isOneway(message.getExchange())) {
                return true;
            }
            // 2. Robust OneWays could have a fault
            return responseCode == 500 && MessageUtils.getContextualBoolean(message, Message.ROBUST_ONEWAY, false);
        }

        /**
         * This predicate returns true if the exchange indicates
         * a oneway MEP.
         *
         * @param exchange The exchange in question
         */
        static boolean isOneway(Exchange exchange) {
            return exchange != null && exchange.isOneWay();
        }

    }

    static class ExceptionAwarePipedInputStream extends PipedInputStream {
        private IOException exception;
        private final Object lock = new Object();

        public ExceptionAwarePipedInputStream(PipedOutputStream pipedOutputStream) throws IOException {
            super(pipedOutputStream);
        }

        public void setException(IOException exception) {
            synchronized (lock) {
                if (this.exception != null) {
                    /* Ignore subsequent exceptions */
                    this.exception = exception;
                }
            }
        }

        @Override
        public int read() throws IOException {
            synchronized (lock) {
                if (exception != null) {
                    throw exception;
                }
            }
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            synchronized (lock) {
                if (exception != null) {
                    throw exception;
                }
            }
            return super.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            synchronized (lock) {
                if (exception != null) {
                    throw exception;
                }
            }
            super.close();
        }

    }

    static class TrustHandler implements Handler<SSLSession> {

        private final Message message;
        private final String conduitName;
        private final List<MessageTrustDecider> trustDeciders;
        private final HostnameVerifier verifier;
        private final URI url;
        private final HttpMethod method;

        public TrustHandler(
                Message message,
                URI url,
                HttpMethod method,
                String conduitName,
                List<MessageTrustDecider> trustDeciders,
                HostnameVerifier verifier) {
            super();
            this.message = message;
            this.url = url;
            this.method = method;
            this.conduitName = conduitName;
            this.trustDeciders = trustDeciders;
            this.verifier = verifier;
        }

        @Override
        public void handle(SSLSession sslSession) {
            if (!verifier.verify(url.getHost(), sslSession)) {
                throw new RuntimeException("Could not verify host " + url.getHost());
            }
            if (!trustDeciders.isEmpty()) {
                HttpsURLConnectionInfo info;
                try {
                    info = getHttpsURLConnectionInfo(url, method.name(), sslSession);
                } catch (SSLPeerUnverifiedException e) {
                    throw new RuntimeException(e);
                }
                for (MessageTrustDecider trustDecider : trustDeciders) {
                    try {
                        trustDecider.establishTrust(conduitName, info, message);
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.log(Level.FINE, "Trust Decider "
                                    + trustDecider.getLogicalName()
                                    + " considers Conduit "
                                    + conduitName
                                    + " trusted.");
                        }
                    } catch (UntrustedURLConnectionIOException untrustedEx) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.log(Level.FINE, "Trust Decider "
                                    + trustDecider.getLogicalName()
                                    + " considers Conduit "
                                    + conduitName
                                    + " untrusted.", untrustedEx);
                        }
                        throw new RuntimeException(untrustedEx);
                    }

                }
            } else {
                // This case, when there is no trust decider, a trust
                // decision should be a matter of policy.
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "No Trust Decider for Conduit '"
                            + conduitName
                            + "'. An affirmative Trust Decision is assumed.");
                }
            }

        }

        HttpsURLConnectionInfo getHttpsURLConnectionInfo(URI url, String method, SSLSession session)
                throws SSLPeerUnverifiedException {

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

    }
}
