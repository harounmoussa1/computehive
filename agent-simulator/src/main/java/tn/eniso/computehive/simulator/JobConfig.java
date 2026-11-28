package tn.eniso.computehive.simulator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture des quelques réglages numériques dont l'entraînement simulé a besoin dans la config JSON d'un job
 * « code + config + données » ({@code LaunchJob.config_json}, ADR 0013). Le serveur n'interprète pas cette
 * config ; le simulateur, qui joue le rôle du script, y cherche seulement {@code epochs} et
 * {@code learning_rate}, au premier niveau. Pas de bibliothèque JSON : le simulateur ne dépend que de
 * {@code worker-proto}.
 */
final class JobConfig {

    static final int DEFAULT_EPOCHS = 10;
    static final double DEFAULT_LEARNING_RATE = 0.01;

    private JobConfig() {
    }

    static int epochs(String configJson) {
        String value = number(configJson, "epochs");
        try {
            int epochs = value == null ? DEFAULT_EPOCHS : Integer.parseInt(value);
            return epochs > 0 ? epochs : DEFAULT_EPOCHS;
        } catch (NumberFormatException e) {
            return DEFAULT_EPOCHS;
        }
    }

    static double learningRate(String configJson) {
        String value = number(configJson, "learning_rate");
        try {
            return value == null ? DEFAULT_LEARNING_RATE : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return DEFAULT_LEARNING_RATE;
        }
    }

    private static String number(String configJson, String key) {
        if (configJson == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9][0-9.eE+-]*)")
                .matcher(configJson);
        return matcher.find() ? matcher.group(1) : null;
    }
}
