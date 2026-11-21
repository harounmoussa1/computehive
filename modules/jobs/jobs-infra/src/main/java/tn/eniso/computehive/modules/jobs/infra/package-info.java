/**
 * <p>Module <b>jobs</b> — cycle de vie des jobs soumis par les utilisateurs.</p>
 *
 * <p>Couche <b>infra</b> : entités du domaine, DTO et énumérations. Base de la pyramide, elle ne dépend que de {@code core-infra}.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.jobs.infra.Job} (entité modifiable, applique les règles de statut), {@code JobStatus} (machine à états), {@code JobType}, et les DTO immuables {@code JobInfo}, {@code JobSubmission}, {@code JobParams}, {@code JobRequirements}, {@code JobProgress}, {@code JobCheckpoint}, {@code JobStatusUpdate}, {@code JobQuery}.</p>
 */
package tn.eniso.computehive.modules.jobs.infra;
