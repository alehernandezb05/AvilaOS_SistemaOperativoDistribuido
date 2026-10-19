package edu.unimet.avilaos.so;

import edu.unimet.avilaos.estructuras.Cola;
import java.util.concurrent.Semaphore;

/**
 * Las cuatro colas de un computador: nuevos, listos, bloqueados y terminados.
 * Un semaforo protege el acceso, porque lo usan varios hilos.
 */
public class ColasSO {

    private final Cola<PCB> nuevos = new Cola<>();
    private final Cola<PCB> listos = new Cola<>();
    private final Cola<PCB> bloqueados = new Cola<>();
    private final Cola<PCB> terminados = new Cola<>();
    private final Semaphore mutex = new Semaphore(1);

    public void agregarNuevo(PCB p) {
        mutex.acquireUninterruptibly();
        try {
            p.setEstado(EstadoProceso.NUEVO);
            nuevos.encolar(p);
        } finally {
            mutex.release();
        }
    }

    public void agregarListo(PCB p) {
        mutex.acquireUninterruptibly();
        try {
            p.setEstado(EstadoProceso.LISTO);
            p.setMotivoBloqueo(null);
            listos.encolar(p);
        } finally {
            mutex.release();
        }
    }

    public void agregarBloqueado(PCB p, MotivoBloqueo motivo) {
        mutex.acquireUninterruptibly();
        try {
            p.setEstado(EstadoProceso.BLOQUEADO);
            p.setMotivoBloqueo(motivo);
            bloqueados.encolar(p);
        } finally {
            mutex.release();
        }
    }

    public void agregarTerminado(PCB p) {
        mutex.acquireUninterruptibly();
        try {
            p.setEstado(EstadoProceso.TERMINADO);
            p.setMotivoBloqueo(null);
            terminados.encolar(p);
        } finally {
            mutex.release();
        }
    }

    public boolean quitarNuevo(PCB p) {
        mutex.acquireUninterruptibly();
        try {
            return nuevos.remover(p);
        } finally {
            mutex.release();
        }
    }

    public boolean quitarBloqueado(PCB p) {
        mutex.acquireUninterruptibly();
        try {
            return bloqueados.remover(p);
        } finally {
            mutex.release();
        }
    }

    /** Deja que la politica elija el siguiente proceso de la cola de listos. */
    public PCB seleccionarSiguiente(EstrategiaPlanificacion estrategia) {
        mutex.acquireUninterruptibly();
        try {
            return estrategia.seleccionarSiguiente(listos);
        } finally {
            mutex.release();
        }
    }

    public int cantidadNuevos() { return tamano(nuevos); }
    public int cantidadListos() { return tamano(listos); }
    public int cantidadBloqueados() { return tamano(bloqueados); }
    public int cantidadTerminados() { return tamano(terminados); }

    private int tamano(Cola<PCB> cola) {
        mutex.acquireUninterruptibly();
        try {
            return cola.getTamano();
        } finally {
            mutex.release();
        }
    }
}
