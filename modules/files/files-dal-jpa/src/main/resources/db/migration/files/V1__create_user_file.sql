-- Module files : fiches des fichiers déposés par les utilisateurs (codes et datasets).
-- Le contenu est dans le stockage de fichiers, sous son empreinte (sha256) ; plusieurs fiches peuvent le partager.
-- Exécuté par Flyway dans le schéma "files" (voir ModuleMigrations).

CREATE TABLE user_file
(
    id                 VARCHAR(36)              PRIMARY KEY,
    owner_id           VARCHAR(64)              NOT NULL,
    owner_name         VARCHAR(255),
    owner_type         VARCHAR(32),
    kind               VARCHAR(16)              NOT NULL,
    name               VARCHAR(64)              NOT NULL,
    sha256             VARCHAR(64)              NOT NULL,
    size_bytes         BIGINT                   NOT NULL,
    entry_count        INTEGER                  NOT NULL,
    uncompressed_bytes BIGINT                   NOT NULL,
    -- Chemins des fichiers d'un code, un par ligne ; vide pour un dataset.
    entries            TEXT                     NOT NULL,
    uploaded_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT                   NOT NULL,
    CONSTRAINT user_file_owner_kind_name_uk UNIQUE (owner_id, kind, name),
    CONSTRAINT user_file_kind_ck CHECK (kind IN ('CODE', 'DATASET'))
);

-- La bibliothèque d'un utilisateur, par type.
CREATE INDEX user_file_owner_kind_idx ON user_file (owner_id, kind);
