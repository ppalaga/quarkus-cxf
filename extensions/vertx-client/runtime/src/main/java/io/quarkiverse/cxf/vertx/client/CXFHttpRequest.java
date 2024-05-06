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

package io.quarkiverse.cxf.vertx.client;

import java.net.URI;

import io.quarkiverse.cxf.vertx.client.AsyncHTTPConduit.AsyncWrappedOutputStream;
import io.vertx.core.http.RequestOptions;

public class CXFHttpRequest {
//    private AsyncWrappedOutputStream out;
    //    private RequestConfig config;

        private final RequestOptions options;

        public CXFHttpRequest(RequestOptions options) {
            super();
            this.options = options;
        }



    //    private static final long serialVersionUID = 1L;
//
    private MutableHttpEntity entity;
//
//    public void setOutputStream(AsyncWrappedOutputStream o) {
//        out = o;
//    }
//
//    public AsyncWrappedOutputStream getOutputStream() {
//        return out;
//    }
//
    public MutableHttpEntity getEntity() {
        return this.entity;
    }

    public void setEntity(final MutableHttpEntity entity) {
        this.entity = entity;
    }

//    @Override
//    public RequestConfig getConfig() {
//        return config;
//    }
//
//    public void setConfig(RequestConfig config) {
//        this.config = config;
//    }
//
//    @Override
//    public URI getUri() {
//        try {
//            return super.getUri();
//        } catch (final URISyntaxException ex) {
//            throw new IllegalArgumentException(ex.getMessage(), ex);
//        }
//    }

    public void removeHeaders(String string) {
        // TODO Auto-generated method stub

    }

    public void setOutputStream(AsyncWrappedOutputStream asyncWrappedOutputStream) {
        // TODO Auto-generated method stub

    }

    public URI getUri() {
        // TODO Auto-generated method stub
        return null;
    }

    public void addHeader(String cookie, String s) {
        // TODO Auto-generated method stub

    }

    public void setHeader(String key, String string) {
        // TODO Auto-generated method stub

    }

    public boolean containsHeader(String string) {
        // TODO Auto-generated method stub
        return false;
    }

    public RequestOptions getOptions() {
        return options;
    }

}
