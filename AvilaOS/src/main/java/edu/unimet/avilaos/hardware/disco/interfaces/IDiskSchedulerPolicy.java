package edu.unimet.avilaos.hardware.disco.interfaces;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.DiskRequest;

public interface IDiskSchedulerPolicy {
    DiskPolicy getPolicy();

    /** Elige y retira una solicitud; los empates conservan el orden de llegada. */
    DiskRequest selectNextRequest(Lista<DiskRequest> queue, int currentHead, DiskDirection direction);
}