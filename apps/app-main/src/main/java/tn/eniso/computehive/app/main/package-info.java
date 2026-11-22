/**
 * <p>Application <b>app-main</b> — M/P, processus principal : jobs, machines et quotas ; scheduling appelé à distance.</p>
 *
 * <p>Seul endroit où l'on choisit les implémentations : {@code service-impl} local ou {@code service-rest-cli} distant, et les drivers des extensions.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.app.main.MainApplication} ; configuration dans {@code application.yml},
 * déploiement dans le {@code docker-compose.yml} du dépôt (ADR 0010).</p>
 */
package tn.eniso.computehive.app.main;
