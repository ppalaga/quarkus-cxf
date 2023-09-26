package io.quarkiverse.cxf.ws.security;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.jboss.logging.Logger;

import io.quarkiverse.cxf.CXFClientInfo;
import io.quarkiverse.cxf.CxfClientProducer.JaxWsProxyFactoryBeanCustomizer;
import io.quarkiverse.cxf.ws.security.CxfWsSecurityConfig.CryptoConfig;
import io.quarkiverse.cxf.ws.security.CxfWsSecurityConfig.MerlinConfig;
import io.quarkiverse.cxf.ws.security.CxfWsSecurityConfig.WsSecurityAction;
import io.quarkiverse.cxf.ws.security.CxfWsSecurityConfig.WsSecurityClientConfig;

@ApplicationScoped
public class WssJaxWsProxyFactoryBeanCustomizer implements JaxWsProxyFactoryBeanCustomizer {
    private static final Logger log = Logger.getLogger(WssJaxWsProxyFactoryBeanCustomizer.class);
    @Inject
    CxfWsSecurityConfig config;

    @Override
    public void customize(Context context, CXFClientInfo cxfClientInfo, JaxWsProxyFactoryBean factory) {

        final String key = cxfClientInfo.getConfigKey();
        if (key != null && config.isClientPresent(key)) {
            final List<Interceptor<? extends Message>> outInterceptors = factory.getOutInterceptors();
            final WsSecurityClientConfig wssConfig = config.getClient(key).wsSecurity();
            final List<WsSecurityAction> actions = wssConfig.actions();
            if (actions.isEmpty()) {
                log.debugf("No actions configured for client \"%s\", thus not adding an %s interceptor to it", key,
                        WSS4JOutInterceptor.class.getSimpleName());
                return;
            }
            if (outInterceptors.stream()
                    .anyMatch(i -> i instanceof WSS4JOutInterceptor)) {
                throw new IllegalStateException(WSS4JOutInterceptor.class.getSimpleName() + " already present in client \""
                        + key + "\". Either remove the ");
            }

            final Map<String, Object> props = new LinkedHashMap<>();

            props.put(
                    ConfigurationConstants.ACTION,
                    actions.stream().map(WsSecurityAction::name).collect(Collectors.joining(" ")));

            consumeAnnotated(context, WsSecurityClientConfig.class, wssConfig, props::put);

            final WSS4JOutInterceptor wss4jOutInterceptor = new WSS4JOutInterceptor(props);

            outInterceptors.add(wss4jOutInterceptor);
        }
    }

    static void consumeAnnotated(Context context, Class<?> cl, Object config, BiConsumer<String, Object> consumer) {
        for (Method method : cl.getDeclaredMethods()) {
            final WssConfigurationConstant wssConfigurationConstant = method.getAnnotation(WssConfigurationConstant.class);
            if (wssConfigurationConstant != null) {
                Object value = null;
                try {
                    method.invoke(config);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new RuntimeException("Could not invoke " + cl.getName() + "." + method.getName() + "()", e);
                }
                if (value instanceof Optional && ((Optional<?>) value).isPresent()) {
                    value = ((Optional<?>) value).get();
                } else {
                    continue;
                }
                final String propKey = wssConfigurationConstant.key() != null && !wssConfigurationConstant.key().isEmpty()
                        ? wssConfigurationConstant.key()
                        : method.getName();
                switch (wssConfigurationConstant.transformer()) {
                    case toString:
                        consumer.accept(propKey, consumer);
                        break;
                    case beanRef:
                        consumer.accept(propKey, context.lookUpBean((String) value));
                        break;
                    case crypto:
                        final CryptoConfig cryptoConfig = (CryptoConfig) value;
                        toCrypto(context, cryptoConfig).ifPresent(crypto -> consumer.accept(propKey, crypto));
                        break;
                    default:
                        throw new IllegalStateException("Unexpected "
                                + io.quarkiverse.cxf.ws.security.WssConfigurationConstant.Transformer.class.getName() + ": "
                                + wssConfigurationConstant.transformer());
                }
            }
        }
    }

    static Optional<Properties> toCrypto(Context context, CryptoConfig cryptoConfig) {
        if (CryptoConfig.MERLIN_PROVIDER.equals(cryptoConfig.provider())) {
            if (isConfigured(cryptoConfig.merlin())) {
                Properties props = new Properties();
                props.put("org.apache.wss4j.crypto.provider", cryptoConfig.provider());

                consumeAnnotated(
                        context,
                        MerlinConfig.class,
                        cryptoConfig.merlin(),
                        (key, val) -> props.put("org.apache.wss4j.crypto.merlin." + key, val));
                return Optional.of(props);
            } else {
                return Optional.empty();
            }
        } else {
            Properties props = new Properties();
            props.put("org.apache.wss4j.crypto.provider", cryptoConfig.provider());
            cryptoConfig.properties().entrySet().stream().forEach(en -> props.put(en.getKey(), en.getValue()));
            return Optional.of(props);
        }
    }

    static boolean isConfigured(MerlinConfig merlin) {
        return Stream.<Supplier<Optional<String>>> of(
                merlin::x509crlFile,
                merlin::keystoreProvider,
                merlin::certProvider,
                merlin::keystoreFile,
                merlin::keystorePassword,
                merlin::keystoreType,
                merlin::keystoreAlias,
                merlin::keystorePrivatePassword,
                merlin::truststoreFile,
                merlin::truststorePassword,
                merlin::truststoreType,
                merlin::truststoreProvider)
                .map(Supplier::get)
                .anyMatch(Optional::isPresent);
    }

}
