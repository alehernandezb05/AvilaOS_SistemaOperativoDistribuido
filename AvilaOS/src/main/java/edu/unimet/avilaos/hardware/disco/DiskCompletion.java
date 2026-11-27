package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;

/** Transferencia terminada; el nucleo consume este resultado sin callbacks bajo el semaforo. */
public record DiskCompletion(long requestId, int processId, String processName,
        DiskOperation operation, long arrivalCycle, long startCycle,
        long completionCycle, DiskPolicy policy) {
}
