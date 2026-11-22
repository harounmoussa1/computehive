package tn.eniso.computehive.modules.scheduling.service.impl.support;

import tn.eniso.computehive.core.infra.exception.NotFoundException;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointKey;
import tn.eniso.computehive.extensions.checkpoints.api.CheckpointStore;
import tn.eniso.computehive.extensions.checkpoints.api.StoredCheckpoint;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Faux stockage des checkpoints : le test déclare les fichiers « déposés » par l'agent et leur empreinte. */
public class FakeCheckpointStore implements CheckpointStore {

    private final Map<CheckpointKey, StoredCheckpoint> stored = new HashMap<>();

    /** Simule le dépôt d'un checkpoint par l'agent ({@code PUT /api/checkpoints/...}). */
    public void uploaded(String jobId, long attemptId, int epoch, String sha256) {
        CheckpointKey key = new CheckpointKey(jobId, attemptId, epoch);
        stored.put(key, new StoredCheckpoint(key, sha256, 1_024, Instant.parse("2026-11-21T10:00:00Z")));
    }

    @Override
    public StoredCheckpoint store(CheckpointKey key, InputStream content) {
        throw new UnsupportedOperationException("scheduling never stores checkpoints itself");
    }

    @Override
    public Optional<StoredCheckpoint> find(CheckpointKey key) {
        return Optional.ofNullable(stored.get(key));
    }

    @Override
    public InputStream open(CheckpointKey key) {
        throw new NotFoundException("Checkpoint", key.path());
    }
}
