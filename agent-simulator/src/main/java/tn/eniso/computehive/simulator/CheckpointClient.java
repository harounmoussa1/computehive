package tn.eniso.computehive.simulator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Dépôt et téléchargement des checkpoints sur le serveur (docs/worker-protocol.md §8), par l'API REST
 * {@code /api/checkpoints/<jobId>/attempts/<n>/epochs/<n>}. Client HTTP du JDK : le faux agent ne dépend
 * toujours que de {@code worker.proto}.
 */
public final class CheckpointClient {

    static final String SHA256_HEADER = "X-Checkpoint-Sha256";

    private final String baseUrl;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** @param baseUrl adresse de l'API REST du serveur, ex. {@code http://localhost:8080} */
    public CheckpointClient(String baseUrl) {
        this(baseUrl, null);
    }

    /** @param token jeton partagé des agents, envoyé dans {@code Authorization: Bearer} ; {@code null} si aucun */
    public CheckpointClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
    }

    /** Le serveur a refusé ou n'a pas répondu. */
    static final class CheckpointTransferException extends Exception {
        CheckpointTransferException(String message) {
            super(message);
        }
    }

    /** Dépose le checkpoint en annonçant son empreinte ; le serveur la recalcule et refuse un envoi corrompu. */
    void upload(String key, byte[] content) throws CheckpointTransferException {
        HttpRequest request = authorized(HttpRequest.newBuilder(uri(key)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/octet-stream")
                .header(SHA256_HEADER, sha256(content))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        int status = send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status / 100 != 2) {
            throw new CheckpointTransferException("upload of " + key + " refused: HTTP " + status);
        }
    }

    /** Télécharge le checkpoint et vérifie qu'il est intact avant de reprendre l'entraînement. */
    byte[] download(String key, String expectedSha256) throws CheckpointTransferException {
        HttpRequest request = authorized(HttpRequest.newBuilder(uri(key))).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new CheckpointTransferException("download of " + key + " refused: HTTP " + response.statusCode());
        }
        String actual = sha256(response.body());
        if (!actual.equals(expectedSha256)) {
            throw new CheckpointTransferException("checkpoint " + key + " is corrupted: expected " + expectedSha256
                    + ", got " + actual);
        }
        return response.body();
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder request) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private URI uri(String key) {
        return URI.create(baseUrl + "/api/checkpoints/" + key);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> body)
            throws CheckpointTransferException {
        try {
            return http.send(request, body);
        } catch (IOException e) {
            throw new CheckpointTransferException(request.method() + " " + request.uri() + " failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CheckpointTransferException("interrupted");
        }
    }
}
