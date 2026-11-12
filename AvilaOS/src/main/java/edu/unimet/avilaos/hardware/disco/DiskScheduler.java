package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequest;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.policies.CScanDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.policies.FifoDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.policies.ScanDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.policies.SstfDiskSchedulerPolicy;
import java.util.concurrent.Semaphore;

public class DiskScheduler {

    private final DiskBlock[] blocks;
    private final int totalBlocks;
    private final int swapSizeBlocks;
    
    private int currentHead;
    private DiskDirection currentDirection;
    private IDiskSchedulerPolicy activePolicy;
    private DiskPolicy activePolicyEnum;

    private final Lista<DiskRequest> requestQueue;
    private final Semaphore queueSemaphore;

    private int totalHeadMovement;
    private int totalRequestsServed;
    private int fifoServedCount;
    private int sstfServedCount;
    private int scanServedCount;
    private int cscanServedCount;
    
    private DiskRequest currentRequestBeingServed;

    public DiskScheduler(int totalBlocks, int initialHead, int swapSizeBlocks, DiskPolicy initialPolicy) {
        this.totalBlocks = totalBlocks;
        this.currentHead = initialHead;
        this.swapSizeBlocks = swapSizeBlocks;
        this.currentDirection = DiskDirection.UP;
        this.requestQueue = new Lista<>();
        this.queueSemaphore = new Semaphore(1);
        
        this.blocks = new DiskBlock[totalBlocks];
        for (int i = 0; i < totalBlocks; i++) {
            this.blocks[i] = new DiskBlock(i);
        }

        setPolicy(initialPolicy);
    }

    public void setPolicy(DiskPolicy policy) {
        this.activePolicyEnum = policy;
        if (policy == null) return;
        if (policy == DiskPolicy.SSTF) {
            this.activePolicy = new SstfDiskSchedulerPolicy();
        } else if (policy == DiskPolicy.SCAN) {
            this.activePolicy = new ScanDiskSchedulerPolicy();
        } else if (policy == DiskPolicy.C_SCAN) {
            this.activePolicy = new CScanDiskSchedulerPolicy();
        } else {
            this.activePolicy = new FifoDiskSchedulerPolicy();
        }
    }

    public void addRequest(DiskRequest request) {
        try {
            queueSemaphore.acquire();
            requestQueue.agregar(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            queueSemaphore.release();
        }
    }

    public void tick() {
        try {
            queueSemaphore.acquire();
            
            if (currentRequestBeingServed == null && !requestQueue.estaVacia()) {
                currentRequestBeingServed = activePolicy.selectNextRequest(requestQueue, currentHead, currentDirection);
            }

            if (currentRequestBeingServed != null) {
                int targetBlock = currentRequestBeingServed.getBlockNumber();

                if (currentHead != targetBlock) {
                    if (targetBlock > currentHead) {
                        currentHead++;
                        currentDirection = DiskDirection.UP;
                    } else {
                        currentHead--;
                        currentDirection = DiskDirection.DOWN;
                    }
                    totalHeadMovement++;
                } else {
                    totalRequestsServed++;
                    incrementPolicyMetric(activePolicyEnum);
                    currentRequestBeingServed = null;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            queueSemaphore.release();
        }
    }

    private void incrementPolicyMetric(DiskPolicy policy) {
        if (policy == null) return;
        if (policy == DiskPolicy.FIFO) {
            fifoServedCount++;
        } else if (policy == DiskPolicy.SSTF) {
            sstfServedCount++;
        } else if (policy == DiskPolicy.SCAN) {
            scanServedCount++;
        } else if (policy == DiskPolicy.C_SCAN) {
            cscanServedCount++;
        }
    }

    public int getCurrentHead() { return currentHead; }
    public DiskDirection getCurrentDirection() { return currentDirection; }
    public DiskPolicy getActivePolicyEnum() { return activePolicyEnum; }
    public int getTotalHeadMovement() { return totalHeadMovement; }
    public int getTotalRequestsServed() { return totalRequestsServed; }
    public DiskBlock[] getBlocks() { return blocks; }
    public DiskRequest getCurrentRequestBeingServed() { return currentRequestBeingServed; }
    public Lista<DiskRequest> getRequestQueue() { return requestQueue; }
}