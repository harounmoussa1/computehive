-- Module quotas : consommation cumulée de chaque utilisateur.
-- Exécuté par Flyway dans le schéma "quotas" (voir ModuleMigrations).
-- user_* est une référence étendue (UserRef) : une copie de l'identité, sans table d'utilisateurs.

CREATE TABLE usage
(
    user_id        VARCHAR(64)              PRIMARY KEY,
    user_name      VARCHAR(255),
    user_type      VARCHAR(32),
    gpu_seconds    BIGINT                   NOT NULL,
    cpu_seconds    BIGINT                   NOT NULL,
    jobs_completed INTEGER                  NOT NULL,
    last_job_at    TIMESTAMP WITH TIME ZONE,
    version        BIGINT                   NOT NULL,
    CONSTRAINT usage_non_negative_ck CHECK (gpu_seconds >= 0 AND cpu_seconds >= 0 AND jobs_completed >= 0)
);

-- Classement du tableau de bord : les plus gros consommateurs d'abord.
CREATE INDEX usage_consumption_idx ON usage (gpu_seconds DESC, cpu_seconds DESC);
