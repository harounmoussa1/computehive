-- Module jobs : jobs soumis par les utilisateurs.
-- Exécuté par Flyway dans le schéma "jobs" (voir ModuleMigrations).
-- owner_* et machine_* sont des références étendues (UserRef, MachineRef) : des copies,
-- sans clé étrangère vers les tables d'un autre module.

CREATE TABLE job
(
    id                     VARCHAR(36)              PRIMARY KEY,
    name                   VARCHAR(255)             NOT NULL,
    type                   VARCHAR(32)              NOT NULL,
    owner_id               VARCHAR(64)              NOT NULL,
    owner_name             VARCHAR(255),
    owner_type             VARCHAR(32),
    epochs                 INTEGER                  NOT NULL,
    learning_rate          DOUBLE PRECISION         NOT NULL,
    batch_size             INTEGER                  NOT NULL,
    requires_gpu           BOOLEAN                  NOT NULL,
    min_gpu_memory_mb      BIGINT                   NOT NULL,
    status                 VARCHAR(16)              NOT NULL,
    machine_id             VARCHAR(36),
    machine_name           VARCHAR(255),
    machine_type           VARCHAR(16),
    current_epoch          INTEGER                  NOT NULL,
    progress               DOUBLE PRECISION         NOT NULL,
    last_loss              DOUBLE PRECISION,
    checkpoint_epoch       INTEGER,
    checkpoint_storage_key VARCHAR(512),
    checkpoint_sha256      VARCHAR(64),
    accuracy               DOUBLE PRECISION,
    failure_reason         VARCHAR(1000),
    submitted_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at             TIMESTAMP WITH TIME ZONE,
    finished_at            TIMESTAMP WITH TIME ZONE,
    version                BIGINT                   NOT NULL,
    CONSTRAINT job_status_ck CHECK (status IN ('PENDING', 'RUNNING', 'REASSIGNED', 'DONE', 'FAILED', 'CANCELLED')),
    CONSTRAINT job_params_ck CHECK (epochs > 0 AND learning_rate > 0 AND batch_size > 0),
    CONSTRAINT job_progress_ck CHECK (progress BETWEEN 0 AND 100),
    -- Un checkpoint est complet ou absent.
    CONSTRAINT job_checkpoint_ck CHECK (
        (checkpoint_epoch IS NULL AND checkpoint_storage_key IS NULL AND checkpoint_sha256 IS NULL)
        OR (checkpoint_epoch IS NOT NULL AND checkpoint_storage_key IS NOT NULL AND checkpoint_sha256 IS NOT NULL))
);

-- Placement (scheduling) et filtre du tableau de bord : jobs d'un statut, du plus ancien au plus récent.
CREATE INDEX job_status_submitted_at_idx ON job (status, submitted_at);

-- Jobs d'un utilisateur.
CREATE INDEX job_owner_id_idx ON job (owner_id, submitted_at);
