/**
 * <p>Extension <b>checkpoint-storage</b> — stockage des checkpoints d'entraînement, vérifiés par SHA-256.</p>
 *
 * <p>Couche <b>ws-rest</b> : contrôleurs REST. Ils traduisent HTTP en appels de le contrat de l'extension, sans logique métier.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.extensions.checkpoints.ws.rest.CheckpointController} ({@code PUT} et {@code GET} {@code /api/checkpoints/{jobId}/attempts/{attemptId}/epochs/{epoch}}, utilisés par l'agent pour déposer et récupérer un checkpoint ; 503 si le stockage est indisponible).</p>
 */
package tn.eniso.computehive.extensions.checkpoints.ws.rest;
