package edu.unimet.avilaos.hardware.disco.policies;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequest;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;

public class ScanDiskSchedulerPolicy implements IDiskSchedulerPolicy {

    @Override
    public DiskRequest selectNextRequest(Lista<DiskRequest> queue, int currentHead, DiskDirection direction) {
        if (queue == null || queue.estaVacia()) {
            return null;
        }

        DiskRequest best = null;
        int minDistance = Integer.MAX_VALUE;

        for (int i = 0; i < queue.getTamano(); i++) {
            DiskRequest req = queue.obtener(i);
            int block = req.getBlockNumber();
            boolean validDirection = (direction == DiskDirection.UP && block >= currentHead)
                    || (direction == DiskDirection.DOWN && block <= currentHead);

            if (validDirection) {
                int distance = Math.abs(block - currentHead);
                if (distance < minDistance) {
                    minDistance = distance;
                    best = req;
                }
            }
        }

        if (best == null) {
            for (int i = 0; i < queue.getTamano(); i++) {
                DiskRequest req = queue.obtener(i);
                int distance = Math.abs(req.getBlockNumber() - currentHead);
                if (distance < minDistance) {
                    minDistance = distance;
                    best = req;
                }
            }
        }

        if (best != null) {
            queue.remover(best);
        }
        return best;
    }
}