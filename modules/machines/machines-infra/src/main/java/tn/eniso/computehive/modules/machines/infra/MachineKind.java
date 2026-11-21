package tn.eniso.computehive.modules.machines.infra;

/** Type de worker : avec GPU NVIDIA, ou CPU seul. Sert aussi de {@code type} dans {@code MachineRef}. */
public enum MachineKind {
    GPU,
    CPU
}
