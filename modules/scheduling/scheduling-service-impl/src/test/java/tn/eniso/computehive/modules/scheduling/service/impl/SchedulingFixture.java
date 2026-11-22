package tn.eniso.computehive.modules.scheduling.service.impl;

import org.mockito.Mockito;
import tn.eniso.computehive.core.infra.ref.UserRef;
import tn.eniso.computehive.core.service.api.interceptor.InterceptorDispatcher;
import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.jobs.infra.JobParams;
import tn.eniso.computehive.modules.jobs.infra.JobRequirements;
import tn.eniso.computehive.modules.jobs.infra.JobSubmission;
import tn.eniso.computehive.modules.jobs.infra.JobType;
import tn.eniso.computehive.modules.scheduling.infra.AssignmentInfo;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentHello;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentRegistered;
import tn.eniso.computehive.modules.scheduling.infra.agent.RunningAttempt;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.AssignmentCrudService;
import tn.eniso.computehive.modules.scheduling.service.impl.entity.ProcessedAgentMessageCrudService;
import tn.eniso.computehive.modules.scheduling.service.impl.support.FakeCheckpointStore;
import tn.eniso.computehive.modules.scheduling.service.impl.support.FakeJobsModule;
import tn.eniso.computehive.modules.scheduling.service.impl.support.FakeMachinesModule;
import tn.eniso.computehive.modules.scheduling.service.impl.support.FakeQuotasModule;
import tn.eniso.computehive.modules.scheduling.service.impl.support.InMemoryAssignmentRepository;
import tn.eniso.computehive.modules.scheduling.service.impl.support.InMemoryProcessedAgentMessageRepository;
import tn.eniso.computehive.modules.scheduling.service.impl.support.MutableClock;
import tn.eniso.computehive.modules.scheduling.service.impl.support.RecordingAgentChannel;

import java.time.Instant;
import java.util.List;

/**
 * Le module scheduling assemblé pour les tests : vrais composants de scheduling, faux modules jobs et
 * machines (avec les vraies entités), repositories en mémoire et canal d'agents enregistreur.
 */
class SchedulingFixture {

    static final Instant START = Instant.parse("2026-11-21T10:00:00Z");
    static final UserRef HAROUN = new UserRef("u-1", "Haroun", "MEMBER");

    final MutableClock clock = new MutableClock(START);
    final FakeJobsModule jobs = new FakeJobsModule(clock);
    final FakeMachinesModule machines = new FakeMachinesModule(clock);
    final RecordingAgentChannel agents = new RecordingAgentChannel();
    final SchedulingProperties properties = SchedulingProperties.defaults();
    final AssignmentCrudService assignments = new AssignmentCrudService(new InMemoryAssignmentRepository(),
            Mockito.mock(InterceptorDispatcher.class));
    final ProcessedAgentMessageCrudService processed =
            new ProcessedAgentMessageCrudService(new InMemoryProcessedAgentMessageRepository());
    final AttemptLifecycle lifecycle = new AttemptLifecycle(assignments, jobs, machines, agents, clock);
    final Fencing fencing = new Fencing(assignments, lifecycle);
    final FakeQuotasModule quotas = new FakeQuotasModule();
    final JobPlacement placement = new JobPlacement(jobs, machines, assignments, agents, quotas, clock);
    final LaunchWatchdog watchdog = new LaunchWatchdog(assignments, jobs, agents, lifecycle, properties, clock);
    final Reassignment reassignment = new Reassignment(assignments, jobs, lifecycle, properties, clock);
    final Reconciler reconciler = new Reconciler(assignments, jobs, machines, lifecycle);
    final FakeCheckpointStore checkpointStore = new FakeCheckpointStore();
    final SchedulingModuleImpl scheduling = new SchedulingModuleImpl(machines, jobs, assignments, processed, fencing,
            lifecycle, reassignment, new CheckpointVerifier(checkpointStore), clock);

    private int messages;

    /** Un agent GPU s'inscrit et garde son stream ouvert ; renvoie l'id de sa machine. */
    String connectGpuAgent(String agentId) {
        AgentRegistered registered = scheduling.registerAgent(gpuHello(agentId, List.of()));
        agents.connect(registered.machineId());
        return registered.machineId();
    }

    String connectCpuAgent(String agentId) {
        AgentRegistered registered = scheduling.registerAgent(new AgentHello(agentId, "pc-" + agentId, "0.1.0", 4,
                16_000, null, List.of()));
        agents.connect(registered.machineId());
        return registered.machineId();
    }

    static AgentHello gpuHello(String agentId, List<RunningAttempt> running) {
        return new AgentHello(agentId, "pc-" + agentId, "0.1.0", 8, 32_000,
                new AgentHello.AgentGpu("RTX 3060", 12_288, true), running);
    }

    JobInfo submitGpuJob() {
        return submitGpuJobOf(HAROUN);
    }

    JobInfo submitGpuJobOf(UserRef owner) {
        return jobs.submit(new JobSubmission(owner, "mnist", JobType.MNIST_TRAIN, new JobParams(10, 0.01, 64),
                JobRequirements.gpu(4_096)));
    }

    /** Dernier LaunchJob envoyé à la machine. */
    AgentCommand.LaunchJob lastLaunch(String machineId) {
        List<AgentCommand.LaunchJob> launches = agents.launches(machineId);
        return launches.get(launches.size() - 1);
    }

    AssignmentInfo currentAttempt(String jobId) {
        return scheduling.currentAttempt(jobId).orElseThrow();
    }

    /** Nouveau {@code message_id}, comme en génère l'agent. */
    String messageId() {
        return "msg-" + (++messages);
    }
}
