package tn.eniso.computehive.core.service.api.interceptor;

/**
 * Point d'appel des intercepteurs, utilisé uniquement par les services CRUD :
 * ce sont eux qui écrivent les entités, donc eux qui annoncent chaque écriture.
 */
public interface InterceptorDispatcher {

    /**
     * Prévient les intercepteurs enregistrés pour le type de {@code entity} et pour {@code phase}.
     *
     * @param previous état avant mise à jour, {@code null} pour un ajout
     */
    <T> void dispatch(LifecyclePhase phase, T entity, T previous);
}
