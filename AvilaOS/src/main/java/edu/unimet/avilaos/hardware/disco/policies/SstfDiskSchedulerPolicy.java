package edu.unimet.avilaos.hardware.disco.policies;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;

public class SstfDiskSchedulerPolicy implements IDiskSchedulerPolicy {

    @Override
    public DiskPolicy getPolicy() { return DiskPolicy.SSTF; }

    @Override
    public DiskRequest selectNextRequest(Lista<DiskRequest> queue, int currentHead, DiskDirection direction) {
        if (queue == null || queue.estaVacia()) {
            return null;
        }
        DiskRequest closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (int i = 0; i < queue.getTamano(); i++) {
            DiskRequest req = queue.obtener(i);
            int distance = Math.abs(req.schedulingBlock(currentHead) - currentHead);
            if (distance < minDistance) {
                minDistance = distance;
                closest = req;
            }
        }

        if (closest != null) {
            queue.remover(closest);
        }
        return closest;
    }
}
