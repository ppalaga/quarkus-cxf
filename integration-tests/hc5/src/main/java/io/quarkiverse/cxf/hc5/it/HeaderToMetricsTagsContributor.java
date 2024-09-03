package io.quarkiverse.cxf.hc5.it;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.quarkiverse.cxf.hc5.it.HeaderToMetricsTagRequestFilter.RequestScopedHeader;
import io.quarkus.micrometer.runtime.HttpServerMetricsTagsContributor;

@Singleton
public class HeaderToMetricsTagsContributor implements HttpServerMetricsTagsContributor {

    @Inject
    RequestScopedHeader requestScopedHeader;

    @Override
    public Tags contribute(Context context) {
        final String val = requestScopedHeader.getHeaderValue();
        if (val != null) {
            return Tags.of(Tag.of("my-header", val));
        }
        return Tags.empty();
    }
}
