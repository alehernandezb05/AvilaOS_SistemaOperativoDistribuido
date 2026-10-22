package edu.unimet.avilaos.so.politicas;

import edu.unimet.avilaos.estructuras.Cola;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;

/**
 * Implementación de la política de planificación Round Robin.
 */
public class RoundRobin implements EstrategiaPlanificacion {

    private int quantum;

    public RoundRobin(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor a 0.");
        }
        this.quantum = quantum;
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        if (quantum > 0) {
            this.quantum = quantum;
        }
    }

    @Override
    public PoliticaPlanificacion getPolitica() {
        return PoliticaPlanificacion.ROUND_ROBIN;
    }

    @Override
    public boolean debeExpropiar(PCB procesoEjecucion, Cola<PCB> colaListos, int ciclosEnEjecucion) {
        if (procesoEjecucion == null) {
            return false;
        }
        // Expropia al proceso de la CPU cuando sus ciclos consumidos alcanzan o superan el quantum
        return ciclosEnEjecucion >= quantum;
    }

    @Override
    public PCB seleccionarSiguiente(Cola<PCB> colaListos) {
        if (colaListos == null || colaListos.estaVacia()) {
            return null;
        }
        // Retorna y remueve el primer proceso en la cola de listos (FIFO)
        return colaListos.desencolar();
    }
}