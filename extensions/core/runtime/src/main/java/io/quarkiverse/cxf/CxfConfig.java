package io.quarkiverse.cxf;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkiverse.cxf.LoggingConfig.GlobalLoggingConfig;
import io.quarkus.runtime.annotations.ConfigDocIgnore;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.configuration.DurationConverter;
import io.quarkus.runtime.configuration.MemorySize;
import io.quarkus.runtime.configuration.MemorySizeConverter;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.cxf")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface CxfConfig {

    // The formatter breaks the java snippet
    // @formatter:off
    /**
     * An URI base to use as a prefix of `quarkus.cxf.client."client-name".decoupled-endpoint`. You will typically want to set this
     * to something like the following:
     *
     * [source,properties]
     * ----
     * quarkus.cxf.decoupled-endpoint-base = https://api.example.com:${quarkus.http.ssl-port}${quarkus.cxf.path}
     * # or for plain HTTP
     * quarkus.cxf.decoupled-endpoint-base = http://api.example.com:${quarkus.http.port}${quarkus.cxf.path}
     * ----
     *
     * If you invoke your WS client from within a HTTP handler, you can leave this option unspecified and rather set it
     * dynamically on the request context of your WS client using the `org.apache.cxf.ws.addressing.decoupled.endpoint.base`
     * key. Here is an example how to do that from a RESTeasy handler method:
     *
     * [source,java]
     * ----
     * import java.util.Map;
     * import jakarta.inject.Inject;
     * import jakarta.ws.rs.POST;
     * import jakarta.ws.rs.Path;
     * import jakarta.ws.rs.Produces;
     * import jakarta.ws.rs.core.Context;
     * import jakarta.ws.rs.core.MediaType;
     * import jakarta.ws.rs.core.UriInfo;
     * import jakarta.xml.ws.BindingProvider;
     * import io.quarkiverse.cxf.annotation.CXFClient;
     * import org.eclipse.microprofile.config.inject.ConfigProperty;
     *
     * &#64;Path("/my-rest")
     * public class MyRestEasyResource {
     *
     *     &#64;Inject
     *     &#64;CXFClient("hello")
     *     HelloService helloService;
     *
     *     &#64;ConfigProperty(name = "quarkus.cxf.path")
     *                      String quarkusCxfPath;
     *
     *     &#64;POST
     *     &#64;Path("/hello")
     *     &#64;Produces(MediaType.TEXT_PLAIN)
     *         public String hello(String body, &#64;Context UriInfo uriInfo) throws IOException {
     *
     *         // You may consider doing this only once if you are sure that your service is accessed
     *         // through a single hostname
     *         String decoupledEndpointBase = uriInfo.getBaseUriBuilder().path(quarkusCxfPath);
     *         Map>String, Object< requestContext = ((BindingProvider)
     *         helloService).getRequestContext();
     *         requestContext.put("org.apache.cxf.ws.addressing.decoupled.endpoint.base",
     *         decoupledEndpointBase);
     *
     *         return wsrmHelloService.hello(body);
     *     }
     * }
     * ----
     *
     * @since 2.7.0
     * @asciidoclet
     */
    // @formatter:on
    public Optional<String> decoupledEndpointBase();

    /**
     * Choose the path of each web services.
     *
     * @asciidoclet
     */
    @WithName("endpoint")
    @ConfigDocMapKey("/endpoint-path")
    @WithDefaults
    public Map<String, CxfEndpointConfig> endpoints();

    /**
     * Configure client proxies.
     *
     * @asciidoclet
     */
    @WithName("client")
    @ConfigDocMapKey("client-name")

    @WithDefaults
    public Map<String, CxfClientConfig> clients();

    /**
     * This exists just as a convenient way to get a `CxfClientConfig` with all defaults set. It is not intended to be used by
     * end users.
     *
     * @asciidoclet
     */
    public InternalConfig internal();

    /**
     * Global logging related configuration
     *
     * @asciidoclet
     */
    GlobalLoggingConfig logging();

    /**
     * Retransmission cache configuration
     *
     * @asciidoclet
     */
    RetransmitCacheConfig retransmitCache();

    default boolean isClientPresent(String key) {
        return Optional.ofNullable(clients()).map(m -> m.containsKey(key)).orElse(false);
    }

    default CxfClientConfig getClient(String key) {
        return Optional.ofNullable(clients()).map(m -> m.get(key)).orElse(null);
    }

    public interface InternalConfig {

        @ConfigDocIgnore
        public CxfClientConfig client();
    }

    public interface RetransmitCacheConfig {
        // The formatter breaks the list with long items
        // @formatter:off
        /**
         * If the request retransmission is active for the given client and if request body is larger than this threshold,
         * then the body is cached on disk instead of keeping it in memory.
         *
         * In plain CXF, this is equivalent to setting the `bus.io.CachedOutputStream.Threshold` property on CXF Bus.
         *
         * See also:
         *
         * * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-client-client-name-auto-redirect[quarkus.cxf.client."client-name".auto-redirect]`
         *
         * @since 3.18.0
         * @asciidoclet
         */
        // @formatter:on
        @WithDefault("128K")
        @WithConverter(MemorySizeConverter.class)
        public MemorySize threshold();

        // The formatter breaks the list with long items
        // @formatter:off
        /**
         * The maximum size of a request body allowed to be cached on disk when retransmitting.
         * If not set, no limit will be enforced.
         * If set and the limit is exceeded, an exception will be thrown and therefore the request will neither be sent nor redirected.
         *
         * In plain CXF, this is equivalent to setting the `bus.io.CachedOutputStream.MaxSize` property on CXF Bus.
         *
         * See also:
         *
         * * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-client-client-name-auto-redirect[quarkus.cxf.client."client-name".auto-redirect]`
         * * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-retransmit-cache-threshold[quarkus.cxf.retransmit-cache.threshold]`
         *
         * @since 3.18.0
         * @asciidoclet
         */
        // @formatter:on
        @WithConverter(MemorySizeConverter.class)
        public Optional<MemorySize> maxSize();

        // The formatter breaks the list with long items
        // @formatter:off
        /**
         * A directory where request bodies exceeding
         * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-retransmit-cache-threshold[quarkus.cxf.retransmit-cache.threshold]`
         * will be be stored for retransmission.
         * If specified, the directory must exist on application startup.
         * If not specified, the system temporary directory will be used.
         *
         * In plain CXF, this is equivalent to setting the `bus.io.CachedOutputStream.OutputDirectory` property on CXF Bus.
         *
         * See also:
         *
         * * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-client-client-name-auto-redirect[quarkus.cxf.client."client-name".auto-redirect]`
         * * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-retransmit-cache-threshold[quarkus.cxf.retransmit-cache.threshold]`
         *
         * @since 3.18.0
         * @asciidoclet
         */
        // @formatter:on
        public Optional<String> directory();

        // The formatter breaks the list with long items
        // @formatter:off
        /**
         * A delay for periodic cleaning of stale temporary files in the
         * xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-retransmit-cache-directory[retransmit cache directory].
         * Those temporary files are normally removed upon receiving a non-redirect response.
         * The periodic garbage collection is a fallback mechanism for exceptional conditions.
         *
         * The minimum value is 2 seconds. If the value of the delay is set to 0, the garbage collection of stale temporary files will be deactivated.
         *
         * In plain CXF, this is equivalent to setting the `bus.io.CachedOutputStreamCleaner.Delay` property on CXF Bus.
         *
         * See also:
         *
         * * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-client-client-name-auto-redirect[quarkus.cxf.client."client-name".auto-redirect]`
         *
         * @since 3.18.0
         * @asciidoclet
         */
        // @formatter:on
        @WithDefault("30m")
        @WithConverter(DurationConverter.class)
        public Duration gcDelay();

        // The formatter breaks the list with long items
        // @formatter:off
        /**
         * If `true` and if periodic cleaning of stale temporary files is enabled via
         * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-retransmit-cache-gc-delay[quarkus.cxf.retransmit-cache.gc-delay]`
         * then temporary files will be removed on application shutdown.
         * Otherwise the stale temporary files in the
         * xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-retransmit-cache-directory[retransmit cache directory]
         * will not be removed on on application shutdown.
         *
         * In plain CXF, this is equivalent to setting the `bus.io.CachedOutputStreamCleaner.CleanOnShutdown` property on CXF Bus.
         *
         * See also:
         *
         * * `xref:reference/extensions/quarkus-cxf.adoc#quarkus-cxf_quarkus-cxf-client-client-name-auto-redirect[quarkus.cxf.client."client-name".auto-redirect]`
         *
         * @since 3.18.0
         * @asciidoclet
         */
        // @formatter:on
        @WithDefault("true")
        public boolean gcOnShutDown();

    }

}
