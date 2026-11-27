/**
 * <p>Extension <b>file-storage</b> — couche <b>ws-rest</b> : le téléchargement des fichiers par les agents.</p>
 *
 * <p>Contenu : {@code FileDownloadController} ({@code GET /api/files/{key}}, reprise avec {@code Range}, empreinte
 * dans {@code X-File-Sha256}) et {@code FileAuthenticationFilter} (jeton partagé des agents). Ne dépend que du
 * contrat {@code FileStore}, sans logique métier.</p>
 */
package tn.eniso.computehive.extensions.files.ws.rest;
