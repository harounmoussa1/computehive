-- Module machines : inventaire des PC workers.
-- Exécuté par Flyway dans le schéma "machines" (voir ModuleMigrations).

CREATE TABLE machine
(
    id                      VARCHAR(36)              PRIMARY KEY,
    agent_id                VARCHAR(128)             NOT NULL,
    name                    VARCHAR(255)             NOT NULL,
    agent_version           VARCHAR(64),
    cpu_cores               INTEGER                  NOT NULL,
    ram_total_mb            BIGINT                   NOT NULL,
    gpu_name                VARCHAR(255),
    gpu_memory_total_mb     BIGINT,
    gpu_memory_free_mb      BIGINT,
    gpu_utilization_percent INTEGER,
    cuda                    BOOLEAN                  NOT NULL,
    status                  VARCHAR(16)              NOT NULL,
    registered_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    last_heartbeat          TIMESTAMP WITH TIME ZONE NOT NULL,
    version                 BIGINT                   NOT NULL,
    CONSTRAINT machine_agent_id_uk UNIQUE (agent_id),
    CONSTRAINT machine_status_ck CHECK (status IN ('ONLINE', 'BUSY', 'DEAD'))
);

-- Requête de la détection de panne : machines vivantes sans heartbeat récent.
CREATE INDEX machine_status_last_heartbeat_idx ON machine (status, last_heartbeat);
