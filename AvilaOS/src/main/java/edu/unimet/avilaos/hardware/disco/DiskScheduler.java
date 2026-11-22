package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.estructuras.Cola;
import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.ComponenteReloj;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;
import java.util.concurrent.Semaphore;

/** Motor de disco: una solicitud activa y una transferencia de bloque por ciclo. */
public class DiskScheduler implements ComponenteReloj {

    public static final int MAX_BLOCKS = 500;
    private final DiskBlock[] blocks;
    private final int initialHead;
    private final int swapSizeBlocks;
    private final Lista<DiskRequest> requestQueue = new Lista<>();
    // Buzon de resultados; no es otra cola de planificacion de E/S.
    private final Cola<DiskCompletion> completions = new Cola<>();
    private final Semaphore mutex = new Semaphore(1);
    private final long[] servedByPolicy = new long[DiskPolicy.values().length];
    private long currentCycle;
    private int currentHead;
    private int speed;
    private DiskDirection currentDirection = DiskDirection.UP;
    private IDiskSchedulerPolicy activePolicy;
    private DiskRequest currentRequest;
    private DiskPolicy selectedPolicy;
    private int blockIndex;
    private long totalHeadMovement;
    private long totalRequestsServed;

    public DiskScheduler(int totalBlocks, int initialHead, int swapSizeBlocks, DiskPolicy policy) {
        this(totalBlocks, initialHead, swapSizeBlocks, 1, DiskPolicies.create(policy));
    }

    public DiskScheduler(int totalBlocks, int initialHead, int swapSizeBlocks,
                         int speed, IDiskSchedulerPolicy policy) {
        if (totalBlocks < 1 || totalBlocks > MAX_BLOCKS) {
            throw new IllegalArgumentException("El disco debe tener entre 1 y " + MAX_BLOCKS + " bloques.");
        }
        if (initialHead < 0 || initialHead >= totalBlocks) {
            throw new IllegalArgumentException("El cabezal inicial esta fuera del disco.");
        }
        if (swapSizeBlocks < 0 || swapSizeBlocks > totalBlocks) {
            throw new IllegalArgumentException("El tamano de swap debe estar entre 0 y el total de bloques.");
        }
        validatePolicy(policy);
        validateSpeed(speed, totalBlocks);
        this.initialHead = initialHead;
        this.currentHead = initialHead;
        this.swapSizeBlocks = swapSizeBlocks;
        this.speed = speed;
        this.activePolicy = policy;
        this.blocks = new DiskBlock[totalBlocks];
        for (int i = 0; i < totalBlocks; i++) blocks[i] = new DiskBlock(i);
    }

    private static void validatePolicy(IDiskSchedulerPolicy policy) {
        if (policy == null || policy.getPolicy() == null) {
            throw new IllegalArgumentException("Falta una estrategia de planificacion valida.");
        }
    }

    private static void validateSpeed(int speed, int totalBlocks) {
        if (speed < 1 || speed > totalBlocks) {
            throw new IllegalArgumentException("La velocidad debe estar entre 1 y el total de bloques.");
        }
    }

    public void setPolicy(DiskPolicy policy) { setPolicy(DiskPolicies.create(policy)); }

    public void setPolicy(IDiskSchedulerPolicy policy) {
        validatePolicy(policy);
        mutex.acquireUninterruptibly();
        try {
            activePolicy = policy;
        } finally {
            mutex.release();
        }
    }

    public void setSpeed(int speed) {
        validateSpeed(speed, blocks.length);
        mutex.acquireUninterruptibly();
        try {
            this.speed = speed;
        } finally {
            mutex.release();
        }
    }

    /** False si el hilo fue interrumpido antes de adquirir el semaforo; se puede reintentar. */
    public boolean addRequest(DiskRequest request) {
        if (request == null) throw new IllegalArgumentException("Falta la solicitud.");
        for (int block : request.getBlockNumbers()) {
            if (block >= blocks.length) throw new IllegalArgumentException("La solicitud sale del disco.");
        }
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            request.enqueue(currentCycle);
            requestQueue.agregar(request);
            return true;
        } finally {
            mutex.release();
        }
    }

    @Override
    public void alCiclo(long cycle) {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            if (cycle <= currentCycle) return;
            currentCycle = cycle;
            if (currentRequest == null && !requestQueue.estaVacia()) {
                currentRequest = activePolicy.selectNextRequest(requestQueue, currentHead, currentDirection);
                if (currentRequest != null) {
                    currentRequest.start(cycle);
                    selectedPolicy = activePolicy.getPolicy();
                    blockIndex = 0;
                }
            }
            if (currentRequest == null) return;
            int target = currentRequest.blockAt(blockIndex);
            if (currentHead != target) {
                int distance = Math.min(speed, Math.abs(target - currentHead));
                currentDirection = target > currentHead ? DiskDirection.UP : DiskDirection.DOWN;
                currentHead += target > currentHead ? distance : -distance;
                totalHeadMovement += distance;
                return; // La transferencia ocupa otro ciclo, incluso si acaba de llegar.
            }
            blockIndex++;
            if (blockIndex == currentRequest.getBlockCount()) {
                completions.encolar(currentRequest.complete(cycle, selectedPolicy));
                totalRequestsServed++;
                servedByPolicy[selectedPolicy.ordinal()]++;
                currentRequest = null;
            }
        } finally {
            mutex.release();
        }
    }

    /** Cada finalizacion se entrega una vez; el consumidor resuelve el PCB fuera del semaforo. */
    public DiskCompletion pollCompleted() {
        mutex.acquireUninterruptibly();
        try {
            return completions.desencolar();
        } finally {
            mutex.release();
        }
    }

    public DiskSnapshot getSnapshot() {
        mutex.acquireUninterruptibly();
        try {
            return new DiskSnapshot(currentCycle, currentHead, currentDirection,
                    activePolicy.getPolicy(), speed, totalHeadMovement, totalRequestsServed,
                    currentRequest == null ? -1 : currentRequest.getId(),
                    currentRequest == null ? -1 : currentRequest.blockAt(blockIndex), requestQueue.getTamano());
        } finally {
            mutex.release();
        }
    }

    public DiskRequest[] getPendingRequests() {
        mutex.acquireUninterruptibly();
        try {
            DiskRequest[] copy = new DiskRequest[requestQueue.getTamano()];
            for (int i = 0; i < copy.length; i++) copy[i] = requestQueue.obtener(i);
            return copy;
        } finally {
            mutex.release();
        }
    }

    /** Copias para dibujar; la vista no puede asignar bloques por su cuenta. */
    public DiskBlock[] getBlocks() {
        mutex.acquireUninterruptibly();
        try {
            DiskBlock[] copy = new DiskBlock[blocks.length];
            for (int i = 0; i < copy.length; i++) {
                DiskBlock block = blocks[i];
                copy[i] = new DiskBlock(block.getBlockNumber());
                copy[i].setOccupied(block.isOccupied());
                copy[i].setOwnerName(block.getOwnerName());
                copy[i].setContentType(block.getContentType());
                copy[i].setDamaged(block.isDamaged());
            }
            return copy;
        } finally {
            mutex.release();
        }
    }

    public long getServedCount(DiskPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Falta la politica.");
        mutex.acquireUninterruptibly();
        try {
            return servedByPolicy[policy.ordinal()];
        } finally {
            mutex.release();
        }
    }

    public int getTotalBlocks() { return blocks.length; }
    public int getInitialHead() { return initialHead; }
    public int getSwapSizeBlocks() { return swapSizeBlocks; }
    public int getCurrentHead() { return getSnapshot().currentHead(); }
    public DiskDirection getCurrentDirection() { return getSnapshot().direction(); }
    public DiskPolicy getActivePolicyEnum() { return getSnapshot().policy(); }
    public long getTotalHeadMovement() { return getSnapshot().totalHeadMovement(); }
    public long getTotalRequestsServed() { return getSnapshot().totalRequestsServed(); }
}
