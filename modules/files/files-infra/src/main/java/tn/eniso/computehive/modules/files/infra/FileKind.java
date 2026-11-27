package tn.eniso.computehive.modules.files.infra;

/**
 * Type d'un fichier de la bibliothèque. Tous deux sont des zips, que l'agent dézippe dans le dossier du job.
 *
 * <ul>
 *   <li>{@code CODE} : le code d'entraînement (un ou plusieurs fichiers Python), dézippé dans {@code code/} ;</li>
 *   <li>{@code DATASET} : des données, déposées une fois et choisies à chaque job, dézippées dans
 *       {@code data/<nom>/}.</li>
 * </ul>
 */
public enum FileKind {
    CODE,
    DATASET
}
