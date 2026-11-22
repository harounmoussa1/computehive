package tn.eniso.computehive.extensions.checkpoints.api;

import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.core.infra.util.Checks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clé d'un checkpoint : {@code <jobId>/attempts/<attemptId>/epochs/<epoch>}.
 *
 * <ul>
 *   <li>Elle contient la tentative : un agent périmé ne peut jamais écraser le checkpoint d'une tentative plus
 *       récente, il écrit forcément sous sa propre tentative (docs/worker-protocol.md §8).</li>
 *   <li>Son format est strict (identifiant de job sans {@code /} ni {@code ..}) : une clé ne peut pas sortir
 *       de l'espace de stockage.</li>
 * </ul>
 *
 * @param jobId     job concerné
 * @param attemptId tentative qui a écrit le checkpoint, à partir de 1
 * @param epoch     epoch après laquelle il a été écrit, à partir de 1
 */
public record CheckpointKey(String jobId, long attemptId, int epoch) {

    private static final Pattern JOB_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern KEY = Pattern.compile("([^/]+)/attempts/(\\d{1,18})/epochs/(\\d{1,9})");

    public CheckpointKey {
        Checks.require(jobId != null && JOB_ID.matcher(jobId).matches(),
                "checkpoint jobId must be 1 to 64 letters, digits, '-' or '_': " + jobId);
        Checks.require(attemptId >= 1, "checkpoint attemptId must start at 1");
        Checks.require(epoch >= 1, "checkpoint epoch must start at 1");
    }

    /** Relit une clé écrite par {@link #path()} (ex. la {@code storage_key} de {@code CheckpointSaved}). */
    public static CheckpointKey parse(String key) {
        Matcher matcher = key == null ? null : KEY.matcher(key);
        if (matcher == null || !matcher.matches()) {
            throw new ValidationException("checkpoint key must look like <jobId>/attempts/<n>/epochs/<n>: " + key);
        }
        return new CheckpointKey(matcher.group(1), Long.parseLong(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    }

    public String path() {
        return jobId + "/attempts/" + attemptId + "/epochs/" + epoch;
    }

    @Override
    public String toString() {
        return path();
    }
}
