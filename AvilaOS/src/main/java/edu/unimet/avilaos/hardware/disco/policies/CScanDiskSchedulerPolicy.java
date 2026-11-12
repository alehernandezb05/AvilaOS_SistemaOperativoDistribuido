package edu.unimet.avilaos.hardware.disco.policies;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequest;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;

public class CScanDiskSchedulerPolicy implements IDiskSchedulerPolicy {

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
            if (block >= currentHead) {
                int distance = block - currentHead;
                if (distance < minDistance) {
                    minDistance = distance;
                    best = req;
                }
            }
        }

        if (best == null) {
            int lowestBlock = Integer.MAX_VALUE;
            for (int i = 0; i < queue.getTamano(); i++) {
                DiskRequest req = queue.obtener(i);
                if (req.getBlockNumber() < lowestBlock) {
                    lowestBlock = req.getBlockNumber();
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