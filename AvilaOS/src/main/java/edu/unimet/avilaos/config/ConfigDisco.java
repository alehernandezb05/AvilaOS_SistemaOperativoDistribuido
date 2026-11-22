package edu.unimet.avilaos.config;

import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;

/** Parametros del disco; los JSON anteriores pueden omitir este objeto. */
public final class ConfigDisco {
    private int totalBlocks = 200;
    private int initialHead = 50;
    private int swapSizeBlocks = 100;
    private int headSpeed = 1;
    private DiskPolicy initialPolicy = DiskPolicy.FIFO;

    public ConfigDisco() { }

    public ConfigDisco(int totalBlocks, int initialHead, int swapSizeBlocks,
                       int headSpeed, DiskPolicy initialPolicy) {
        this.totalBlocks = totalBlocks;
        this.initialHead = initialHead;
        this.swapSizeBlocks = swapSizeBlocks;
        this.headSpeed = headSpeed;
        this.initialPolicy = initialPolicy;
    }

    public void validar() throws ConfiguracionInvalidaException {
        if (totalBlocks < 1 || totalBlocks > DiskScheduler.MAX_BLOCKS
                || initialHead < 0 || initialHead >= totalBlocks
                || swapSizeBlocks < 0 || swapSizeBlocks > totalBlocks
                || headSpeed < 1 || headSpeed > totalBlocks || initialPolicy == null) {
            throw new ConfiguracionInvalidaException("Disco invalido: revisar bloques (1-"
                    + DiskScheduler.MAX_BLOCKS + "), cabezal, swap, velocidad y politica.");
        }
    }

    public int getTotalBlocks() { return totalBlocks; }
    public int getInitialHead() { return initialHead; }
    public int getSwapSizeBlocks() { return swapSizeBlocks; }
    public int getHeadSpeed() { return headSpeed; }
    public DiskPolicy getInitialPolicy() { return initialPolicy; }
}
