package io.quarkiverse.cxf.vertx.http.client;

import io.vertx.core.Future;

/**
 * Provider of per-request {@code Authorization} header value.
 *
 * @since 4.0.0
 */
public interface AuthorizationProvider {
    Future<String> getAuthorization(String clientKey);
}
