package edu.unimet.avilaos.so.politicas;

import edu.unimet.avilaos.estructuras.Cola;
import edu.unimet.avilaos.estructuras.Comparador;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;

/**
 * Implementación de la política Earliest Deadline First (EDF).
 * Selecciona el proceso con el deadline más cercano (menor número de ciclos restantes para vencer).
 * Funciona de forma APROPIATIVA para evitar pérdidas de plazo de ejecución.
 */
public class EDF implements EstrategiaPlanificacion {

    // Deadline mas cercano primero; los empates conservan el orden de llegada
    private static final Comparador<PCB> POR_DEADLINE =
            (a, b) -> Integer.compare(a.getDeadline(), b.getDeadline());

    @Override
    public PoliticaPlanificacion getPolitica() {
        return PoliticaPlanificacion.EDF;
    }

    /**
     * Evalúa si debe expropiar la CPU al proceso actual.
     * Ordena la cola de listos y retorna true si el primero tiene un deadline menor
     * (más urgente) que el del proceso actualmente en ejecución.
     */
    @Override
    public boolean debeExpropiar(PCB procesoEjecucion, Cola<PCB> colaListos, int ciclosEnEjecucion) {
        if (procesoEjecucion == null || colaListos == null || colaListos.estaVacia()) {
            return false;
        }

        colaListos.ordenar(POR_DEADLINE);
        return colaListos.verFrente().getDeadline() < procesoEjecucion.getDeadline();
    }

    /**
     * Ordena la cola de listos por deadline y saca el primero, de modo que la cola
     * queda ordenada y visible para la GUI.
     */
    @Override
    public PCB seleccionarSiguiente(Cola<PCB> colaListos) {
        if (colaListos == null || colaListos.estaVacia()) {
            return null;
        }

        colaListos.ordenar(POR_DEADLINE);
        return colaListos.desencolar();
    }
}
