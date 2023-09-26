package io.quarkiverse.cxf.ws.security;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.apache.wss4j.common.ConfigurationConstants;

@Target({ METHOD })
@Retention(RUNTIME)
public @interface WssConfigurationConstant {
    /**
     * The name of a constant from {@link ConfigurationConstants} to which the annotated method should be mapped.
     *
     * @return the name of a constant from {@link ConfigurationConstants} to which the annotated method should be mapped
     */
    String key() default "";

    /**
     * The kind of transformer that should be used to map a {@link Wsdl2JavaParameterSet} attribute value to a
     * command line option string
     *
     * @return kind of transformer that should be used to map a {@link Wsdl2JavaParameterSet} attribute value to a
     *         command line option string
     */
    Transformer transformer() default Transformer.toString;

    public enum Transformer {
        /** Calls the given type's {@code toString()} on the given value */
        toString,
        beanRef,
        crypto;
    }

}
