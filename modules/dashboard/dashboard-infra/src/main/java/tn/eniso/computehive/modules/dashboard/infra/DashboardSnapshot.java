package tn.eniso.computehive.modules.dashboard.infra;

import tn.eniso.computehive.core.infra.util.Checks;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.quotas.infra.UsageInfo;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tout ce qu'affiche le tableau de bord, lu en une fois : une seule requête par rafraîchissement.
 *
 * @param generatedAt        instant de la lecture, selon l'horloge du serveur : la page calcule « il y a 3 s »
 *                           par rapport à lui, pas à l'horloge du navigateur
 * @param machinesByStatus   nombre de machines par statut (tous les statuts présents, éventuellement à 0)
 * @param jobsByStatus       nombre de jobs par statut, sur <b>tous</b> les jobs
 * @param machines           toutes les machines, triées par nom
 * @param jobs               jobs affichés, du plus récent au plus ancien : tous les jobs pas encore terminés, et
 *                           les derniers jobs terminés
 * @param hiddenFinishedJobs nombre de jobs terminés plus anciens, non affichés
 * @param ranking            consommations, de la plus forte à la plus faible
 * @param jobTypes           types de jobs que l'on peut soumettre
 */
public record DashboardSnapshot(Instant generatedAt,
                                Map<MachineStatus, Integer> machinesByStatus,
                                Map<JobStatus, Integer> jobsByStatus,
                                List<MachineInfo> machines,
                                List<JobInfo> jobs,
                                int hiddenFinishedJobs,
                                List<UsageInfo> ranking,
                                List<JobType> jobTypes) {

    public DashboardSnapshot {
        Checks.requireNonNull(generatedAt, "generatedAt");
        machinesByStatus = Collections.unmodifiableMap(new EnumMap<>(machinesByStatus));
        jobsByStatus = Collections.unmodifiableMap(new EnumMap<>(jobsByStatus));
        machines = List.copyOf(machines);
        jobs = List.copyOf(jobs);
        ranking = List.copyOf(ranking);
        jobTypes = List.copyOf(jobTypes);
        Checks.require(hiddenFinishedJobs >= 0, "hiddenFinishedJobs must not be negative");
    }
}
