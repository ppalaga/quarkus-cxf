package io.quarkiverse.cxf.oidc;

import java.util.logging.Filter;

import org.jboss.logmanager.Logger;

import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OidcRecorder {
    public void protectTokenRequestLogs(ShutdownContext shutdown) {
        Logger logger = Logger.getLogger("io.quarkus.oidc.client.runtime.OidcClientImpl");
        Filter previous = logger.getFilter();
        Filter redacting = record -> {
            // Beta2 masks client secrets and refresh tokens, but its DEBUG request dump still includes subject_token.
            // This dump is emitted before OidcRequestFilter, so redact it at the logger, before any handler sees it.
            String message = record.getMessage();
            if (message != null && message.contains("token: url :") && message.contains("request params:")) {
                record.setMessage("OIDC token request (authentication details redacted)");
                record.setParameters(null);
            }
            return previous == null || previous.isLoggable(record);
        };
        logger.setFilter(redacting);
        shutdown.addLastShutdownTask(() -> {
            if (logger.getFilter() == redacting) {
                logger.setFilter(previous);
            }
        });
    }
}
