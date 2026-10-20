package edu.unimet.avilaos.so.politicas;

import edu.unimet.avilaos.estructuras.Cola;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;

/**
 * Implementación de la política de planificación por Prioridades Apropiativas.
 * Criterio: Menor valor numérico representa MAYOR prioridad.
 */
public class PrioridadesApropiativas implements EstrategiaPlanificacion {

    @Override
    public PoliticaPlanificacion getPolitica() {
        return PoliticaPlanificacion.PRIORIDADES_APROPIATIVAS;
    }

    /**
     * Evalúa si debe expropiar la CPU al proceso en ejecución.
     * Retorna true si en la cola de listos existe algún proceso con mayor prioridad (menor valor numérico).
     */
    @Override
    public boolean debeExpropiar(PCB procesoEjecucion, Cola<PCB> colaListos, int ciclosEnEjecucion) {
        if (procesoEjecucion == null || colaListos == null || colaListos.estaVacia()) {
            return false;
        }

        int tamano = colaListos.getTamano();
        for (int i = 0; i < tamano; i++) {
            PCB listo = colaListos.obtener(i);
            if (listo != null && listo.getPrioridad() < procesoEjecucion.getPrioridad()) {
                return true; // Existe un proceso en listos con mayor prioridad
            }
        }
        return false;
    }

    /**
     * Busca el proceso con mayor prioridad en la cola de listos, lo remueve y lo devuelve.
     */
    @Override
    public PCB seleccionarSiguiente(Cola<PCB> colaListos) {
        if (colaListos == null || colaListos.estaVacia()) {
            return null;
        }

        PCB masPrioritario = colaListos.obtener(0);
        int tamano = colaListos.getTamano();

        for (int i = 1; i < tamano; i++) {
            PCB actual = colaListos.obtener(i);
            if (actual != null && actual.getPrioridad() < masPrioritario.getPrioridad()) {
                masPrioritario = actual;
            }
        }

        // Remueve el proceso seleccionado de la cola (cumple Regla 1)
        colaListos.remover(masPrioritario);
        return masPrioritario;
    }
}