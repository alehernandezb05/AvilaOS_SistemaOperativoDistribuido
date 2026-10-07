package edu.unimet.avilaos.hardware;

/**
 * Todo componente que reacciona al reloj global (CPU, DMA, etc.)
 * implementa esta interfaz. En cada ciclo del reloj, el computador
 * le avisa para que avance un ciclo de ejecución.
 */
public interface ComponenteReloj {

    void alCiclo(long cicloGlobal);
}