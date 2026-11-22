package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequestStatus;

/** Solicitud con identidad propia y recorrido de bloques inmutable. */
public final class DiskRequest {

    private static long nextId;
    private final long id;
    private final int processId;
    private final String processName;
    private final DiskOperation operation;
    private final int[] blockNumbers;
    private volatile Progress progress = new Progress(DiskRequestStatus.NEW, -1, -1, null);

    private record Progress(DiskRequestStatus status, long arrival, long start, DiskCompletion result) { }

    public DiskRequest(int processId, String processName, DiskOperation operation, int blockNumber) {
        this(processId, processName, operation, new int[] {blockNumber});
    }

    public DiskRequest(int processId, String processName, DiskOperation operation, int[] blockNumbers) {
        if (processId < 1 || processName == null || processName.isBlank() || operation == null) {
            throw new IllegalArgumentException("La solicitud necesita ID de proceso, nombre y operacion.");
        }
        if (blockNumbers == null || blockNumbers.length == 0) {
            throw new IllegalArgumentException("La solicitud necesita al menos un bloque.");
        }
        this.blockNumbers = blockNumbers.clone();
        for (int block : this.blockNumbers) {
            if (block < 0) throw new IllegalArgumentException("El numero de bloque no puede ser negativo.");
        }
        this.id = nextId();
        this.processId = processId;
        this.processName = processName;
        this.operation = operation;
    }

    private static synchronized long nextId() { return ++nextId; }

    synchronized void enqueue(long cycle) {
        if (progress.status() != DiskRequestStatus.NEW) {
            throw new IllegalArgumentException("La solicitud ya fue enviada a un disco.");
        }
        progress = new Progress(DiskRequestStatus.QUEUED, cycle, -1, null);
    }

    void start(long cycle) {
        if (progress.status() != DiskRequestStatus.QUEUED) {
            throw new IllegalStateException("Solo puede atenderse una solicitud en cola.");
        }
        progress = new Progress(DiskRequestStatus.ACTIVE, progress.arrival(), cycle, null);
    }

    DiskCompletion complete(long cycle, DiskPolicy policy) {
        Progress previous = progress;
        if (previous.status() != DiskRequestStatus.ACTIVE) {
            throw new IllegalStateException("La solicitud no esta activa.");
        }
        DiskCompletion result = new DiskCompletion(id, processId, processName, operation,
                previous.arrival(), previous.start(), cycle, policy);
        progress = new Progress(DiskRequestStatus.COMPLETED, previous.arrival(), previous.start(), result);
        return result;
    }

    int blockAt(int index) { return blockNumbers[index]; }
    public long getId() { return id; }
    public int getProcessId() { return processId; }
    public String getProcessName() { return processName; }
    public DiskOperation getOperation() { return operation; }
    public int getBlockNumber() { return blockNumbers[0]; }
    public int getBlockCount() { return blockNumbers.length; }
    public int[] getBlockNumbers() { return blockNumbers.clone(); }
    public DiskRequestStatus getStatus() { return progress.status(); }
    public long getArrivalCycle() { return progress.arrival(); }
    public long getStartCycle() { return progress.start(); }
    public DiskCompletion getResult() { return progress.result(); }
}
