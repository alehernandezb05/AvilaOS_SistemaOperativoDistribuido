package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;

/** Valores del disco observados bajo el mismo semaforo, sin estructuras mutables. */
public record DiskSnapshot(long cycle, int currentHead, DiskDirection direction,
        DiskPolicy policy, int speed, long totalHeadMovement, long totalRequestsServed,
        long activeRequestId, int targetBlock, int pendingCount) {
}
