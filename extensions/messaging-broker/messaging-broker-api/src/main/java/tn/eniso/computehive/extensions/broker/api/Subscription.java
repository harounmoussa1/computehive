package tn.eniso.computehive.extensions.broker.api;

/** Abonnement en cours ; {@link #close()} y met fin (sans effet s'il est déjà fermé). */
public interface Subscription extends AutoCloseable {

    @Override
    void close();
}
