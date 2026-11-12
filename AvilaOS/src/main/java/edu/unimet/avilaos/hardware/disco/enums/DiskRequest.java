package edu.unimet.avilaos.hardware.disco.enums;

public class DiskRequest {
    private final int blockNumber;
    private final String processName;

    public DiskRequest(int blockNumber, String processName) {
        this.blockNumber = blockNumber;
        this.processName = processName;
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public String getProcessName() {
        return processName;
    }
}