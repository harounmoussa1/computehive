package tn.eniso.computehive.modules.scheduling.infra.agent;

/** Sort d'un message d'agent traité par {@code SchedulingModule}. */
public enum MessageOutcome {
    /** Message appliqué. */
    ACCEPTED,
    /** {@code message_id} déjà traité : ignoré (renvoi de l'outbox de l'agent). */
    DUPLICATE,
    /** Tentative périmée, inconnue ou terminée : ignoré, et {@code StopJob(STALE_ATTEMPT)} envoyé à l'agent. */
    STALE,
    /** Message sans objet (ex. accusé de réception d'un ordre inconnu) : ignoré. */
    IGNORED
}
