package tn.eniso.computehive.drivers.storageremote;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Stockage objet <b>compatible S3</b> : en déploiement, SeaweedFS (gratuit, auto-hébergé), lancé par docker compose
 * avec son propre volume. Les objets survivent donc au redémarrage des applications, et toute application qui reçoit
 * l'adresse du service voit les mêmes fichiers. Le client est celui de MinIO, une bibliothèque S3 générique : le même
 * code marche avec MinIO, Garage ou Amazon S3.
 *
 * <p>L'empreinte et la date de dépôt sont gardées dans les métadonnées de l'objet ({@code x-amz-meta-sha256},
 * {@code x-amz-meta-stored-at}) : {@link #stat} répond sans relire le contenu. Le bucket est créé au premier
 * dépôt s'il n'existe pas.</p>
 *
 * <p>Toute erreur réseau ou réponse d'erreur du service devient une {@link RemoteUnavailableException} ; seul
 * un objet absent donne une réponse vide.</p>
 */
public class S3RemoteBucket implements RemoteBucket {

    private static final Logger log = LoggerFactory.getLogger(S3RemoteBucket.class);
    private static final String SHA256 = "sha256";
    private static final String STORED_AT = "stored-at";
    private static final Set<String> MISSING = Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket");

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public S3RemoteBucket(String endpoint, String accessKey, String secretKey, String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        // Délais par opération réseau (et non par transfert complet) : un gros fichier peut prendre plus longtemps.
        this.client.setTimeout(TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(60),
                TimeUnit.SECONDS.toMillis(60));
        this.bucket = bucket;
    }

    @Override
    public void put(String key, Path file, ObjectInfo info) {
        call("put " + key, () -> {
            ensureBucket();
            client.uploadObject(UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .filename(file.toString())
                    .userMetadata(Map.of(SHA256, info.sha256(), STORED_AT, info.storedAt().toString()))
                    .build());
            return null;
        });
    }

    @Override
    public Optional<ObjectInfo> stat(String key) {
        return call("stat " + key, () -> {
            try {
                StatObjectResponse stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
                return Optional.of(toInfo(key, stat));
            } catch (ErrorResponseException e) {
                return missing(e);
            }
        });
    }

    @Override
    public Optional<InputStream> open(String key, long offset) {
        return call("open " + key, () -> {
            try {
                GetObjectArgs.Builder request = GetObjectArgs.builder().bucket(bucket).object(key);
                if (offset > 0) {
                    request.offset(offset);
                }
                return Optional.<InputStream>of(client.getObject(request.build()));
            } catch (ErrorResponseException e) {
                return missing(e);
            }
        });
    }

    private void ensureBucket() throws MinioException, IOException, GeneralSecurityException {
        if (bucketReady) {
            return;
        }
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("S3 bucket {} created", bucket);
        }
        bucketReady = true;
    }

    private static ObjectInfo toInfo(String key, StatObjectResponse stat) {
        Map<String, String> metadata = stat.userMetadata();
        String sha256 = metadata(metadata, SHA256);
        if (sha256 == null) {
            throw new RemoteUnavailableException("object " + key + " has no sha256 metadata");
        }
        Instant storedAt = stat.lastModified().toInstant();
        String recorded = metadata(metadata, STORED_AT);
        if (recorded != null) {
            try {
                storedAt = Instant.parse(recorded);
            } catch (DateTimeParseException e) {
                log.warn("Object {} has an unreadable stored-at metadata: {}", key, recorded);
            }
        }
        return new ObjectInfo(sha256, stat.size(), storedAt);
    }

    /** Les noms de métadonnées reviennent avec la casse choisie par le serveur : on les compare sans la casse. */
    private static String metadata(Map<String, String> metadata, String name) {
        return metadata.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static <T> Optional<T> missing(ErrorResponseException e) {
        if (MISSING.contains(e.errorResponse().code())) {
            return Optional.empty();
        }
        throw new RemoteUnavailableException("S3 storage refused the request: " + e.errorResponse().code(), e);
    }

    @FunctionalInterface
    private interface S3Call<T> {
        T run() throws MinioException, IOException, GeneralSecurityException;
    }

    private <T> T call(String operation, S3Call<T> call) {
        try {
            return call.run();
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new RemoteUnavailableException("S3 storage call failed (" + operation + "): " + e.getMessage(), e);
        }
    }
}
