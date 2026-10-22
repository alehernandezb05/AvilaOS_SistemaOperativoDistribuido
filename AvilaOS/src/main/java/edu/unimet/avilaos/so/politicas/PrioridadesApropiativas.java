package edu.unimet.avilaos.so.politicas;

import edu.unimet.avilaos.estructuras.Cola;
import edu.unimet.avilaos.estructuras.Comparador;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;

/**
 * Implementación de la política de planificación por Prioridades Apropiativas.
 * Criterio: Menor valor numérico representa MAYOR prioridad.
 */
public class PrioridadesApropiativas implements EstrategiaPlanificacion {

    // Menor valor numerico primero; los empates conservan el orden de llegada
    private static final Comparador<PCB> POR_PRIORIDAD =
            (a, b) -> Integer.compare(a.getPrioridad(), b.getPrioridad());

    @Override
    public PoliticaPlanificacion getPolitica() {
        return PoliticaPlanificacion.PRIORIDADES_APROPIATIVAS;
    }

    /**
     * Evalúa si debe expropiar la CPU al proceso en ejecución.
     * Ordena la cola de listos y retorna true si el primero tiene mayor prioridad (menor valor numérico).
     */
    @Override
    public boolean debeExpropiar(PCB procesoEjecucion, Cola<PCB> colaListos, int ciclosEnEjecucion) {
        if (procesoEjecucion == null || colaListos == null || colaListos.estaVacia()) {
            return false;
        }

        colaListos.ordenar(POR_PRIORIDAD);
        return colaListos.verFrente().getPrioridad() < procesoEjecucion.getPrioridad();
    }

    /**
     * Ordena la cola de listos por prioridad y saca el primero, de modo que la cola
     * queda ordenada y visible para la GUI.
     */
    @Override
    public PCB seleccionarSiguiente(Cola<PCB> colaListos) {
        if (colaListos == null || colaListos.estaVacia()) {
            return null;
        }

        colaListos.ordenar(POR_PRIORIDAD);
        return colaListos.desencolar();
    }
}
