package edu.unimet.avilaos.hardware;

import java.util.concurrent.Semaphore;

/** Memoria principal: solo controla la memoria usada y la libre (sin particiones). */
public class RAM {

    private final int total;
    private volatile int usada;
    private final Semaphore mutex = new Semaphore(1);

    public RAM(int total) {
        this.total = total;
        this.usada = 0;
    }

    /** Reserva memoria. Devuelve false si no hay espacio suficiente. */
    public boolean reservar(int cantidad) {
        if (cantidad <= 0) {
            return false;
        }
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            if (usada + cantidad > total) {
                return false;
            }
            usada += cantidad;
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Libera memoria previamente reservada. */
    public void liberar(int cantidad) {
        if (cantidad <= 0) {
            return;
        }
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            usada = Math.max(0, usada - cantidad);
        } finally {
            mutex.release();
        }
    }

    public boolean hayEspacio(int cantidad) {
        return usada + cantidad <= total;
    }

    public int getTotal() { return total; }
    public int getUsada() { return usada; }
    public int getLibre() { return total - usada; }
}