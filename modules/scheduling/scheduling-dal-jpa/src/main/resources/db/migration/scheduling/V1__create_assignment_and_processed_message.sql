-- Module scheduling : tentatives d'exécution et dédoublonnage des messages d'agent.
-- Exécuté par Flyway dans le schéma "scheduling" (voir ModuleMigrations).
-- job_* et machine_* sont des références étendues (JobRef, MachineRef) : des copies,
-- sans clé étrangère vers les tables d'un autre module.

CREATE TABLE assignment
(
    id                       VARCHAR(36)              PRIMARY KEY,
    job_id                   VARCHAR(36)              NOT NULL,
    job_name                 VARCHAR(255),
    job_type                 VARCHAR(32),
    machine_id               VARCHAR(36)              NOT NULL,
    machine_name             VARCHAR(255),
    machine_type             VARCHAR(16),
    attempt_id               BIGINT                   NOT NULL,
    status                   VARCHAR(16)              NOT NULL,
    resume_epoch             INTEGER                  NOT NULL,
    resume_checkpoint_key    VARCHAR(512),
    resume_checkpoint_sha256 VARCHAR(64),
    launch_command_id        VARCHAR(36)              NOT NULL,
    launch_sent_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    launch_send_count        INTEGER                  NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at                 TIMESTAMP WITH TIME ZONE,
    end_reason               VARCHAR(1000),
    version                  BIGINT                   NOT NULL,
    CONSTRAINT assignment_job_attempt_uk UNIQUE (job_id, attempt_id),
    CONSTRAINT assignment_launch_command_uk UNIQUE (launch_command_id),
    CONSTRAINT assignment_status_ck CHECK (status IN ('LAUNCHING', 'RUNNING', 'COMPLETED', 'FAILED',
                                                      'STOPPED', 'REJECTED', 'LOST')),
    CONSTRAINT assignment_attempt_ck CHECK (attempt_id >= 1),
    CONSTRAINT assignment_resume_ck CHECK (resume_epoch >= 0)
);

-- Garantie d'absence de job en double : au plus une tentative active par job…
CREATE UNIQUE INDEX assignment_one_active_per_job_uk ON assignment (job_id)
    WHERE status IN ('LAUNCHING', 'RUNNING');

-- … et au plus une tentative active par machine (v1 : un job à la fois par PC).
CREATE UNIQUE INDEX assignment_one_active_per_machine_uk ON assignment (machine_id)
    WHERE status IN ('LAUNCHING', 'RUNNING');

-- Renvoi des LaunchJob restés sans CommandAck.
CREATE INDEX assignment_status_launch_sent_at_idx ON assignment (status, launch_sent_at);

CREATE TABLE processed_agent_message
(
    message_id   VARCHAR(128)             PRIMARY KEY,
    machine_id   VARCHAR(36)              NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Purge des traces de plus de 24 h.
CREATE INDEX processed_agent_message_processed_at_idx ON processed_agent_message (processed_at);
