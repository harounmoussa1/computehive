package tn.eniso.computehive.modules.jobs.service.api;

import tn.eniso.computehive.modules.jobs.infra.JobCheckpoint;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobProgress;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatusUpdate;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;

import java.util.List;
import java.util.Optional;

/**
 * Façade du module jobs : son unique contrat public.
 *
 * <p>Appelée par la couche ws-rest (soumission, annulation, consultation) et par {@code scheduling}
 * (démarrage, avancement, checkpoints, fin). {@code scheduling} vérifie la tentative
 * ({@code attemptId}, fencing) <b>avant</b> d'appeler cette façade : un message périmé n'arrive jamais ici.</p>
 *
 * <p>Toutes les méthodes qui modifient un job lèvent
 * {@link tn.eniso.computehive.core.infra.exception.NotFoundException} si le job est inconnu, et
 * {@link tn.eniso.computehive.core.infra.exception.ConflictException} si son statut ne permet pas l'opération.</p>
 */
public interface JobsModule {

    /** Soumet un job ; il est {@code PENDING} et attend une machine. */
    JobInfo submit(JobSubmission submission);

    /**
     * Annule un job pas encore terminé. Sans effet s'il est déjà annulé.
     * S'il tournait, sa machine reste indiquée pour que {@code scheduling} arrête l'agent.
     */
    JobInfo cancel(String jobId);

    Optional<JobInfo> findById(String jobId);

    /** Jobs qui respectent tous les critères non {@code null}, du plus ancien au plus récent. */
    List<JobInfo> findByQuery(JobQuery query);

    /** Change le statut d'un job : démarrage, réattribution, fin réussie ou échec. */
    JobInfo updateStatus(String jobId, JobStatusUpdate update);

    /** Enregistre l'avancement d'un job {@code RUNNING}. */
    JobInfo updateProgress(String jobId, JobProgress progress);

    /**
     * Enregistre le nouveau checkpoint officiel d'un job {@code RUNNING}, déjà vérifié par
     * {@code scheduling}. Sans effet si c'est déjà le checkpoint officiel.
     */
    JobInfo recordCheckpoint(String jobId, JobCheckpoint checkpoint);
}
