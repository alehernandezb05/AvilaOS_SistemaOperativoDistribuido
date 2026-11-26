package edu.unimet.avilaos.hardware.disco;

/**
 * Estado del disco y su cola tomados en el mismo ciclo.
 * La solicitud activa, si existe, va primero en entries.
 */
public record DiskQueueView(DiskSnapshot state, DiskQueueEntry[] entries) {
    public DiskQueueView { entries = entries.clone(); }
    @Override public DiskQueueEntry[] entries() { return entries.clone(); }
}
