package tn.eniso.computehive.extensions.broker.api;

import tn.eniso.computehive.core.infra.util.Checks;

import java.util.regex.Pattern;

/**
 * Sujet d'événements typé. Le module qui publie déclare ses topics dans son {@code service-api},
 * à côté de sa façade : ils font partie de son contrat public.
 *
 * <pre>{@code
 * public static final Topic<MachineDeadEvent> MACHINE_DEAD =
 *         Topic.of("machines.machine-dead", MachineDeadEvent.class);
 * }</pre>
 *
 * @param name        nom stable, {@code <module>.<événement>} en minuscules (devient le topic Kafka)
 * @param payloadType type du contenu ; un record immuable, sérialisable en JSON
 */
public record Topic<E>(String name, Class<E> payloadType) {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9-]*)+");

    public Topic {
        Checks.require(name != null && NAME.matcher(name).matches(),
                "topic name must look like <module>.<event>, in lowercase: " + name);
        Checks.requireNonNull(payloadType, "payloadType");
    }

    public static <E> Topic<E> of(String name, Class<E> payloadType) {
        return new Topic<>(name, payloadType);
    }
}
