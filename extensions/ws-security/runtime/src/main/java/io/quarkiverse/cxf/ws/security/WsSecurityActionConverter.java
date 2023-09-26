package io.quarkiverse.cxf.ws.security;

import io.quarkiverse.cxf.AbstractEnumConverter;
import io.quarkiverse.cxf.ws.security.CxfWsSecurityConfig.WsSecurityAction;

public class WsSecurityActionConverter extends AbstractEnumConverter<WsSecurityAction> {

    private static final long serialVersionUID = 1L;

    public WsSecurityActionConverter() {
        super(WsSecurityAction.class);
    }

    @Override
    public WsSecurityAction convert(String value) {
        return super.convert(value);
    }

}
