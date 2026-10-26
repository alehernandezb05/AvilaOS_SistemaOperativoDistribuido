package edu.unimet.avilaos.cluster;

import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.hardware.RAM;
import edu.unimet.avilaos.hardware.Reloj;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.Log;
import edu.unimet.avilaos.so.NucleoSO;
import edu.unimet.avilaos.so.PCB;

/**
 * Un computador simulado: CPU, RAM y su propio nucleo de SO.
 * Todos los computadores del cluster son instancias de esta misma clase.
 */
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
        // El orden importa: primero el nucleo decide, luego la CPU ejecuta
        reloj.registrar(nucleo);
        reloj.registrar(cpu);
    }

    public void agregarProceso(PCB proceso) {
        nucleo.agregarProceso(proceso);
    }

    /** Cambia la politica de planificacion de este computador, sin afectar a los demas. */
    public void cambiarPolitica(EstrategiaPlanificacion estrategia) {
        nucleo.setEstrategia(estrategia);
    }

    /** Conecta el log global del sistema al nucleo de este computador. */
    public void setLog(Log log) {
        nucleo.setLog(log);
    }

    public int getId() { return id; }
    public CPU getCpu() { return cpu; }
    public RAM getRam() { return ram; }
    public NucleoSO getNucleo() { return nucleo; }
}