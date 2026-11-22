/**
 * <p>Extension <b>notifications</b> — envoi de notifications aux utilisateurs (simulé, aucune API payante).</p>
 *
 * <p>Contrat technique de l'extension. Les modules ne dépendent que de ce contrat ; un driver l'implémente avec une technologie.</p>
 *
 * <p>Contenu : {@link tn.eniso.computehive.extensions.notifications.api.Notifier}, {@code Notification} (destinataire {@code UserRef}, catégorie, sujet, texte), {@code NotificationException}. Drivers : {@code driver-notif-log}, {@code driver-notif-email} (Mailpit).</p>
 */
package tn.eniso.computehive.extensions.notifications.api;
