package io.quarkiverse.cxf;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.apache.cxf.common.logging.AbstractDelegatingLogger;
import org.jboss.logging.Logger;

/**
 * <p>
 * java.util.logging.Logger implementation delegating to JBoss Logging. Adapted from org.apache.cxf.common.logging.Slf4jLogger
 * </p>
 * <p>
 * Methods {@link java.util.logging.Logger#setParent(Logger)}, {@link java.util.logging.Logger#getParent()},
 * {@link java.util.logging.Logger#setUseParentHandlers(boolean)} and
 * {@link java.util.logging.Logger#getUseParentHandlers()} are not overridden.
 * </p>
 * <p>
 * Level mapping inspired by {@link org.slf4j.bridge.SLF4JBridgeHandler}:
 * </p>
 *
 * <pre>
 * FINEST  -&gt; TRACE
 * FINER   -&gt; DEBUG
 * FINE    -&gt; DEBUG
 * CONFIG  -&gt; DEBUG
 * INFO    -&gt; INFO
 * WARN ING -&gt; WARN
 * SEVER   -&gt; ERROR
 * </pre>
 */
public class JBossLoggingLogger extends AbstractDelegatingLogger {

    private final Logger logger;

    public JBossLoggingLogger(String name, String resourceBundleName) {
        super(name, resourceBundleName);
        logger = Logger.getLogger(name);
    }

    @Override
    protected boolean supportsHandlers() {
        return true;
    }

    @Override
    public Level getLevel() {
        Level level;
        // Verify from the wider (trace) to the narrower (error)
        if (logger.isTraceEnabled()) {
            level = Level.FINEST;
        } else if (logger.isDebugEnabled()) {
            // map to the lowest between FINER, FINE and CONFIG
            level = Level.FINER;
        } else if (logger.isInfoEnabled()) {
            level = Level.INFO;
        } else if (logger.isEnabled(Logger.Level.WARN)) {
            level = Level.WARNING;
        } else if (logger.isEnabled(Logger.Level.ERROR)) {
            level = Level.SEVERE;
        } else {
            level = Level.OFF;
        }
        return level;
    }

    @Override
    public boolean isLoggable(Level level) {
        final int i = level.intValue();
        if (i == Level.OFF.intValue()) {
            return false;
        } else if (i >= Level.SEVERE.intValue()) {
            return logger.isEnabled(Logger.Level.ERROR);
        } else if (i >= Level.WARNING.intValue()) {
            return logger.isEnabled(Logger.Level.WARN);
        } else if (i >= Level.INFO.intValue()) {
            return logger.isInfoEnabled();
        } else if (i >= Level.FINER.intValue()) {
            return logger.isDebugEnabled();
        }
        return logger.isTraceEnabled();
    }

    @Override
    protected void internalLogFormatted(String msg, LogRecord record) {

        Level level = record.getLevel();
        Throwable t = record.getThrown();

        if (msg != null && msg.startsWith("")) {
            msg = "";
            record.setMessage(record.getMessage().replace("", ""));
        }

        Handler[] targets = getHandlers();
        if (targets != null) {
            for (Handler h : targets) {
                h.publish(record);
            }
        }
        if (!getUseParentHandlers()) {
            return;
        }

        /*
         * As we can not use a "switch ... case" block but only a "if ... else if ..." block, the order of the
         * comparisons is important. We first try log level FINE then INFO, WARN, FINER, etc
         */
        if (Level.FINE.equals(level)) {
            logger.debug(msg, t);
        } else if (Level.INFO.equals(level)) {
            logger.info(msg, t);
        } else if (Level.WARNING.equals(level)) {
            logger.warn(msg, t);
        } else if (Level.FINER.equals(level)) {
            logger.trace(msg, t);
        } else if (Level.FINEST.equals(level)) {
            logger.trace(msg, t);
        } else if (Level.ALL.equals(level)) {
            // should never occur, all is used to configure java.util.logging
            // but not accessible by the API Logger.xxx() API
            logger.error(msg, t);
        } else if (Level.SEVERE.equals(level)) {
            logger.error(msg, t);
        } else if (Level.CONFIG.equals(level)) {
            logger.debug(msg, t);
        } else if (Level.OFF.equals(level)) {
            // don't log
        }
    }
}
