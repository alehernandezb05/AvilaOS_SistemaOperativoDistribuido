package edu.unimet.avilaos.so;

import java.util.concurrent.Semaphore;

/**
 * Buffer acotado del productor-consumidor. Vive en la RAM de su computador
 * anfitrion y se sincroniza con tres semaforos de AvilaOS:
 * vacios (conteo, empieza en la capacidad), llenos (conteo, empieza en 0)
 * y mutex (binario, protege la region critica).
 *
 * insertar y extraer solo cambian el contenido; el orden de los semWait y
 * semSignal lo sigue el productor y el consumidor.
 */
public class Buffer {

    public static final int MIN_CAPACIDAD = 1;
    public static final int MAX_CAPACIDAD = 100;
    /** RAM que ocupa cada elemento de capacidad en el computador anfitrion. */
    public static final int MEMORIA_POR_ELEMENTO = 1;

    // Contador compartido: IDs B1, B2... unicos en todo el sistema
    private static int contadorIds = 0;

    private static synchronized String siguienteId() {
        contadorIds++;
        return "B" + contadorIds;
    }

    private final String id;
    private final int idAnfitrion;
    private final int capacidad;
    private final Semaforo vacios;
    private final Semaforo llenos;
    private final Semaforo mutex;

    private int elementos = 0;
    // Totales historicos, para las metricas
    private long producidos = 0;
    private long consumidos = 0;

    // Protege el contenido entre el hilo del reloj y la GUI (no es un semaforo de AvilaOS)
    private final Semaphore cerrojo = new Semaphore(1);

    public Buffer(int capacidad, int idAnfitrion) {
        if (capacidad < MIN_CAPACIDAD || capacidad > MAX_CAPACIDAD) {
            throw new IllegalArgumentException("La capacidad del buffer debe estar entre " + MIN_CAPACIDAD
                    + " y " + MAX_CAPACIDAD + " (vale " + capacidad + ").");
        }
        this.id = siguienteId();
        this.idAnfitrion = idAnfitrion;
        this.capacidad = capacidad;
        this.vacios = new Semaforo(id + ".vacios", TipoSemaforo.CONTEO, capacidad);
        this.llenos = new Semaforo(id + ".llenos", TipoSemaforo.CONTEO, 0);
        this.mutex = new Semaforo(id + ".mutex", TipoSemaforo.BINARIO, 1);
    }

    /** Agrega un elemento. Lanza IllegalStateException si esta lleno (la sincronizacion fallo). */
    public void insertar() {
        cerrojo.acquireUninterruptibly();
        try {
            if (elementos >= capacidad) {
                throw new IllegalStateException("Insercion en el buffer " + id + " lleno (" + capacidad + ").");
            }
            elementos++;
            producidos++;
        } finally {
            cerrojo.release();
        }
    }

    /** Saca un elemento. Lanza IllegalStateException si esta vacio (la sincronizacion fallo). */
    public void extraer() {
        cerrojo.acquireUninterruptibly();
        try {
            if (elementos <= 0) {
                throw new IllegalStateException("Extraccion del buffer " + id + " vacio.");
            }
            elementos--;
            consumidos++;
        } finally {
            cerrojo.release();
        }
    }

    /** true si el proceso esta en otro computador: cada acceso paga la latencia de red. */
    public boolean esRemoto(PCB proceso) {
        return proceso.getIdComputador() != idAnfitrion;
    }

    public int getElementos() {
        cerrojo.acquireUninterruptibly();
        try {
            return elementos;
        } finally {
            cerrojo.release();
        }
    }

    public long getProducidos() {
        cerrojo.acquireUninterruptibly();
        try {
            return producidos;
        } finally {
            cerrojo.release();
        }
    }

    public long getConsumidos() {
        cerrojo.acquireUninterruptibly();
        try {
            return consumidos;
        } finally {
            cerrojo.release();
        }
    }

    public String getId() { return id; }
    public int getIdAnfitrion() { return idAnfitrion; }
    public int getCapacidad() { return capacidad; }
    public int getMemoria() { return capacidad * MEMORIA_POR_ELEMENTO; }
    public Semaforo getVacios() { return vacios; }
    public Semaforo getLlenos() { return llenos; }
    public Semaforo getMutex() { return mutex; }

    @Override
    public String toString() {
        return id + " (" + getElementos() + "/" + capacidad + ")";
    }
}
