package tn.eniso.computehive.core.infra.util;

import tn.eniso.computehive.core.infra.exception.ValidationException;

/**
 * Vérifications d'arguments qui lèvent une {@link ValidationException} (erreur métier, HTTP 400)
 * plutôt qu'une {@link IllegalArgumentException} générique.
 */
public final class Checks {

    private Checks() {
    }

    public static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new ValidationException(name + " must not be null");
        }
        return value;
    }

    public static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(name + " must not be blank");
        }
        return value;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new ValidationException(message);
        }
    }
}
