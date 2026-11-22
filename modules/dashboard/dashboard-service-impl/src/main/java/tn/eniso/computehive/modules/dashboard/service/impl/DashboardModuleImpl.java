package tn.eniso.computehive.modules.dashboard.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.core.infra.exception.RemoteModuleUnavailableException;
import tn.eniso.computehive.modules.dashboard.infra.DashboardSnapshot;
import tn.eniso.computehive.modules.dashboard.infra.JobDetails;
import tn.eniso.computehive.modules.dashboard.service.api.DashboardModule;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobQuery;
import tn.eniso.computehive.modules.jobs.infra.JobStatus;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.jobs.service.api.JobsModule;
import tn.eniso.computehive.modules.machines.infra.MachineInfo;
import tn.eniso.computehive.modules.machines.infra.MachineStatus;
import tn.eniso.computehive.modules.machines.service.api.MachinesModule;
import tn.eniso.computehive.modules.quotas.service.api.QuotasModule;
import tn.eniso.computehive.modules.scheduling.service.api.SchedulingModule;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Implémentation de {@link DashboardModule} : ne lit que les façades des autres modules (façade → façade), et
 * n'a donc ni repository ni service CRUD. Le même code tourne en M/1 et en M/P, où {@link SchedulingModule} est le
 * proxy vers {@code app-scheduling}.
 */
@Service
public class DashboardModuleImpl implements DashboardModule {

    /** Jobs terminés affichés au plus : les plus récents. Les jobs en cours sont toujours tous affichés. */
    static final int RECENT_FINISHED_JOBS = 25;

    private static final Logger log = LoggerFactory.getLogger(DashboardModuleImpl.class);

    private final JobsModule jobs;
    private final MachinesModule machines;
    private final QuotasModule quotas;
    private final SchedulingModule scheduling;
    private final Clock clock;

    public DashboardModuleImpl(JobsModule jobs, MachinesModule machines, QuotasModule quotas,
                               SchedulingModule scheduling, Clock clock) {
        this.jobs = jobs;
        this.machines = machines;
        this.quotas = quotas;
        this.scheduling = scheduling;
        this.clock = clock;
    }

    @Override
    public DashboardSnapshot snapshot() {
        List<JobInfo> allJobs = jobs.findByQuery(new JobQuery(null, null));
        List<MachineInfo> allMachines = machines.findAll();

        // Du plus récent au plus ancien : tous les jobs pas encore terminés, et les derniers jobs terminés.
        List<JobInfo> shown = new ArrayList<>();
        int finished = 0;
        for (JobInfo job : allJobs.stream().sorted(Comparator.comparing(JobInfo::submittedAt).reversed()).toList()) {
            if (!job.status().isTerminal() || finished++ < RECENT_FINISHED_JOBS) {
                shown.add(job);
            }
        }

        return new DashboardSnapshot(clock.instant(),
                countBy(allMachines, MachineInfo::status, MachineStatus.class),
                countBy(allJobs, JobInfo::status, JobStatus.class),
                allMachines, shown, Math.max(0, finished - RECENT_FINISHED_JOBS), quotas.ranking(),
                Arrays.asList(JobType.values()));
    }

    @Override
    public JobDetails jobDetails(String jobId) {
        JobInfo job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job", jobId));
        try {
            return new JobDetails(job, scheduling.attemptsOf(jobId), true);
        } catch (RemoteModuleUnavailableException e) {
            // M/P : app-scheduling injoignable. Le job s'affiche quand même, sans l'historique de ses tentatives.
            log.warn("Attempts of job {} unavailable: {}", jobId, e.getMessage());
            return new JobDetails(job, List.of(), false);
        }
    }

    /** Compte par statut, avec tous les statuts (0 compris) pour que la page n'ait pas de cas particulier. */
    private static <T, S extends Enum<S>> Map<S, Integer> countBy(List<T> items, Function<T, S> status,
                                                                  Class<S> type) {
        Map<S, Integer> counts = new EnumMap<>(type);
        for (S value : type.getEnumConstants()) {
            counts.put(value, 0);
        }
        items.forEach(item -> counts.merge(status.apply(item), 1, Integer::sum));
        return counts;
    }
}
