package io.quarkiverse.cxf.vertx.http.client;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpVersion;

public interface HttpClientPool {
    HttpClient getClient(HttpVersion version);
}