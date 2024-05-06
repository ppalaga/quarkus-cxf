package io.quarkiverse.cxf.vertx.client;

public interface Header {

    /**
     * Returns {@code true} if the header should be considered sensitive.
     * <p>
     * Some encoding schemes such as HPACK impose restrictions on encoded
     * representation of sensitive headers.
     * </p>
     *
     * @return {@code true} if the header should be considered sensitive.
     *
     * @since 5.0
     */
    boolean isSensitive();

    /**
     * Gets the name of this pair.
     *
     * @return the name of this pair, never {@code null}.
     */
    String getName();

    /**
     * Gets the value of this pair.
     *
     * @return the value of this pair, may be {@code null}.
     */
    String getValue();

}
