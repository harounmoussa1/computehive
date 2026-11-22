/**
 * <p>Module <b>dashboard</b> — tableau de bord : vue d'ensemble des machines, des jobs et des quotas.</p>
 *
 * <p>Couche <b>service-impl</b> : implémentation de la façade. Le module n'a pas de données, donc ni repository
 * ni service CRUD : il lit uniquement les façades de jobs, machines, quotas et scheduling.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.dashboard.service.impl.DashboardModuleImpl}.</p>
 */
package tn.eniso.computehive.modules.dashboard.service.impl;
