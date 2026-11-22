package edu.unimet.avilaos.hardware;

import edu.unimet.avilaos.estructuras.Cola;
import java.util.concurrent.Semaphore;

/**
 * Reloj global: un hilo que, en cada ciclo, avisa a todos los componentes
 * registrados para que avancen exactamente un ciclo.
 */
public class Reloj implements Runnable {

    private final Cola<ComponenteReloj> componentes = new Cola<>();
    private final Semaphore mutex = new Semaphore(1);

    private volatile long cicloActual = 0;
    private volatile int duracionCicloMs;
    private volatile boolean corriendo = false;
    private volatile boolean pausado = false;
    private Thread hilo;

    public Reloj(int duracionCicloMs) {
        this.duracionCicloMs = Math.max(1, duracionCicloMs);
    }

    public void registrar(ComponenteReloj componente) {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            componentes.encolar(componente);
        } finally {
            mutex.release();
        }
    }

    public void iniciar() {
        if (corriendo) {
            return;
        }
        corriendo = true;
        hilo = new Thread(this, "Reloj-Global");
        hilo.start();
    }

    public void detener() {
        corriendo = false;
        if (hilo != null) {
            hilo.interrupt();
        }
    }

    /** Espera el fin del ciclo en curso antes de confirmar la pausa. */
    public void pausar() {
        if (Thread.currentThread() == hilo) {
            pausado = true;
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            pausado = true;
        } finally {
            mutex.release();
        }
    }
    public void reanudar() { pausado = false; }

    @Override
    public void run() {
        while (corriendo) {
            notificarComponentes();
            try {
                Thread.sleep(duracionCicloMs);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void notificarComponentes() {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            if (!corriendo || pausado) return;
            cicloActual++;
            for (int i = 0; i < componentes.getTamano(); i++) {
                componentes.obtener(i).alCiclo(cicloActual);
            }
        } finally {
            mutex.release();
        }
    }

    public long getCicloActual() { return cicloActual; }
    public int getDuracionCicloMs() { return duracionCicloMs; }
    public boolean estaCorriendo() { return corriendo && !pausado; }
    public boolean estaPausado() { return pausado; }

    /** Se puede cambiar en tiempo de ejecucion. Ignora valores invalidos. */
    public void setDuracionCicloMs(int ms) {
        if (ms >= 1) {
            this.duracionCicloMs = ms;
        }
    }
}