package tn.eniso.computehive.extensions.files.api;

import tn.eniso.computehive.core.infra.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Clé d'un fichier : l'empreinte SHA-256 de son contenu (adressage par le contenu).
 *
 * <ul>
 *   <li>Un même contenu déposé deux fois, même par deux personnes, n'est stocké qu'une fois.</li>
 *   <li>Un fichier ne change jamais sous sa clé : l'agent qui le télécharge peut vérifier qu'il a reçu le bon
 *       contenu, et garder un cache par empreinte.</li>
 *   <li>Le format strict (64 caractères hexadécimaux) empêche toute sortie de l'espace de stockage.</li>
 * </ul>
 *
 * <p>Le propriétaire et le nom d'un fichier ne sont pas ici : ils sont dans la fiche du module {@code files}.</p>
 *
 * @param sha256 empreinte du contenu, en hexadécimal minuscule
 */
public record FileKey(String sha256) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public FileKey {
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new ValidationException("file key must be a sha256 (64 lowercase hex characters): " + sha256);
        }
    }

    /** Relit une clé reçue de l'extérieur (ex. {@code GET /api/files/{key}}) ; les majuscules sont acceptées. */
    public static FileKey parse(String key) {
        return new FileKey(key == null ? null : key.toLowerCase(Locale.ROOT));
    }

    /** Chemin de l'objet dans le stockage : {@code files/<sha256>}. */
    public String path() {
        return "files/" + sha256;
    }

    @Override
    public String toString() {
        return sha256;
    }
}
