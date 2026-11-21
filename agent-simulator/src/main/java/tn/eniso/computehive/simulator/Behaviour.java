package tn.eniso.computehive.simulator;

import java.time.Duration;

/**
 * Comportement du faux agent : rythme de l'entraînement simulé et pannes à provoquer.
 *
 * @param epochDuration     durée simulée d'une epoch
 * @param checkpointEvery   un checkpoint toutes les {@code checkpointEvery} epochs (0 : jamais)
 * @param rejectLaunches    refuser tous les {@code LaunchJob} ({@code CommandAck(accepted = false)})
 * @param failAtEpoch       le script échoue à cette epoch ({@code Finished(FAILED)}) ; 0 : jamais
 * @param crashAtEpoch      le PC « meurt » à cette epoch : plus aucun message, stream coupé ; 0 : jamais
 * @param maxReconnectDelay attente maximale entre deux tentatives de reconnexion (protocole : 15 s)
 */
public record Behaviour(Duration epochDuration, int checkpointEvery, boolean rejectLaunches, int failAtEpoch,
                        int crashAtEpoch, Duration maxReconnectDelay) {

    public static Behaviour normal() {
        return new Behaviour(Duration.ofSeconds(1), 2, false, 0, 0, Duration.ofSeconds(15));
    }

    public Behaviour withEpochDuration(Duration value) {
        return new Behaviour(value, checkpointEvery, rejectLaunches, failAtEpoch, crashAtEpoch, maxReconnectDelay);
    }

    public Behaviour withCheckpointEvery(int value) {
        return new Behaviour(epochDuration, value, rejectLaunches, failAtEpoch, crashAtEpoch, maxReconnectDelay);
    }

    public Behaviour rejectingLaunches() {
        return new Behaviour(epochDuration, checkpointEvery, true, failAtEpoch, crashAtEpoch, maxReconnectDelay);
    }

    public Behaviour failingAt(int epoch) {
        return new Behaviour(epochDuration, checkpointEvery, rejectLaunches, epoch, crashAtEpoch, maxReconnectDelay);
    }

    public Behaviour crashingAt(int epoch) {
        return new Behaviour(epochDuration, checkpointEvery, rejectLaunches, failAtEpoch, epoch, maxReconnectDelay);
    }

    public Behaviour withMaxReconnectDelay(Duration value) {
        return new Behaviour(epochDuration, checkpointEvery, rejectLaunches, failAtEpoch, crashAtEpoch, value);
    }
}
