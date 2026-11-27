package tn.eniso.computehive.modules.files.infra;

import tn.eniso.computehive.core.infra.util.Checks;

import java.util.List;

/**
 * Ce que l'inspection d'un zip déposé a trouvé.
 *
 * @param entryCount        nombre de fichiers dans le zip
 * @param uncompressedBytes taille totale une fois dézippé
 * @param entries           chemins des fichiers, gardés seulement pour un code (pour vérifier son point d'entrée) ;
 *                          vide pour un dataset, qui peut en contenir des millions
 */
public record ZipSummary(int entryCount, long uncompressedBytes, List<String> entries) {

    public ZipSummary {
        Checks.require(entryCount >= 0, "entryCount must not be negative");
        Checks.require(uncompressedBytes >= 0, "uncompressedBytes must not be negative");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
