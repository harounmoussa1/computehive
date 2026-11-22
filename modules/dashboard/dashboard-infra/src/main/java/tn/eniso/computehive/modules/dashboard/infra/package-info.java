/**
 * <p>Module <b>dashboard</b> — tableau de bord : vue d'ensemble des machines, des jobs et des quotas.</p>
 *
 * <p>Couche <b>infra</b> : les vues du tableau de bord, des records immuables. Elles sont composées des vues
 * publiques des autres modules ({@code JobInfo}, {@code MachineInfo}, {@code UsageInfo}, {@code AssignmentInfo}) :
 * le module n'a ni entité ni table.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.modules.dashboard.infra.DashboardSnapshot} (la vue d'ensemble),
 * {@link tn.eniso.computehive.modules.dashboard.infra.JobDetails} (un job et ses tentatives).</p>
 */
package tn.eniso.computehive.modules.dashboard.infra;
