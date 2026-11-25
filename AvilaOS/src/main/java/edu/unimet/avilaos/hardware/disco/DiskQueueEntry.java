package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;

/**
 * Copia de una solicitud de la cola de E/S para mostrarla.
 * targetBlock es -1 cuando la solicitud solo trabaja con metadatos.
 */
public record DiskQueueEntry(long requestId, int processId, String processName, DiskOperation operation,
        int targetBlock, int blockCount, long arrivalCycle, boolean active) {
}
