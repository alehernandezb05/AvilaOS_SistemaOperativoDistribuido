package edu.unimet.avilaos.so;

import edu.unimet.avilaos.estructuras.Cola;
import java.util.concurrent.Semaphore;

/**
 * Log de eventos del sistema, en memoria. Lo escriben los nucleos desde el hilo
 * del reloj y lo lee la GUI, por eso un semaforo protege el acceso.
 * Guarda solo los ultimos eventos para no crecer sin limite.
 */
public class Log {

    public static final int CAPACIDAD_POR_DEFECTO = 500;

    private final Cola<String> eventos = new Cola<>();
    private final Semaphore mutex = new Semaphore(1);
    private final int capacidad;
    // Total de eventos registrados desde el inicio; sirve de indice para leer solo los nuevos
    private long totalRegistrados = 0;
    private volatile boolean ecoConsola = false;

    public Log() {
        this(CAPACIDAD_POR_DEFECTO);
    }

    public Log(int capacidad) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad del log debe ser mayor a 0.");
        }
        this.capacidad = capacidad;
    }

    /** Registra un evento con el ciclo en que ocurrio. */
    public void registrar(long ciclo, String mensaje) {
        String evento = "[ciclo " + ciclo + "] " + mensaje;
        mutex.acquireUninterruptibly();
        try {
            eventos.encolar(evento);
            totalRegistrados++;
            // Si se pasa de la capacidad se descarta el evento mas viejo
            if (eventos.getTamano() > capacidad) {
                eventos.desencolar();
            }
        } finally {
            mutex.release();
        }
        if (ecoConsola) {
            System.out.println(evento);
        }
    }

    /**
     * Devuelve los eventos registrados a partir del indice dado (en orden).
     * La GUI guarda getTotalRegistrados() y en el siguiente refresco pide desde ahi.
     * Si algunos ya fueron descartados, empieza por el mas viejo que se conserva.
     */
    public String[] obtenerDesde(long desde) {
        mutex.acquireUninterruptibly();
        try {
            long primero = totalRegistrados - eventos.getTamano();
            long inicio = Math.max(desde, primero);
            if (inicio >= totalRegistrados) {
                return new String[0];
            }
            String[] nuevos = new String[(int) (totalRegistrados - inicio)];
            for (int i = 0; i < nuevos.length; i++) {
                nuevos[i] = eventos.obtener((int) (inicio - primero) + i);
            }
            return nuevos;
        } finally {
            mutex.release();
        }
    }

    public long getTotalRegistrados() {
        mutex.acquireUninterruptibly();
        try {
            return totalRegistrados;
        } finally {
            mutex.release();
        }
    }

    /** Si esta activo, cada evento tambien se imprime en la consola (util para depurar). */
    public void setEcoConsola(boolean ecoConsola) {
        this.ecoConsola = ecoConsola;
    }
}
