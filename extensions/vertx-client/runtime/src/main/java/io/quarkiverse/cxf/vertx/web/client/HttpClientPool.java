package io.quarkiverse.cxf.vertx.web.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;

@Singleton
public class HttpClientPool {
    private final Map<HttpVersion, HttpClient> clients = new ConcurrentHashMap<>();

    @Inject
    Vertx vertx;

    public HttpClient getClient(HttpVersion version) {
        final HttpClientOptions opts = new HttpClientOptions().setProtocolVersion(version);
        return clients.computeIfAbsent(version, v -> {
            return vertx.createHttpClient(opts);
        });
    }
}
