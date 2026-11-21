package tn.eniso.computehive.modules.jobs.infra;

/**
 * État d'un job, décidé uniquement par le serveur.
 *
 * <ul>
 *   <li>{@code PENDING} : soumis, en attente d'une machine ;</li>
 *   <li>{@code RUNNING} : en cours sur une machine ;</li>
 *   <li>{@code REASSIGNED} : sa machine est tombée en panne ; en attente d'une autre machine,
 *       il reprendra depuis son dernier checkpoint ;</li>
 *   <li>{@code DONE}, {@code FAILED}, {@code CANCELLED} : états finaux.</li>
 * </ul>
 *
 * <pre>
 *   PENDING ──► RUNNING ──► DONE
 *      │         │  │ └───► FAILED ◄──┐
 *      │         │  └─────► REASSIGNED ──► RUNNING
 *      └─────────┴──────────────┴─────► CANCELLED
 * </pre>
 */
public enum JobStatus {
    PENDING,
    RUNNING,
    REASSIGNED,
    DONE,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DONE || this == FAILED || this == CANCELLED;
    }

    /** {@code true} si le job attend qu'on lui attribue une machine. */
    public boolean isWaitingForMachine() {
        return this == PENDING || this == REASSIGNED;
    }

    public boolean canMoveTo(JobStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING || next == CANCELLED;
            case RUNNING -> next == DONE || next == FAILED || next == CANCELLED || next == REASSIGNED;
            case REASSIGNED -> next == RUNNING || next == FAILED || next == CANCELLED;
            case DONE, FAILED, CANCELLED -> false;
        };
    }
}
