package edu.unimet.avilaos.config;

import edu.unimet.avilaos.so.Buffer;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import edu.unimet.avilaos.so.TipoProceso;

/**
 * Parametros con los que arranca una simulacion. Se guarda y se carga en JSON
 * (ver ArchivoConfiguracion). Usa arreglos en lugar de colecciones de Java.
 * Incluye la carga inicial de buffers y procesos: los buffers se crean en orden
 * y reciben B1, B2..., que son los IDs que usan sus productores y consumidores.
 */
public class Configuracion {

    // Rangos validos; la GUI puede usarlos para sus campos
    public static final int MIN_COMPUTADORES = 2;
    public static final int MAX_COMPUTADORES = 8;
    public static final int MIN_CICLO_MS = 50;
    public static final int MAX_CICLO_MS = 5000;
    public static final int MIN_QUANTUM = 1;
    public static final int MAX_QUANTUM = 50;
    public static final int MIN_LATENCIA = 0;
    public static final int MAX_LATENCIA = 100;
    public static final int MIN_RAM = 1;
    public static final int MAX_RAM = 100000;

    private int duracionCicloMs;
    private int quantum;
    private int latenciaRed;           // ciclos que cuesta cada acceso a un buffer remoto
    private ConfigComputador[] computadores;
    private ConfigBuffer[] buffers;    // carga inicial; puede faltar en archivos viejos
    private ConfigProceso[] procesos;  // carga inicial; puede faltar en archivos viejos

    /** Capacidad y computador anfitrion de un buffer de la carga inicial. */
    public static class ConfigBuffer {
        private int capacidad;
        private int anfitrion;

        public ConfigBuffer(int capacidad, int anfitrion) {
            this.capacidad = capacidad;
            this.anfitrion = anfitrion;
        }

        public int getCapacidad() { return capacidad; }
        public int getAnfitrion() { return anfitrion; }
    }

    /** RAM y politica inicial de un computador del cluster. */
    public static class ConfigComputador {
        private int ram;
        private PoliticaPlanificacion politica;

        public ConfigComputador(int ram, PoliticaPlanificacion politica) {
            this.ram = ram;
            this.politica = politica;
        }

        public int getRam() { return ram; }
        public PoliticaPlanificacion getPolitica() { return politica; }
    }

    public Configuracion(int duracionCicloMs, int quantum, int latenciaRed, ConfigComputador[] computadores) {
        this(duracionCicloMs, quantum, latenciaRed, computadores, new ConfigBuffer[0], new ConfigProceso[0]);
    }

    public Configuracion(int duracionCicloMs, int quantum, int latenciaRed, ConfigComputador[] computadores,
                         ConfigBuffer[] buffers, ConfigProceso[] procesos) {
        this.duracionCicloMs = duracionCicloMs;
        this.quantum = quantum;
        this.latenciaRed = latenciaRed;
        this.computadores = computadores;
        this.buffers = buffers;
        this.procesos = procesos;
    }

    /** Configuracion inicial cuando el usuario no carga ningun archivo. */
    public static Configuracion porDefecto() {
        return new Configuracion(500, 3, 2, new ConfigComputador[] {
            new ConfigComputador(256, PoliticaPlanificacion.FCFS),
            new ConfigComputador(256, PoliticaPlanificacion.FCFS)
        });
    }

    /** Revisa que todos los valores esten en rango; si no, explica cual falla. */
    public void validar() throws ConfiguracionInvalidaException {
        verificarRango("La duracion del ciclo (ms)", duracionCicloMs, MIN_CICLO_MS, MAX_CICLO_MS);
        verificarRango("El quantum", quantum, MIN_QUANTUM, MAX_QUANTUM);
        verificarRango("La latencia de red", latenciaRed, MIN_LATENCIA, MAX_LATENCIA);
        if (computadores == null) {
            throw new ConfiguracionInvalidaException("Faltan los computadores.");
        }
        verificarRango("La cantidad de computadores", computadores.length, MIN_COMPUTADORES, MAX_COMPUTADORES);
        for (int i = 0; i < computadores.length; i++) {
            ConfigComputador c = computadores[i];
            if (c == null) {
                throw new ConfiguracionInvalidaException("Faltan los datos del computador " + (i + 1) + ".");
            }
            verificarRango("La RAM del computador " + (i + 1), c.ram, MIN_RAM, MAX_RAM);
            // Gson deja null si el texto no coincide con ningun valor del enum
            if (c.politica == null) {
                throw new ConfiguracionInvalidaException("La politica del computador " + (i + 1) + " no es valida.");
            }
        }
        validarBuffers();
        ConfigProceso[] lista = getProcesos();
        int cantidadBuffers = getBuffers().length;
        for (int i = 0; i < lista.length; i++) {
            if (lista[i] == null) {
                throw new ConfiguracionInvalidaException("Faltan los datos del proceso " + (i + 1) + ".");
            }
            lista[i].validar(i + 1, computadores.length);
            // Productores y consumidores deben usar un buffer de esta misma configuracion
            if (lista[i].getTipo() == TipoProceso.PRODUCTOR || lista[i].getTipo() == TipoProceso.CONSUMIDOR) {
                int numero = numeroDeBuffer(lista[i].getIdBuffer());
                if (numero < 1 || numero > cantidadBuffers) {
                    throw new ConfiguracionInvalidaException("El proceso " + (i + 1) + " (" + lista[i].getNombre()
                            + ") usa el buffer " + lista[i].getIdBuffer() + ", que no esta en la configuracion.");
                }
            }
        }
    }

    // Revisa capacidad y anfitrion de cada buffer y que la RAM de los buffers quepa en cada computador
    private void validarBuffers() throws ConfiguracionInvalidaException {
        ConfigBuffer[] lista = getBuffers();
        int[] ramDeBuffers = new int[computadores.length];
        for (int i = 0; i < lista.length; i++) {
            ConfigBuffer b = lista[i];
            if (b == null) {
                throw new ConfiguracionInvalidaException("Faltan los datos del buffer B" + (i + 1) + ".");
            }
            verificarRango("La capacidad del buffer B" + (i + 1), b.capacidad, Buffer.MIN_CAPACIDAD, Buffer.MAX_CAPACIDAD);
            verificarRango("El anfitrion del buffer B" + (i + 1), b.anfitrion, 1, computadores.length);
            ramDeBuffers[b.anfitrion - 1] += b.capacidad * Buffer.MEMORIA_POR_ELEMENTO;
        }
        for (int i = 0; i < computadores.length; i++) {
            if (ramDeBuffers[i] > computadores[i].ram) {
                throw new ConfiguracionInvalidaException("Los buffers del computador " + (i + 1) + " ocupan "
                        + ramDeBuffers[i] + " de RAM y solo tiene " + computadores[i].ram + ".");
            }
        }
    }

    // "B3" -> 3; cualquier otro texto -> -1
    private static int numeroDeBuffer(String id) {
        if (id == null || id.length() < 2 || id.charAt(0) != 'B') {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void verificarRango(String campo, int valor, int min, int max)
            throws ConfiguracionInvalidaException {
        if (valor < min || valor > max) {
            throw new ConfiguracionInvalidaException(
                    campo + " debe estar entre " + min + " y " + max + " (vale " + valor + ").");
        }
    }

    public int getDuracionCicloMs() { return duracionCicloMs; }
    public int getQuantum() { return quantum; }
    public int getLatenciaRed() { return latenciaRed; }
    public int getCantidadComputadores() { return computadores.length; }
    public ConfigComputador getComputador(int indice) { return computadores[indice]; }

    /** Procesos de la carga inicial (vacio si el archivo no tiene). */
    public ConfigProceso[] getProcesos() {
        return procesos == null ? new ConfigProceso[0] : procesos;
    }

    /** Buffers de la carga inicial (vacio si el archivo no tiene), en el orden en que se crean. */
    public ConfigBuffer[] getBuffers() {
        return buffers == null ? new ConfigBuffer[0] : buffers;
    }

    /** RAM de cada computador, en el formato que recibe el Simulador. */
    public int[] getTamanosRam() {
        int[] rams = new int[computadores.length];
        for (int i = 0; i < rams.length; i++) {
            rams[i] = computadores[i].ram;
        }
        return rams;
    }

    /** Politica inicial de cada computador, en el formato que recibe el Simulador. */
    public PoliticaPlanificacion[] getPoliticas() {
        PoliticaPlanificacion[] politicas = new PoliticaPlanificacion[computadores.length];
        for (int i = 0; i < politicas.length; i++) {
            politicas[i] = computadores[i].politica;
        }
        return politicas;
    }
}
