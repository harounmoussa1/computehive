package tn.eniso.computehive.core.infra.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Calcul du SHA-256 pendant la lecture d'un flux, commun aux stockages de fichiers : le contenu n'est lu qu'une fois,
 * même pour un gros checkpoint.
 */
public final class Sha256 {

    private Sha256() {
    }

    /** Flux qui calcule l'empreinte et compte les octets au fil de la lecture. */
    public static final class DigestingStream extends FilterInputStream {

        private final MessageDigest digest = newDigest();
        private long size;

        public DigestingStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                digest.update((byte) b);
                size++;
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                digest.update(buffer, offset, read);
                size += read;
            }
            return read;
        }

        /** Empreinte de tout ce qui a été lu, en hexadécimal minuscule. */
        public String sha256() {
            return HexFormat.of().formatHex(digest.digest());
        }

        public long size() {
            return size;
        }
    }

    public static String of(byte[] content) {
        return HexFormat.of().formatHex(newDigest().digest(content));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
