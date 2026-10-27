package edu.unimet.avilaos.so;

import edu.unimet.avilaos.estructuras.Cola;
import java.util.concurrent.Semaphore;

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

    public boolean quitarListo(PCB proceso) {
        mutex.acquireUninterruptibly();
        try {
            return listos.remover(proceso);
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

    public PCB obtenerNuevo(int indice) { return obtener(nuevos, indice); }
    public PCB obtenerListo(int indice) { return obtener(listos, indice); }
    public PCB obtenerBloqueado(int indice) { return obtener(bloqueados, indice); }
    public PCB obtenerTerminado(int indice) { return obtener(terminados, indice); }

    private PCB obtener(Cola<PCB> cola, int indice) {
        mutex.acquireUninterruptibly();
        try {
            return cola.obtener(indice);
        } finally {
            mutex.release();
        }
    }

    public PCB seleccionarSiguiente(EstrategiaPlanificacion estrategia) {
        mutex.acquireUninterruptibly();
        try {
            return estrategia.seleccionarSiguiente(listos);
        } finally {
            mutex.release();
        }
    }

    public boolean debeExpropiar(EstrategiaPlanificacion estrategia, PCB enEjecucion, int ciclosEnCpu) {
        mutex.acquireUninterruptibly();
        try {
            return estrategia.debeExpropiar(enEjecucion, listos, ciclosEnCpu);
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
        public PCB[] copiaDeTodos() {
        mutex.acquireUninterruptibly();
        try {
            int total = nuevos.getTamano() + listos.getTamano()
                    + bloqueados.getTamano() + terminados.getTamano();
            PCB[] copia = new PCB[total];
            int k = copiar(nuevos, copia, 0);
            k = copiar(listos, copia, k);
            k = copiar(bloqueados, copia, k);
            copiar(terminados, copia, k);
            return copia;
        } finally {
            mutex.release();
        }
    }

    private int copiar(Cola<PCB> cola, PCB[] destino, int desde) {
        int n = cola.getTamano();
        for (int i = 0; i < n; i++) {
            destino[desde + i] = cola.obtener(i);
        }
        return desde + n;
    }
}