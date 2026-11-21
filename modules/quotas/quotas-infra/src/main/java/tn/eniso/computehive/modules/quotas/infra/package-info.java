/**
 * <p>Module <b>quotas</b> — consommation gpu par utilisateur et priorité de placement.</p>
 *
 * <p>Couche <b>infra</b> : entités du domaine, DTO et énumérations. Base de la pyramide, elle ne dépend que de {@code core-infra}.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.quotas.infra.Usage} (consommation cumulée d'un utilisateur, {@code UserRef}), {@code UsageInfo} (vue immuable), {@code UsagePolicy} (priorité = 1 / (1 + heures GPU + heures CPU / 10)).</p>
 */
package tn.eniso.computehive.modules.quotas.infra;
