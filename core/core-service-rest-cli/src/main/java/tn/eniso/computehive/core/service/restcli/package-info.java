/**
 * <p><b>core-service-rest-cli</b> — socle commun des proxys {@code service-rest-cli} (mode M/P).</p>
 *
 * <p>Un proxy implémente la façade d'un module qui tourne dans une autre application ; il appelle ses endpoints
 * internes ({@code /internal/**}) à travers {@link tn.eniso.computehive.core.service.restcli.RemoteModuleClient},
 * qui ajoute le jeton interne et l'identifiant de corrélation, applique Resilience4j (retries, circuit breaker) et
 * relève les erreurs métier du module distant à l'identique. L'appelant ne voit donc pas la différence avec M/1.</p>
 */
package tn.eniso.computehive.core.service.restcli;
