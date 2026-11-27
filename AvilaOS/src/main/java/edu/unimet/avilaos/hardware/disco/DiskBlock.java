package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.hardware.disco.enums.DiskBlockType;

/** Estado inmutable de una posición del mapa del disco. */
public record DiskBlock(int blockNumber, DiskBlockType contentType, String filePath,
                        int processId, int nextBlock) {

    public static final int END_OF_CHAIN = -1;

    public boolean isOccupied() {
        return contentType == DiskBlockType.FILE || contentType == DiskBlockType.SWAP_OCCUPIED;
    }
}
