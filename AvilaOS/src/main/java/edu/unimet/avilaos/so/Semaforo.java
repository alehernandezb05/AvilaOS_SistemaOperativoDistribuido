package edu.unimet.avilaos.so;

import edu.unimet.avilaos.estructuras.Cola;
import java.util.concurrent.Semaphore;

/**
 * Semaforo del sistema operativo simulado: un contador y una cola propia de
 * procesos bloqueados. Un valor negativo indica cuantos procesos esperan.
 *
 * Solo decide quien se bloquea y quien despierta; cambiar el estado del proceso
 * y liberar o devolver la CPU lo hace el nucleo. semSignal devuelve el proceso
 * despertado para que se desbloquee en su propio computador, que puede no ser
 * el del buffer.
 *
 * No confundir con java.util.concurrent.Semaphore: ese solo protege este
 * objeto, porque lo modifica el hilo del reloj y lo lee la GUI.
 */
public class Semaforo {

    private final String nombre;
    private final TipoSemaforo tipo;
    private int valor;
    private final Cola<PCB> bloqueados = new Cola<>();
    private final Semaphore mutex = new Semaphore(1);

    public Semaforo(String nombre, TipoSemaforo tipo, int valorInicial) {
        if (tipo == TipoSemaforo.BINARIO && valorInicial != 0 && valorInicial != 1) {
            throw new IllegalArgumentException("Un semaforo binario empieza en 0 o 1 (vale " + valorInicial + ").");
        }
        if (valorInicial < 0) {
            throw new IllegalArgumentException("El valor inicial no puede ser negativo (vale " + valorInicial + ").");
        }
        this.nombre = nombre;
        this.tipo = tipo;
        this.valor = valorInicial;
    }

    /**
     * Resta 1. Si el valor queda negativo, el proceso entra a la cola de bloqueados.
     * @return true si el proceso puede seguir, false si debe bloquearse
     */
    public boolean semWait(PCB proceso) {
        mutex.acquireUninterruptibly();
        try {
            valor--;
            if (valor < 0) {
                bloqueados.encolar(proceso);
                return false;
            }
            return true;
        } finally {
            mutex.release();
        }
    }

    /**
     * Suma 1 y, si habia procesos esperando, saca al primero que llego.
     * @return el proceso que hay que desbloquear, o null si nadie esperaba
     */
    public PCB semSignal() {
        mutex.acquireUninterruptibly();
        try {
            // En un binario, sumar con el valor en 1 indica un semSignal sin su semWait
            if (tipo == TipoSemaforo.BINARIO && valor >= 1) {
                throw new IllegalStateException("semSignal sobre el semaforo binario " + nombre
                        + " que ya vale 1.");
            }
            valor++;
            if (valor <= 0) {
                return bloqueados.desencolar();
            }
            return null;
        } finally {
            mutex.release();
        }
    }

    /**
     * Saca a un proceso bloqueado que termino por otra causa (por ejemplo, vencio su
     * deadline) y corrige el valor, para que un semSignal posterior no lo despierte.
     * @return true si el proceso estaba esperando en este semaforo
     */
    public boolean quitar(PCB proceso) {
        mutex.acquireUninterruptibly();
        try {
            if (bloqueados.remover(proceso)) {
                valor++;
                return true;
            }
            return false;
        } finally {
            mutex.release();
        }
    }

    /** Copia de los procesos bloqueados, en orden de llegada (para la GUI). */
    public PCB[] getBloqueados() {
        mutex.acquireUninterruptibly();
        try {
            PCB[] copia = new PCB[bloqueados.getTamano()];
            for (int i = 0; i < copia.length; i++) {
                copia[i] = bloqueados.obtener(i);
            }
            return copia;
        } finally {
            mutex.release();
        }
    }

    public int cantidadBloqueados() {
        mutex.acquireUninterruptibly();
        try {
            return bloqueados.getTamano();
        } finally {
            mutex.release();
        }
    }

    public int getValor() {
        mutex.acquireUninterruptibly();
        try {
            return valor;
        } finally {
            mutex.release();
        }
    }

    public String getNombre() { return nombre; }
    public TipoSemaforo getTipo() { return tipo; }

    @Override
    public String toString() {
        return nombre + "=" + getValor();
    }
}
