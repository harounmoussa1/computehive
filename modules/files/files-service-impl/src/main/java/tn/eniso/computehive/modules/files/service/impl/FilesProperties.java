package tn.eniso.computehive.modules.files.service.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import tn.eniso.computehive.modules.files.infra.FileKind;

/**
 * Limites des dépôts ({@code computehive.files.*}), pour chaque type de fichier (ADR 0013 §8).
 *
 * @param codeMaxSize                taille maximale d'un code zip
 * @param codeMaxUncompressedSize    taille maximale d'un code une fois dézippé
 * @param codeMaxEntries             nombre maximal de fichiers d'un code
 * @param datasetMaxSize             taille maximale d'un dataset zip
 * @param datasetMaxUncompressedSize taille maximale d'un dataset une fois dézippé
 * @param datasetMaxEntries          nombre maximal de fichiers d'un dataset
 * @param maxCompressionRatio        rapport maximal entre la taille dézippée et la taille compressée d'un fichier :
 *                                   au-delà, c'est une « bombe zip » (quelques Ko qui deviennent des Go)
 */
@ConfigurationProperties("computehive.files")
public record FilesProperties(
        @DefaultValue("50MB") DataSize codeMaxSize,
        @DefaultValue("200MB") DataSize codeMaxUncompressedSize,
        @DefaultValue("2000") int codeMaxEntries,
        @DefaultValue("10GB") DataSize datasetMaxSize,
        @DefaultValue("50GB") DataSize datasetMaxUncompressedSize,
        @DefaultValue("1000000") int datasetMaxEntries,
        @DefaultValue("200") int maxCompressionRatio) {

    /** Limites d'un type de fichier. */
    public record Limits(DataSize maxSize, DataSize maxUncompressedSize, int maxEntries, int maxCompressionRatio) {

        public Limits {
            if (maxSize.toBytes() <= 0 || maxUncompressedSize.toBytes() <= 0 || maxEntries <= 0
                    || maxCompressionRatio <= 1) {
                throw new IllegalArgumentException("computehive.files limits must be positive");
            }
        }
    }

    public Limits of(FileKind kind) {
        return kind == FileKind.CODE
                ? new Limits(codeMaxSize, codeMaxUncompressedSize, codeMaxEntries, maxCompressionRatio)
                : new Limits(datasetMaxSize, datasetMaxUncompressedSize, datasetMaxEntries, maxCompressionRatio);
    }

    public static FilesProperties defaults() {
        return new FilesProperties(DataSize.ofMegabytes(50), DataSize.ofMegabytes(200), 2_000,
                DataSize.ofGigabytes(10), DataSize.ofGigabytes(50), 1_000_000, 200);
    }
}
