package tn.eniso.computehive.modules.jobs.infra;

/**
 * Types de job prédéfinis. L'utilisateur choisit un type, il n'envoie jamais de code :
 * l'agent associe chaque type à un script Python connu ({@code job_type} dans {@code LaunchJob}).
 */
public enum JobType {

    /** Entraînement d'un réseau de classification de chiffres manuscrits (MNIST, PyTorch). */
    MNIST_TRAIN
}
