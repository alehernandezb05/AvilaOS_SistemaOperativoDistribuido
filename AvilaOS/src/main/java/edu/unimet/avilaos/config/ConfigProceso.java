package edu.unimet.avilaos.config;

import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;

/**
 * Datos con los que se crea un proceso de la carga inicial. Guarda lo que el
 * usuario indico al crearlo, no su estado durante la simulacion.
 */
public class ConfigProceso {

    private String nombre;
    private int computador;
    private TipoProceso tipo;
    private int prioridad;
    private int memoria;
    private int instrucciones;
    private int deadline;

    // I/O bound
    private int ciclosParaES;
    private int duracionES;

    // Productor y consumidor
    private String idBuffer;
    private int ciclosPorElemento;
    private int elementosTotales;

    /** Toma los datos de un proceso recien creado (antes de que avance la simulacion). */
    public static ConfigProceso desde(PCB pcb) {
        ConfigProceso c = new ConfigProceso();
        c.nombre = pcb.getNombre();
        c.computador = pcb.getIdComputador();
        c.tipo = pcb.getTipo();
        c.prioridad = pcb.getPrioridad();
        c.memoria = pcb.getMemoria();
        c.instrucciones = pcb.getTotalInstrucciones();
        c.deadline = pcb.getDeadline();
        c.ciclosParaES = pcb.getCiclosParaES();
        c.duracionES = pcb.getDuracionES();
        c.idBuffer = pcb.getIdBuffer();
        c.ciclosPorElemento = pcb.getCiclosPorElemento();
        c.elementosTotales = pcb.getElementosTotales();
        return c;
    }

    /** Crea el PCB de este proceso, con un ID nuevo. */
    public PCB crearPCB(long cicloCreacion) {
        PCB pcb = new PCB(nombre, computador, tipo, prioridad, memoria, instrucciones, deadline, cicloCreacion);
        switch (tipo) {
            case IO_BOUND -> {
                pcb.setCiclosParaES(ciclosParaES);
                pcb.setDuracionES(duracionES);
            }
            case PRODUCTOR, CONSUMIDOR -> {
                pcb.setIdBuffer(idBuffer);
                pcb.setCiclosPorElemento(ciclosPorElemento);
                pcb.setElementosTotales(elementosTotales);
            }
            case CPU_BOUND -> { }
        }
        return pcb;
    }

    /** Revisa los valores; el numero de proceso se usa solo para el mensaje. */
    void validar(int numero, int cantidadComputadores) throws ConfiguracionInvalidaException {
        String quien = "El proceso " + numero;
        if (nombre == null || nombre.isBlank()) {
            throw new ConfiguracionInvalidaException(quien + " no tiene nombre.");
        }
        if (tipo == null) {
            throw new ConfiguracionInvalidaException(quien + " (" + nombre + ") no tiene un tipo valido.");
        }
        if (computador < 1 || computador > cantidadComputadores) {
            throw new ConfiguracionInvalidaException(quien + " (" + nombre + ") esta en el computador "
                    + computador + ", que no existe.");
        }
        if (prioridad < 1 || memoria < 1 || instrucciones < 1 || deadline < 1) {
            throw new ConfiguracionInvalidaException(quien + " (" + nombre
                    + ") debe tener prioridad, memoria, instrucciones y deadline mayores a 0.");
        }
        // Mismas reglas que los setters del PCB (sin crear uno, para no gastar IDs)
        boolean datosTipoValidos = switch (tipo) {
            case CPU_BOUND -> true;
            case IO_BOUND -> ciclosParaES >= 0 && duracionES >= 0;
            case PRODUCTOR, CONSUMIDOR -> idBuffer != null && !idBuffer.isBlank()
                    && ciclosPorElemento >= 1 && elementosTotales >= 1;
        };
        if (!datosTipoValidos) {
            throw new ConfiguracionInvalidaException(quien + " (" + nombre + ") tiene datos de "
                    + tipo + " invalidos.");
        }
    }

    public String getNombre() { return nombre; }
    public int getComputador() { return computador; }
    public TipoProceso getTipo() { return tipo; }
    public String getIdBuffer() { return idBuffer; }
}
