/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>infra</b> : entités du domaine, DTO et énumérations. Base de la pyramide, elle ne dépend que de {@code core-infra}.</p>
 *
 * <p>Contenu prévu : {@code Job} (owner {@code UserRef}, type prédéfini, params, requirements, status, machine {@code MachineRef}, progress, lastLoss, lastCheckpointEpoch, version), {@code JobStatus} (PENDING, RUNNING, REASSIGNED, DONE, FAILED, CANCELLED), {@code JobType}.</p>
 */
package tn.eniso.computehive.modules.jobs.infra;
