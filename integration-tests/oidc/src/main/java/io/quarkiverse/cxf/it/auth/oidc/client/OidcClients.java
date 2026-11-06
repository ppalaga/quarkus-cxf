package io.quarkiverse.cxf.it.auth.oidc.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import org.apache.cxf.BusFactory;
import org.apache.cxf.wsdl.WSDLManager;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.cxf.annotation.CXFClient;
import io.quarkiverse.cxf.it.auth.oidc.client.model.OidcEcho;

/** Injected client suppliers keep WSDL loading and configuration validation inside the tested request context. */
@ApplicationScoped
public class OidcClients {

    @CXFClient("wsdlDefaultSecure")
    Instance<OidcEcho> wsdlDefaultSecure;

    @CXFClient("acquireDefault")
    Instance<OidcEcho> acquireDefault;

    @CXFClient("acquireNamed")
    Instance<OidcEcho> acquireNamed;

    @CXFClient("propagate")
    Instance<OidcEcho> propagate;

    @CXFClient("exchangeNamed")
    Instance<OidcEcho> exchangeNamed;

    @CXFClient("exchangeOther")
    Instance<OidcEcho> exchangeOther;

    @CXFClient("exchangeAudience")
    Instance<OidcEcho> exchangeAudience;

    @CXFClient("defaultClient")
    Instance<OidcEcho> defaultClient;

    @CXFClient("lifecycleNamed")
    Instance<OidcEcho> lifecycleNamed;

    @CXFClient("lifecycleExchange")
    Instance<OidcEcho> lifecycleExchange;

    @CXFClient("refresh")
    Instance<OidcEcho> refresh;

    @CXFClient("refreshFailure")
    Instance<OidcEcho> refreshFailure;

    @CXFClient("failureAcquire")
    Instance<OidcEcho> failureAcquire;

    @CXFClient("failureExchange")
    Instance<OidcEcho> failureExchange;

    @CXFClient("refusedAcquire")
    Instance<OidcEcho> refusedAcquire;

    @CXFClient("refusedExchange")
    Instance<OidcEcho> refusedExchange;

    @CXFClient("concurrentExchange")
    Instance<OidcEcho> concurrentExchange;

    @CXFClient("asyncAcquire")
    Instance<OidcEcho> asyncAcquire;

    @CXFClient("asyncExchange")
    Instance<OidcEcho> asyncExchange;

    @CXFClient("omittedAnonymous")
    Instance<OidcEcho> omittedAnonymous;

    @CXFClient("omittedBasic")
    Instance<OidcEcho> omittedBasic;

    @CXFClient("omittedBearer")
    Instance<OidcEcho> omittedBearer;

    @CXFClient("disabledAnonymous")
    Instance<OidcEcho> disabledAnonymous;

    @CXFClient("disabledBasic")
    Instance<OidcEcho> disabledBasic;

    @CXFClient("disabledBearer")
    Instance<OidcEcho> disabledBearer;

    @CXFClient("acquireTls")
    Instance<OidcEcho> acquireTls;

    @CXFClient("wsdlAcquireOmitted")
    Instance<OidcEcho> wsdlAcquireOmitted;

    @CXFClient("wsdlAcquireFalse")
    Instance<OidcEcho> wsdlAcquireFalse;

    @CXFClient("wsdlAcquireSecure")
    Instance<OidcEcho> wsdlAcquireSecure;

    @CXFClient("wsdlAcquireTls")
    Instance<OidcEcho> wsdlAcquireTls;

    @CXFClient("conflictAcquireBasic")
    Instance<OidcEcho> conflictAcquireBasic;

    @CXFClient("conflictAcquireDigest")
    Instance<OidcEcho> conflictAcquireDigest;

    @CXFClient("propagateTls")
    Instance<OidcEcho> propagateTls;

    @CXFClient("wsdlPropagateOmitted")
    Instance<OidcEcho> wsdlPropagateOmitted;

    @CXFClient("wsdlPropagateFalse")
    Instance<OidcEcho> wsdlPropagateFalse;

    @CXFClient("wsdlPropagateSecure")
    Instance<OidcEcho> wsdlPropagateSecure;

    @CXFClient("wsdlPropagateTls")
    Instance<OidcEcho> wsdlPropagateTls;

    @CXFClient("conflictPropagateBasic")
    Instance<OidcEcho> conflictPropagateBasic;

    @CXFClient("conflictPropagateDigest")
    Instance<OidcEcho> conflictPropagateDigest;

    @CXFClient("exchangeTls")
    Instance<OidcEcho> exchangeTls;

    @CXFClient("wsdlExchangeOmitted")
    Instance<OidcEcho> wsdlExchangeOmitted;

    @CXFClient("wsdlExchangeFalse")
    Instance<OidcEcho> wsdlExchangeFalse;

    @CXFClient("wsdlExchangeSecure")
    Instance<OidcEcho> wsdlExchangeSecure;

    @CXFClient("wsdlExchangeTls")
    Instance<OidcEcho> wsdlExchangeTls;

    @CXFClient("conflictExchangeBasic")
    Instance<OidcEcho> conflictExchangeBasic;

    @CXFClient("conflictExchangeDigest")
    Instance<OidcEcho> conflictExchangeDigest;

    @CXFClient("incompatibleAcquire")
    Instance<OidcEcho> incompatibleAcquire;

    @CXFClient("incompatibleExchange")
    Instance<OidcEcho> incompatibleExchange;

    private final Map<String, OidcEcho> shared = new ConcurrentHashMap<>();

    public OidcEcho shared(String name) {
        return shared.computeIfAbsent(name, key -> supplier(key).get());
    }

    public synchronized OidcEcho fresh(String name) {
        // T-019--T-022 must actually fetch the WSDL, including after a valid caller warmed another proxy.
        ConfigProvider.getConfig().getOptionalValue("quarkus.cxf.client." + name + ".wsdl", String.class)
                .ifPresent(wsdl -> {
                    WSDLManager manager = BusFactory.getDefaultBus().getExtension(WSDLManager.class);
                    // CXF 4.2.4's CacheMap may retain identity aliases after removeDefinition(String).
                    // Use the value overload without creating another alias, until this URL is actually cold.
                    manager.getDefinitions().entrySet().stream().filter(entry -> wsdl.equals(entry.getKey()))
                            .map(Map.Entry::getValue).findFirst().ifPresent(definition -> {
                                do {
                                    manager.removeDefinition(definition);
                                } while (manager.getDefinitions().containsKey(wsdl));
                            });
                });
        return supplier(name).get();
    }

    public void destroy(String name, OidcEcho client) {
        supplier(name).destroy(client);
    }

    @PreDestroy
    void close() {
        shared.forEach(this::destroy);
    }

    private Instance<OidcEcho> supplier(String name) {
        return switch (name) {
            case "wsdlDefaultSecure" -> wsdlDefaultSecure;
            case "acquireDefault" -> acquireDefault;
            case "acquireNamed" -> acquireNamed;
            case "propagate" -> propagate;
            case "exchangeNamed" -> exchangeNamed;
            case "exchangeOther" -> exchangeOther;
            case "exchangeAudience" -> exchangeAudience;
            case "defaultClient" -> defaultClient;
            case "lifecycleNamed" -> lifecycleNamed;
            case "lifecycleExchange" -> lifecycleExchange;
            case "refresh" -> refresh;
            case "refreshFailure" -> refreshFailure;
            case "failureAcquire" -> failureAcquire;
            case "failureExchange" -> failureExchange;
            case "refusedAcquire" -> refusedAcquire;
            case "refusedExchange" -> refusedExchange;
            case "concurrentExchange" -> concurrentExchange;
            case "asyncAcquire" -> asyncAcquire;
            case "asyncExchange" -> asyncExchange;
            case "omittedAnonymous" -> omittedAnonymous;
            case "omittedBasic" -> omittedBasic;
            case "omittedBearer" -> omittedBearer;
            case "disabledAnonymous" -> disabledAnonymous;
            case "disabledBasic" -> disabledBasic;
            case "disabledBearer" -> disabledBearer;
            case "acquireTls" -> acquireTls;
            case "wsdlAcquireOmitted" -> wsdlAcquireOmitted;
            case "wsdlAcquireFalse" -> wsdlAcquireFalse;
            case "wsdlAcquireSecure" -> wsdlAcquireSecure;
            case "wsdlAcquireTls" -> wsdlAcquireTls;
            case "conflictAcquireBasic" -> conflictAcquireBasic;
            case "conflictAcquireDigest" -> conflictAcquireDigest;
            case "propagateTls" -> propagateTls;
            case "wsdlPropagateOmitted" -> wsdlPropagateOmitted;
            case "wsdlPropagateFalse" -> wsdlPropagateFalse;
            case "wsdlPropagateSecure" -> wsdlPropagateSecure;
            case "wsdlPropagateTls" -> wsdlPropagateTls;
            case "conflictPropagateBasic" -> conflictPropagateBasic;
            case "conflictPropagateDigest" -> conflictPropagateDigest;
            case "exchangeTls" -> exchangeTls;
            case "wsdlExchangeOmitted" -> wsdlExchangeOmitted;
            case "wsdlExchangeFalse" -> wsdlExchangeFalse;
            case "wsdlExchangeSecure" -> wsdlExchangeSecure;
            case "wsdlExchangeTls" -> wsdlExchangeTls;
            case "conflictExchangeBasic" -> conflictExchangeBasic;
            case "conflictExchangeDigest" -> conflictExchangeDigest;
            case "incompatibleAcquire" -> incompatibleAcquire;
            case "incompatibleExchange" -> incompatibleExchange;
            default -> throw new IllegalArgumentException("Unknown test client: " + name);
        };
    }
}
