package edu.unimet.avilaos.hardware.disco.policies;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequest;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;

public class FifoDiskSchedulerPolicy implements IDiskSchedulerPolicy {

    @Override
    public DiskRequest selectNextRequest(Lista<DiskRequest> queue, int currentHead, DiskDirection direction) {
        if (queue == null || queue.estaVacia()) {
            return null;
        }
        DiskRequest next = queue.obtener(0);
        queue.remover(next);
        return next;
    }
}