package edu.unimet.avilaos.so;

import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.hardware.ComponenteReloj;
import edu.unimet.avilaos.hardware.RAM;

public class NucleoSO implements ComponenteReloj {

    private final int idComputador;
    private final CPU cpu;
    private final RAM ram;
    private final ColasSO colas = new ColasSO();
    private volatile EstrategiaPlanificacion estrategia;
    private int ciclosEnCpu = 0;
    private volatile Log log;               
    private volatile long cicloActual = 0;  

    public NucleoSO(int idComputador, CPU cpu, RAM ram, EstrategiaPlanificacion estrategia) {
        this.idComputador = idComputador;
        this.cpu = cpu;
        this.ram = ram;
        this.estrategia = estrategia;
    }

    public void agregarProceso(PCB proceso) {
        colas.agregarNuevo(proceso);
    }

    @Override
    public void alCiclo(long cicloGlobal) {
        cicloActual = cicloGlobal;
        procesarDeadlines(cicloGlobal);
        terminarSiCorresponde(cicloGlobal);
        admitirNuevos();
        planificar();
        if (!cpu.estaLibre()) {
            ciclosEnCpu++;
        }
    }

    private void procesarDeadlines(long cicloGlobal) {
        PCB actual = cpu.getProcesoActual();
        if (actual != null) {
            actual.decrementarDeadline();
            if (actual.deadlineVencido()) {
                cpu.liberar();
                ram.liberar(actual.getMemoria());
                actual.setEstado(EstadoProceso.TERMINADO);
                actual.setCicloFin(cicloGlobal);
                colas.agregarTerminado(actual);
                ciclosEnCpu = 0;
                registrar("termina el proceso " + actual + " por vencimiento de deadline");
            }
        }

        int i = 0;
        PCB listo = colas.obtenerListo(i);
        while (listo != null) {
            listo.decrementarDeadline();
            if (listo.deadlineVencido()) {
                colas.quitarListo(listo);
                ram.liberar(listo.getMemoria());
                listo.setEstado(EstadoProceso.TERMINADO);
                listo.setCicloFin(cicloGlobal);
                colas.agregarTerminado(listo);
                registrar("vence deadline del proceso " + listo + " en cola de listos");
            } else {
                i++;
            }
            listo = colas.obtenerListo(i);
        }
    }

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

    private void admitirNuevos() {
        int i = 0;
        PCB p = colas.obtenerNuevo(i);
        while (p != null) {
            if (ram.reservar(p.getMemoria())) {
                colas.quitarNuevo(p);
                colas.agregarListo(p);
                registrar("admite el proceso " + p + " (nuevo -> listo)");
            } else {
                i++;
            }
            p = colas.obtenerNuevo(i);
        }
    }

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

    private void registrar(String mensaje) {
        Log l = log;
        if (l != null) {
            l.registrar(cicloActual, "Computador " + idComputador + " " + mensaje);
        }
    }

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