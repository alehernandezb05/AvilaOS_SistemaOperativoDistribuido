package edu.unimet.avilaos.hardware;

import edu.unimet.avilaos.so.PCB;

/**
 * Procesador: no toma decisiones. Guarda el proceso que le asignan y,
 * en cada ciclo del reloj, ejecuta una instruccion de ese proceso.
 */
public class CPU implements ComponenteReloj {

    private volatile PCB procesoActual = null;
    private volatile ModoEjecucion modo = ModoEjecucion.SISTEMA_OPERATIVO;
    private volatile long ciclosOcupada = 0;
    private volatile long ciclosTotales = 0;

    /** Le da un proceso a la CPU y pasa a modo usuario. */
    public void asignar(PCB proceso) {
        this.procesoActual = proceso;
        this.modo = ModoEjecucion.USUARIO;
    }

    /** Quita el proceso de la CPU y lo devuelve. La CPU vuelve a modo sistema operativo. */
    public PCB liberar() {
        PCB p = procesoActual;
        procesoActual = null;
        modo = ModoEjecucion.SISTEMA_OPERATIVO;
        return p;
    }

    public boolean estaLibre() {
        return procesoActual == null;
    }

    @Override
    public void alCiclo(long cicloGlobal) {
        ciclosTotales++;
        PCB p = procesoActual;
        if (p != null) {
            p.ejecutarInstruccion();
            ciclosOcupada++;
        }
    }

    public PCB getProcesoActual() { return procesoActual; }
    public ModoEjecucion getModo() { return modo; }
    public void setModo(ModoEjecucion modo) { this.modo = modo; }
    public long getCiclosOcupada() { return ciclosOcupada; }
    public long getCiclosTotales() { return ciclosTotales; }

    /** Porcentaje de ciclos en que la CPU tuvo un proceso (entre 0 y 1). */
    public double getUtilizacion() {
        return ciclosTotales == 0 ? 0 : (double) ciclosOcupada / ciclosTotales;
    }
}
