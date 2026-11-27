package tn.eniso.computehive.modules.files.dal.api;

import tn.eniso.computehive.modules.files.infra.FileKind;
import tn.eniso.computehive.modules.files.infra.UserFile;

import java.util.List;
import java.util.Optional;

/**
 * Accès aux fiches des fichiers déposés. Seul {@code UserFileCrudService} l'utilise.
 *
 * <p>Un utilisateur ne peut pas avoir deux fichiers de même type et de même nom : {@link #save} lève alors une
 * {@code DataIntegrityViolationException}.</p>
 */
public interface UserFileRepository {

    /** Insère ou met à jour la fiche, et renvoie l'état enregistré (avec sa nouvelle version). */
    UserFile save(UserFile file);

    Optional<UserFile> findById(String id);

    Optional<UserFile> findByOwnerKindAndName(String ownerId, FileKind kind, String name);

    /** Fichiers d'un utilisateur, d'un type ou de tous types ({@code kind} nul), triés par nom. */
    List<UserFile> findByOwner(String ownerId, FileKind kind);

    void delete(String id);
}
