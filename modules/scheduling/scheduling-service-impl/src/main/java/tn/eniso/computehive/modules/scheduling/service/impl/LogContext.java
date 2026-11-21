package tn.eniso.computehive.modules.scheduling.service.impl;

import org.slf4j.MDC;

import java.util.function.Supplier;

/** Place une clé dans le MDC (logs JSON) le temps d'une opération, puis rétablit la valeur précédente. */
final class LogContext {

    private LogContext() {
    }

    static <T> T with(String key, String value, Supplier<T> action) {
        String previous = MDC.get(key);
        if (value != null) {
            MDC.put(key, value);
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        }
    }

    static void with(String key, String value, Runnable action) {
        with(key, value, () -> {
            action.run();
            return null;
        });
    }
}
