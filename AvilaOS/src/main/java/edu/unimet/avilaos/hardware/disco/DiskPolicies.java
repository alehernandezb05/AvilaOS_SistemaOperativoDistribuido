package edu.unimet.avilaos.hardware.disco;

import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.policies.CScanDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.policies.FifoDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.policies.ScanDiskSchedulerPolicy;
import edu.unimet.avilaos.hardware.disco.policies.SstfDiskSchedulerPolicy;

/** La fabrica de estrategias queda fuera del motor del disco. */
public final class DiskPolicies {
    private DiskPolicies() { }

    public static IDiskSchedulerPolicy create(DiskPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Falta la politica del disco.");
        return switch (policy) {
            case FIFO -> new FifoDiskSchedulerPolicy();
            case SSTF -> new SstfDiskSchedulerPolicy();
            case SCAN -> new ScanDiskSchedulerPolicy();
            case C_SCAN -> new CScanDiskSchedulerPolicy();
        };
    }
}
