package io.quarkiverse.cxf.vertx.client;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.cxf.Bus;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public class VertxHttpClientConduit extends HTTPConduit {

    public VertxHttpClientConduit(Bus b, EndpointInfo ei, EndpointReferenceType t) throws IOException {
        super(b, ei, t);
    }

    @Override
    protected void setupConnection(Message message, Address address, HTTPClientPolicy csPolicy) throws IOException {

    }

    @Override
    protected OutputStream createOutputStream(Message message, boolean needToCacheRequest, boolean isChunking,
            int chunkThreshold) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

}
