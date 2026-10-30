package edu.unimet.avilaos.cluster;

import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.hardware.RAM;
import edu.unimet.avilaos.hardware.Reloj;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.Log;
import edu.unimet.avilaos.so.NucleoSO;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.ServicioCluster;

public class Computador {

    private final int id;
    private final CPU cpu;
    private final RAM ram;
    private final NucleoSO nucleo;

    public Computador(int id, int tamanoRam, EstrategiaPlanificacion estrategia, Reloj reloj) {
        this.id = id;
        this.cpu = new CPU();
        this.ram = new RAM(tamanoRam);
        this.nucleo = new NucleoSO(id, cpu, ram, estrategia);
        reloj.registrar(nucleo);
        reloj.registrar(cpu);
    }

    public void agregarProceso(PCB proceso) {
        nucleo.agregarProceso(proceso);
    }

    public void cambiarPolitica(EstrategiaPlanificacion estrategia) {
        nucleo.setEstrategia(estrategia);
    }

    public void setLog(Log log) {
        nucleo.setLog(log);
    }

    public void setServicio(ServicioCluster servicio) {
        nucleo.setServicio(servicio);
    }

    public int getId() { return id; }
    public CPU getCpu() { return cpu; }
    public RAM getRam() { return ram; }
    public NucleoSO getNucleo() { return nucleo; }
}