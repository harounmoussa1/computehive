package tn.eniso.computehive.modules.files.service.impl;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;
import tn.eniso.computehive.core.infra.exception.ValidationException;
import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.ZipSummary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Vérifie un zip déposé avant de le garder (ADR 0013 §8). Le zip contient du code ou des données qui seront dézippés
 * sur les PC du laboratoire : il ne doit rien pouvoir écrire hors du dossier du job, ni saturer leur disque.
 *
 * <ul>
 *   <li>un zip valide, avec au moins un fichier ;</li>
 *   <li>aucun chemin absolu ({@code /etc/…}, {@code C:…}), aucun {@code ..}, aucune barre oblique inverse ni
 *       caractère de contrôle (« zip slip ») ;</li>
 *   <li>aucun lien symbolique (il pourrait pointer hors du dossier) ;</li>
 *   <li>nombre de fichiers et taille dézippée bornés ; rapport de compression borné, contre les « bombes zip » ;</li>
 *   <li>pour un code, au moins un fichier {@code .py}.</li>
 * </ul>
 *
 * <p>Les tailles sont celles annoncées par le zip. Pour un code (petit), elles sont aussi vérifiées en dézippant
 * réellement chaque fichier : un zip qui ment sur ses tailles est refusé. Pour un dataset (jusqu'à plusieurs Go),
 * cette vérification complète est faite par l'agent, qui revérifie tout à l'extraction.</p>
 */
@Component
public class ZipInspector {

    /** Lettre de lecteur Windows en tête de chemin ({@code C:}). */
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");

    /** Taille en dessous de laquelle le rapport de compression n'est pas regardé (petits fichiers très répétitifs). */
    private static final long RATIO_FLOOR = 1024 * 1024;

    public ZipSummary inspect(Path zip, FileKind kind, FilesProperties.Limits limits) {
        try (ZipFile file = ZipFile.builder().setPath(zip).get()) {
            List<String> entries = new ArrayList<>();
            int count = 0;
            long uncompressed = 0;
            boolean hasPython = false;
            Enumeration<ZipArchiveEntry> all = file.getEntriesInPhysicalOrder();
            while (all.hasMoreElements()) {
                ZipArchiveEntry entry = all.nextElement();
                String name = checkPath(entry.getName());
                if (entry.isUnixSymlink()) {
                    throw refused("symbolic links are not allowed: " + name);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                count++;
                if (count > limits.maxEntries()) {
                    throw refused("too many files (more than " + limits.maxEntries() + ")");
                }
                long size = entry.getSize();
                if (size < 0) {
                    throw refused("unknown size for " + name);
                }
                checkRatio(name, size, entry.getCompressedSize(), limits);
                uncompressed += size;
                if (uncompressed > limits.maxUncompressedSize().toBytes()) {
                    throw refused("too large once unzipped (more than " + limits.maxUncompressedSize() + ")");
                }
                if (kind == FileKind.CODE) {
                    checkRealSize(file, entry, name);
                    entries.add(name);
                    hasPython |= name.endsWith(".py");
                }
            }
            if (count == 0) {
                throw refused("the zip contains no file");
            }
            if (kind == FileKind.CODE && !hasPython) {
                throw refused("a code zip must contain at least one .py file");
            }
            return new ZipSummary(count, uncompressed, entries);
        } catch (IOException e) {
            throw refused("not a valid zip (" + e.getMessage() + ")");
        }
    }

    private static String checkPath(String name) {
        boolean unsafe = name.isEmpty()
                || name.startsWith("/")
                || DRIVE.matcher(name).matches()
                || name.contains("\\")
                || name.chars().anyMatch(c -> c < 0x20 || c == 0x7f);
        if (!unsafe) {
            for (String segment : name.split("/")) {
                if (segment.equals("..")) {
                    unsafe = true;
                    break;
                }
            }
        }
        if (unsafe) {
            throw refused("unsafe path in the zip: " + name.replaceAll("\\p{Cntrl}", "?"));
        }
        return name;
    }

    private static void checkRatio(String name, long size, long compressed, FilesProperties.Limits limits) {
        if (size > RATIO_FLOOR && size > (long) limits.maxCompressionRatio() * Math.max(compressed, 1)) {
            throw refused("suspicious compression ratio for " + name + " (zip bomb?)");
        }
    }

    /** Dézippe vraiment le fichier, sans dépasser sa taille annoncée d'un seul octet. */
    private static void checkRealSize(ZipFile file, ZipArchiveEntry entry, String name) throws IOException {
        try (InputStream in = file.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            long read = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                read += n;
                if (read > entry.getSize()) {
                    throw refused(name + " is larger than announced by the zip");
                }
            }
        }
    }

    private static ValidationException refused(String reason) {
        return new ValidationException("zip refused: " + reason);
    }
}
