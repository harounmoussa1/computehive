package tn.eniso.computehive.modules.scheduling.infra;

/**
 * État d'une tentative d'exécution ({@link Assignment}).
 *
 * <ul>
 *   <li>{@code LAUNCHING} : {@code LaunchJob} envoyé à l'agent, en attente de son {@code CommandAck} ;</li>
 *   <li>{@code RUNNING} : l'agent a accepté et exécute le job ;</li>
 *   <li>{@code COMPLETED}, {@code FAILED} : l'agent a terminé ({@code Finished}) avec succès ou en échec ;</li>
 *   <li>{@code STOPPED} : arrêtée sur ordre du serveur (annulation, déplacement) ;</li>
 *   <li>{@code REJECTED} : l'agent a refusé le {@code LaunchJob} ({@code CommandAck(accepted = false)}) ;</li>
 *   <li>{@code LOST} : la machine est tombée en panne, ou ne fait plus tourner le job.</li>
 * </ul>
 *
 * <pre>
 *   LAUNCHING ──► RUNNING ──► COMPLETED | FAILED | STOPPED | LOST
 *       └──► REJECTED, ou directement COMPLETED | FAILED | STOPPED | LOST (CommandAck perdu)
 * </pre>
 */
public enum AssignmentStatus {
    LAUNCHING,
    RUNNING,
    COMPLETED,
    FAILED,
    STOPPED,
    REJECTED,
    LOST;

    /** {@code true} tant que la tentative peut encore tourner sur sa machine. */
    public boolean isActive() {
        return this == LAUNCHING || this == RUNNING;
    }

    public boolean canMoveTo(AssignmentStatus next) {
        return switch (this) {
            case LAUNCHING -> next != LAUNCHING;
            case RUNNING -> next != LAUNCHING && next != RUNNING && next != REJECTED;
            case COMPLETED, FAILED, STOPPED, REJECTED, LOST -> false;
        };
    }
}
