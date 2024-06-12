/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.quarkiverse.cxf.vertx.web.client;

import java.io.IOException;

import org.apache.cxf.Bus;
import org.apache.cxf.common.injection.NoJSR250Annotations;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.apache.cxf.transport.http.HTTPTransportFactory;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.client.WebClient;

/**
 *
 */
@NoJSR250Annotations
public class VertxWebClientHTTPConduitFactory implements HTTPConduitFactory {

    private final HttpClient httpClient;

    VertxWebClientHTTPConduitFactory() {
        super();
        InstanceHandle<Vertx> vertx = Arc.container().instance(Vertx.class);
        if (!vertx.isAvailable()) {
            throw new IllegalStateException(Vertx.class.getName() + " not available in Arc");
        }
        this.httpClient = vertx.get().createHttpClient();
    }

    @Override
    public HTTPConduit createConduit(HTTPTransportFactory f, Bus b, EndpointInfo localInfo, EndpointReferenceType target)
            throws IOException {
        return new VertxWebClientHTTPConduit(b, localInfo, httpClient);
    }

}
