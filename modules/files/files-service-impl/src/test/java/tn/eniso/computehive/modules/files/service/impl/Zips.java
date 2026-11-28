package tn.eniso.computehive.modules.files.service.impl;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fabrique de zips pour les tests, y compris des zips piégés (lien symbolique, chemin dangereux…). */
final class Zips {

    /** Date fixe des fichiers : deux appels à {@link #bytes()} donnent le même zip, donc la même empreinte. */
    private static final long FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final Map<String, String> links = new LinkedHashMap<>();

    static Zips with(String name, String content) {
        return new Zips().file(name, content);
    }

    Zips file(String name, String content) {
        return file(name, content.getBytes(StandardCharsets.UTF_8));
    }

    Zips file(String name, byte[] content) {
        files.put(name, content);
        return this;
    }

    /** Un lien symbolique {@code name} vers {@code target}, comme en crée {@code zip --symlinks}. */
    Zips link(String name, String target) {
        links.put(name, target);
        return this;
    }

    byte[] bytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    ZipArchiveEntry entry = new ZipArchiveEntry(file.getKey());
                    entry.setTime(FIXED_TIME);
                    zip.putArchiveEntry(entry);
                    zip.write(file.getValue());
                    zip.closeArchiveEntry();
                }
                for (Map.Entry<String, String> link : links.entrySet()) {
                    ZipArchiveEntry entry = new ZipArchiveEntry(link.getKey());
                    entry.setUnixMode(0120777);
                    entry.setTime(FIXED_TIME);
                    zip.putArchiveEntry(entry);
                    zip.write(link.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeArchiveEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Path writeTo(Path dir) {
        try {
            return Files.write(Files.createTempFile(dir, "test-", ".zip"), bytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
