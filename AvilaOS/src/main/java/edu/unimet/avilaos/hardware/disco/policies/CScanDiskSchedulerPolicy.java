package edu.unimet.avilaos.hardware.disco.policies;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;

public class CScanDiskSchedulerPolicy implements IDiskSchedulerPolicy {

    @Override
    public DiskPolicy getPolicy() { return DiskPolicy.C_SCAN; }

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

        if (best != null) {
            queue.remover(best);
        }
        return best;
    }

    @Override
    public Sweep sweepWithoutRequest(int lastBlock, DiskDirection direction) {
        return new Sweep(lastBlock, DiskDirection.UP, 0);
    }
}
