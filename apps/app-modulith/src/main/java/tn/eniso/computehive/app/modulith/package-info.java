/**
 * <p>Application <b>app-modulith</b> — M/1 : tous les modules dans un seul processus.</p>
 *
 * <p>Seul endroit où l'on choisit les implémentations : {@code service-impl} local ou {@code service-rest-cli} distant, et les drivers des extensions.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.app.modulith.ModulithApplication} (scan du core et des modules ; drivers importés explicitement : broker mémoire, notifications dans les logs), {@code application.yml}, {@code logback-spring.xml} (logs lisibles, ou JSON avec le profil {@code json}).</p>
 */
package tn.eniso.computehive.app.modulith;
