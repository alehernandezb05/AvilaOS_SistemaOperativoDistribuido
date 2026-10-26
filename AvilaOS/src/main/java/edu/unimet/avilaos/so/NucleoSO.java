package edu.unimet.avilaos.so;

import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.hardware.ComponenteReloj;
import edu.unimet.avilaos.hardware.RAM;

/**
 * Nucleo del sistema operativo de un computador. En cada ciclo del reloj
 * termina procesos, admite nuevos, y decide quien usa la CPU segun la politica activa.
 * IMPORTANTE: se registra en el reloj ANTES que la CPU, para que decida
 * primero y la CPU ejecute despues.
 */
public class NucleoSO implements ComponenteReloj {

    private final int idComputador;
    private final CPU cpu;
    private final RAM ram;
    private final ColasSO colas = new ColasSO();
    private volatile EstrategiaPlanificacion estrategia;
    private int ciclosEnCpu = 0;
    private volatile Log log;               // opcional: si es null no se registra nada
    private volatile long cicloActual = 0;  // ultimo ciclo recibido, para fechar los eventos

    public NucleoSO(int idComputador, CPU cpu, RAM ram, EstrategiaPlanificacion estrategia) {
        this.idComputador = idComputador;
        this.cpu = cpu;
        this.ram = ram;
        this.estrategia = estrategia;
    }

    /** Un proceso nuevo entra a la cola de nuevos. */
    public void agregarProceso(PCB proceso) {
        colas.agregarNuevo(proceso);
    }

    @Override
    public void alCiclo(long cicloGlobal) {
        cicloActual = cicloGlobal;
        terminarSiCorresponde(cicloGlobal);
        admitirNuevos();
        planificar();
        if (!cpu.estaLibre()) {
            ciclosEnCpu++;
        }
    }

    /** Si el proceso de la CPU ya no tiene instrucciones, lo termina y libera su memoria. */
    private void terminarSiCorresponde(long cicloGlobal) {
        PCB actual = cpu.getProcesoActual();
        if (actual != null && actual.haTerminado()) {
            cpu.liberar();
            ram.liberar(actual.getMemoria());
            actual.setCicloFin(cicloGlobal);
            colas.agregarTerminado(actual);
            ciclosEnCpu = 0;
            registrar("termina el proceso " + actual + " y libera " + actual.getMemoria() + " de RAM");
        }
    }

    /** Pasa de nuevos a listos los procesos que caben en la RAM. */
    private void admitirNuevos() {
        int i = 0;
        PCB p = colas.obtenerNuevo(i);
        while (p != null) {
            if (ram.reservar(p.getMemoria())) {
                colas.quitarNuevo(p);
                colas.agregarListo(p);
                registrar("admite el proceso " + p + " (nuevo -> listo)");
                // no se avanza i: al quitar p, el siguiente ocupa su lugar
            } else {
                i++;
            }
            p = colas.obtenerNuevo(i);
        }
    }

    /** Expulsa al proceso actual si la politica lo pide y asigna la CPU si esta libre. */
    private void planificar() {
        PCB actual = cpu.getProcesoActual();
        if (actual != null && colas.debeExpropiar(estrategia, actual, ciclosEnCpu)) {
            cpu.liberar();
            colas.agregarListo(actual);
            ciclosEnCpu = 0;
            registrar("expropia el proceso " + actual + " (" + estrategia.getPolitica() + ")");
            actual = null;
        }
        if (actual == null) {
            PCB siguiente = colas.seleccionarSiguiente(estrategia);
            if (siguiente != null) {
                siguiente.setEstado(EstadoProceso.EJECUCION);
                cpu.asignar(siguiente);
                ciclosEnCpu = 0;
                registrar("selecciona el proceso " + siguiente);
            }
        }
    }

    /** Agrega el evento al log con el prefijo del computador, si hay log. */
    private void registrar(String mensaje) {
        Log l = log;
        if (l != null) {
            l.registrar(cicloActual, "Computador " + idComputador + " " + mensaje);
        }
    }

    /** Cambia la politica en tiempo de ejecucion. */
    public void setEstrategia(EstrategiaPlanificacion estrategia) {
        this.estrategia = estrategia;
        registrar("cambia su politica a " + estrategia.getPolitica());
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public EstrategiaPlanificacion getEstrategia() { return estrategia; }
    public int getIdComputador() { return idComputador; }
    public CPU getCpu() { return cpu; }
    public RAM getRam() { return ram; }
    public ColasSO getColas() { return colas; }
}