package edu.unimet.avilaos.so.politicas;

import edu.unimet.avilaos.estructuras.Cola;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;

/**
 * Implementación de la política Earliest Deadline First (EDF).
 * Selecciona el proceso con el deadline más cercano (menor número de ciclos restantes para vencer).
 * Funciona de forma APROPIATIVA para evitar pérdidas de plazo de ejecución.
 */
public class EDF implements EstrategiaPlanificacion {

    @Override
    public PoliticaPlanificacion getPolitica() {
        return PoliticaPlanificacion.EDF;
    }

    /**
     * Evalúa si debe expropiar la CPU al proceso actual.
     * Retorna true si hay en la cola de listos un proceso cuyo deadline sea menor
     * (más urgente) que el del proceso actualmente en ejecución.
     */
    @Override
    public boolean debeExpropiar(PCB procesoEjecucion, Cola<PCB> colaListos, int ciclosEnEjecucion) {
        if (procesoEjecucion == null || colaListos == null || colaListos.estaVacia()) {
            return false;
        }

        int tamano = colaListos.getTamano();
        for (int i = 0; i < tamano; i++) {
            PCB listo = colaListos.obtener(i);
            if (listo != null && listo.getDeadline() < procesoEjecucion.getDeadline()) {
                return true; // Existe un proceso más urgente en estado LISTO
            }
        }
        return false;
    }

    /**
     * Busca el proceso con el deadline más cercano, lo saca de la cola y lo retorna.
     */
    @Override
    public PCB seleccionarSiguiente(Cola<PCB> colaListos) {
        if (colaListos == null || colaListos.estaVacia()) {
            return null;
        }

        PCB masUrgente = colaListos.obtener(0);
        int tamano = colaListos.getTamano();

        for (int i = 1; i < tamano; i++) {
            PCB actual = colaListos.obtener(i);
            if (actual != null && actual.getDeadline() < masUrgente.getDeadline()) {
                masUrgente = actual;
            }
        }

        // Remueve explícitamente el proceso de la cola (cumple Regla 1)
        colaListos.remover(masUrgente);
        return masUrgente;
    }
}