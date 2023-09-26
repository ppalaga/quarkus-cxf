package io.quarkiverse.cxf;

import org.apache.cxf.transports.http.configuration.ConnectionType;

public class ConnectionTypeConverter extends AbstractEnumConverter<ConnectionType> {

    private static final long serialVersionUID = 1L;

    public ConnectionTypeConverter() {
        super(ConnectionType.class, ConnectionType::value);
    }

    @Override
    public ConnectionType convert(String value) {
        return super.convert(value);
    }

}
