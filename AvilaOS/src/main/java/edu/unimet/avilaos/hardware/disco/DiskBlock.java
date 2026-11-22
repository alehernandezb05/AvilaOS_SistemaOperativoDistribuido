package edu.unimet.avilaos.hardware.disco;

public class DiskBlock {
    private final int blockNumber;
    private boolean occupied;
    private String ownerName;
    private String contentType; // "FREE", "FILE", "SWAP"
    private boolean damaged;

    public DiskBlock(int blockNumber) {
        this.blockNumber = blockNumber;
        this.occupied = false;
        this.ownerName = null;
        this.contentType = "FREE";
        this.damaged = false;
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isDamaged() {
        return damaged;
    }

    public void setDamaged(boolean damaged) {
        this.damaged = damaged;
    }
}