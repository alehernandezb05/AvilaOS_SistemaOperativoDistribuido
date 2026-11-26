package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.estructuras.Cola;
import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.ComponenteReloj;
import edu.unimet.avilaos.hardware.disco.DiskStorage.InitialFile;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy.Sweep;
import edu.unimet.avilaos.so.Log;
import java.util.concurrent.Semaphore;

/** Motor de disco: una solicitud activa y una transferencia de bloque por ciclo. */
public class DiskScheduler implements ComponenteReloj {

    public static final int MAX_BLOCKS = 500;
    private static final System.Logger LOGGER = System.getLogger(DiskScheduler.class.getName());
    private final DiskStorage storage;
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
    private int sweepReturnTarget = -1;
    private long totalHeadMovement;
    private long totalRequestsServed;
    private long totalWaitCycles;
    private long totalReturnCycles;
    private final long[] waitByPolicy = new long[DiskPolicy.values().length];
    private volatile Log log;

    public DiskScheduler(int totalBlocks, int initialHead, int swapSizeBlocks, DiskPolicy policy) {
        this(totalBlocks, initialHead, swapSizeBlocks, 1, DiskPolicies.create(policy));
    }

    public DiskScheduler(int totalBlocks, int initialHead, int swapSizeBlocks,
                         int speed, IDiskSchedulerPolicy policy) {
        this(totalBlocks, initialHead, swapSizeBlocks, speed, policy, new InitialFile[0]);
    }

    public DiskScheduler(int totalBlocks, int initialHead, int swapSizeBlocks,
                         int speed, IDiskSchedulerPolicy policy, InitialFile... initialFiles) {
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
        this.storage = new DiskStorage(totalBlocks, swapSizeBlocks, initialFiles);
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
        validateSpeed(speed, getTotalBlocks());
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
            if (block >= getTotalBlocks()) throw new IllegalArgumentException("La solicitud sale del disco.");
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
        
        long previousCycle = currentCycle;
try {
    if (cycle <= currentCycle) return;
    currentCycle = cycle;
            if (sweepReturnTarget >= 0) {
                advanceSweepReturn();
                return;
            }
            if (currentRequest == null) {
                if (requestQueue.estaVacia()) return;
                currentRequest = activePolicy.selectNextRequest(requestQueue, currentHead, currentDirection);
                if (currentRequest == null) {
                    advanceSweep();
                    return;
                }
                currentRequest.start(cycle);
                selectedPolicy = activePolicy.getPolicy();
                Log eventos = log;
                if (eventos != null) {
                    String destino = currentRequest.getBlockCount() == 0 ? " para metadatos"
                            : " en el bloque " + currentRequest.getBlockNumber();
                    eventos.registrar(cycle, "El disco atiende la solicitud " + currentRequest.getId() + " "
                            + currentRequest.getOperation() + " de P" + currentRequest.getProcessId()
                            + destino + " (" + selectedPolicy + ")");
                }
                blockIndex = 0;
            }
            if (currentRequest.getBlockCount() > 0) {
                int target = currentRequest.blockAt(blockIndex);
                if (currentHead != target) {
                    totalHeadMovement += moveHeadToward(target);
                    return; // La transferencia ocupa otro ciclo, incluso si acaba de llegar.
                }
                blockIndex++;
            }
            if (blockIndex == currentRequest.getBlockCount()) {
                completions.encolar(currentRequest.complete(cycle, selectedPolicy));
                long waitCycles = currentRequest.getStartCycle()
        - currentRequest.getArrivalCycle();

totalWaitCycles += waitCycles;
waitByPolicy[selectedPolicy.ordinal()] += waitCycles;
                totalRequestsServed++;
                servedByPolicy[selectedPolicy.ordinal()]++;
                currentRequest = null;
            }
                } finally {
            try {
                if (currentCycle > previousCycle) {
                    recordMovement();
                }
            } finally {
                mutex.release();
            }
        }
    }
    public void cancelRequest(DiskRequest request) {
        if (request == null) {
            return;
        }

        mutex.acquireUninterruptibly();
        try {
            if (currentRequest == request) {
                request.cancel();
                currentRequest = null;
                blockIndex = 0;
                selectedPolicy = null;
            } else if (requestQueue.remover(request)) {
                request.cancel();
            }
        } finally {
            mutex.release();
        }
    }
    private void advanceSweep() {
        Sweep sweep = activePolicy.sweepWithoutRequest(getTotalBlocks() - 1, currentDirection);
        if (sweep == null) return;
        boolean alreadyAtTarget = currentHead == sweep.targetBlock();
        if (!alreadyAtTarget) totalHeadMovement += moveHeadToward(sweep.targetBlock());
        if (currentHead != sweep.targetBlock()) return;
        if (sweep.returnBlock() >= 0) {
            sweepReturnTarget = sweep.returnBlock();
            if (alreadyAtTarget) advanceSweepReturn();
        } else if (currentDirection != sweep.directionOnArrival()) {
            DiskDirection previousDirection = currentDirection;
            currentDirection = sweep.directionOnArrival();
            LOGGER.log(System.Logger.Level.INFO, "{0} cambia de {1} a {2} en el bloque {3}, ciclo {4}.",
                    activePolicy.getPolicy(), previousDirection, currentDirection, currentHead, currentCycle);
        }
    }

    // El retorno circular ocupa ciclos a la velocidad del disco y prolonga la espera.
    // Durante este trayecto no se seleccionan solicitudes ni se acumula desplazamiento.
    private void advanceSweepReturn() {
    if (currentHead != sweepReturnTarget) {
        moveHeadToward(sweepReturnTarget);
        totalReturnCycles++;
    }

    if (currentHead == sweepReturnTarget) {
        sweepReturnTarget = -1;
    }
}

    private int moveHeadToward(int target) {
        int distance = Math.min(speed, Math.abs(target - currentHead));
        currentDirection = target > currentHead ? DiskDirection.UP : DiskDirection.DOWN;
        currentHead += currentDirection == DiskDirection.UP ? distance : -distance;
        return distance;
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
            return snapshot();
        } finally {
            mutex.release();
        }
    }

    /** Estado y cola copiados bajo el mismo semaforo; la solicitud activa va primero. */
    public DiskQueueView getQueueView() {
        mutex.acquireUninterruptibly();
        try {
            int offset = currentRequest == null ? 0 : 1;
            DiskQueueEntry[] entries = new DiskQueueEntry[offset + requestQueue.getTamano()];
            if (currentRequest != null) entries[0] = entry(currentRequest, true);
            for (int i = 0; i < requestQueue.getTamano(); i++) {
                entries[offset + i] = entry(requestQueue.obtener(i), false);
            }
            return new DiskQueueView(snapshot(), entries);
        } finally {
            mutex.release();
        }
    }

    private DiskSnapshot snapshot() {
        return new DiskSnapshot(currentCycle, currentHead, currentDirection,
                activePolicy.getPolicy(), speed, totalHeadMovement, totalRequestsServed,
                currentRequest == null ? -1 : currentRequest.getId(),
                activeTarget(), requestQueue.getTamano());
    }

    private int activeTarget() {
        return currentRequest == null || currentRequest.getBlockCount() == 0
                ? -1 : currentRequest.blockAt(blockIndex);
    }

    private DiskQueueEntry entry(DiskRequest request, boolean active) {
        int target = active ? activeTarget() : request.getBlockCount() == 0 ? -1 : request.blockAt(0);
        return new DiskQueueEntry(request.getId(), request.getProcessId(), request.getProcessName(),
                request.getOperation(), target, request.getBlockCount(), request.getArrivalCycle(), active);
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

    /** Copia del mapa con bloques inmutables para los observadores. */
    public DiskBlock[] getBlocks() {
        return storage.getBlocks();
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

        public double getAverageWaitCycles() {
        mutex.acquireUninterruptibly();
        try {
            return totalRequestsServed == 0 ? 0.0
                    : (double) totalWaitCycles / totalRequestsServed;
        } finally {
            mutex.release();
        }
    }

    public double getAverageWaitCycles(DiskPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Falta la politica.");
        }

        mutex.acquireUninterruptibly();
        try {
            int index = policy.ordinal();
            return servedByPolicy[index] == 0 ? 0.0
                    : (double) waitByPolicy[index] / servedByPolicy[index];
        } finally {
            mutex.release();
        }
    }
        public record MovementSample(long cycle, long movement) {
    }

    private MovementSample[] movementHistory =
            new MovementSample[128];

    private int movementHistorySize;

    private void recordMovement() {
        if (movementHistorySize == movementHistory.length) {
            MovementSample[] expanded =
                    new MovementSample[movementHistory.length * 2];

            System.arraycopy(movementHistory, 0, expanded,
                    0, movementHistorySize);

            movementHistory = expanded;
        }

        movementHistory[movementHistorySize++] =
                new MovementSample(currentCycle, totalHeadMovement);
    }

    public MovementSample[] getMovementHistory(int fromIndex) {
        mutex.acquireUninterruptibly();
        try {
            if (fromIndex < 0 || fromIndex > movementHistorySize) {
                throw new IllegalArgumentException("Indice invalido.");
            }

            MovementSample[] copy =
                    new MovementSample[movementHistorySize - fromIndex];

            System.arraycopy(movementHistory, fromIndex,
                    copy, 0, copy.length);

            return copy;
        } finally {
            mutex.release();
        }
    }    
    public long getTotalReturnCycles() {
        mutex.acquireUninterruptibly();
        try {
            return totalReturnCycles;
        } finally {
            mutex.release();
        }
    }
    public DiskStorage getStorage() { return storage; }
    public void setLog(Log log) { this.log = log; }
    public int getTotalBlocks() { return storage.getTotalBlocks(); }
    public int getInitialHead() { return initialHead; }
    public int getSwapSizeBlocks() { return swapSizeBlocks; }
    public int getCurrentHead() { return getSnapshot().currentHead(); }
    public DiskDirection getCurrentDirection() { return getSnapshot().direction(); }
    public DiskPolicy getActivePolicyEnum() { return getSnapshot().policy(); }
    public long getTotalHeadMovement() { return getSnapshot().totalHeadMovement(); }
    public long getTotalRequestsServed() { return getSnapshot().totalRequestsServed(); }
}
