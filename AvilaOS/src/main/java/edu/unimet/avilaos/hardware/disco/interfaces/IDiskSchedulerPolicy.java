package edu.unimet.avilaos.hardware.disco.interfaces;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequest;

public interface IDiskSchedulerPolicy {
    DiskRequest selectNextRequest(Lista<DiskRequest> queue, int currentHead, DiskDirection direction);
}