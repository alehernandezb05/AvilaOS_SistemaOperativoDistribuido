package edu.unimet.avilaos.hardware;

import edu.unimet.avilaos.estructuras.Cola;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

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
        if (corriendo || (hilo != null && hilo.isAlive())) {
            return;
        }
        corriendo = true;
        hilo = new Thread(this, "Reloj-Global");
        hilo.start();
    }

    public void detener() {
        // No interrumpir una reserva o liberacion a mitad de un ciclo.
        if (Thread.currentThread() == hilo) {
            corriendo = false;
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            corriendo = false;
            if (hilo != null) hilo.interrupt(); // ya no hay un ciclo en curso
        } finally {
            mutex.release();
        }
    }

    /** Solo operaciones del modelo; nunca invocar Swing ni esperar al EDT aqui. */
    public <T> T entreCiclos(Supplier<T> operacion) {
        if (Thread.currentThread() == hilo) return operacion.get();
        mutex.acquireUninterruptibly();
        try { return operacion.get(); }
        finally { mutex.release(); }
    }

    /** El refresco puede saltarse un ciclo ocupado, sin bloquear el EDT. */
    public <T> T intentarObservar(Supplier<T> observacion) {
        if (!mutex.tryAcquire()) return null;
        try { return observacion.get(); }
        finally { mutex.release(); }
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
                corriendo = false;
                Thread.currentThread().interrupt();
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