package edu.unimet.avilaos.hardware.disco.interfaces;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.DiskRequest;

public interface IDiskSchedulerPolicy {
    DiskPolicy getPolicy();

    /** Elige y retira una solicitud elegible; los empates conservan el orden de llegada. */
    DiskRequest selectNextRequest(Lista<DiskRequest> queue, int currentHead, DiskDirection direction);

    /** Decide el barrido si quedan solicitudes en cola y ninguna es elegible. */
    default Sweep sweepWithoutRequest(int lastBlock, DiskDirection direction) {
        return null;
    }

    /** returnBlock >= 0 indica un retorno circular tras alcanzar el objetivo. */
    record Sweep(int targetBlock, DiskDirection directionOnArrival, int returnBlock) {
        public Sweep(int targetBlock, DiskDirection directionOnArrival) {
            this(targetBlock, directionOnArrival, -1);
        }
    }
}
