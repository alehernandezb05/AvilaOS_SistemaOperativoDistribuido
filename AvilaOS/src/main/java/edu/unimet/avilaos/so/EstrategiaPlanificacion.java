package edu.unimet.avilaos.so;

import edu.unimet.avilaos.estructuras.Cola;

/**
 * Contrato de toda politica de planificacion. Agregar una politica nueva
 * es crear una clase que implemente esta interfaz, sin tocar el planificador.
 */
public interface EstrategiaPlanificacion {

    /** Elige el siguiente proceso de la cola de listos, lo saca de la cola y lo devuelve (null si esta vacia). */
    PCB seleccionarSiguiente(Cola<PCB> listos);

    /** Indica si el proceso que esta en la CPU debe ser expulsado para dar paso a otro. */
    boolean debeExpropiar(PCB enEjecucion, Cola<PCB> listos, int ciclosEnCpu);

    /** Devuelve el enum que identifica a esta politica. */
    PoliticaPlanificacion getPolitica();
}