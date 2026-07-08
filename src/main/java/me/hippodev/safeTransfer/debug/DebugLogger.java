package me.hippodev.safeTransfer.debug;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin wrapper around the plugin's Logger that only prints when
 * {@code debug: true} is set in config.yml - keeps normal operation quiet
 * while allowing verbose SRV/CNAME-hop and ping diagnostics on demand.
 */
public final class DebugLogger {

    private final Logger logger;
    private volatile boolean enabled;

    public DebugLogger(Logger logger, boolean enabled) {
        this.logger = logger;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String message) {
        if (enabled) {
            logger.info("[debug] " + message);
        }
    }

    public void log(String message, Throwable throwable) {
        if (enabled) {
            logger.log(Level.WARNING, "[debug] " + message, throwable);
        }
    }
}
