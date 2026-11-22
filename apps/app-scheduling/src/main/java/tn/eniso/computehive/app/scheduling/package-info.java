/**
 * <p>Application <b>app-scheduling</b> — M/P, processus extrait : scheduling et la gateway gRPC des agents ; jobs, machines et quotas appelés à distance.</p>
 *
 * <p>Seul endroit où l'on choisit les implémentations : {@code service-impl} local ou {@code service-rest-cli} distant, et les drivers des extensions.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.app.scheduling.SchedulingApplication} ; configuration dans
 * {@code application.yml}, déploiement dans le {@code docker-compose.yml} du dépôt (ADR 0010).</p>
 */
package tn.eniso.computehive.app.scheduling;
