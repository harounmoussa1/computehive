package tn.eniso.computehive.modules.scheduling.service.impl;

import tn.eniso.computehive.modules.jobs.infra.JobInfo;
import tn.eniso.computehive.modules.scheduling.infra.Assignment;
import tn.eniso.computehive.modules.scheduling.infra.ResumePoint;
import tn.eniso.computehive.modules.scheduling.infra.agent.AgentCommand;

/** Construction de l'ordre {@code LaunchJob} d'une tentative : le même à chaque (re)envoi, avec le même {@code command_id}. */
final class LaunchCommands {

    private LaunchCommands() {
    }

    static AgentCommand.LaunchJob of(Assignment assignment, JobInfo job) {
        ResumePoint resume = assignment.getResumeFrom();
        return new AgentCommand.LaunchJob(assignment.getLaunchCommandId(), job.id(), assignment.getAttemptId(),
                job.type().name(), job.params().epochs(), job.params().learningRate(), job.params().batchSize(),
                resume.epoch(), resume.checkpointKey(), resume.checkpointSha256());
    }

    /** Point de reprise d'un job : son checkpoint officiel, ou zéro s'il n'en a pas. */
    static ResumePoint resumePointOf(JobInfo job) {
        return job.lastCheckpoint() == null
                ? ResumePoint.FROM_SCRATCH
                : new ResumePoint(job.lastCheckpoint().epoch(), job.lastCheckpoint().storageKey(),
                job.lastCheckpoint().sha256());
    }
}
