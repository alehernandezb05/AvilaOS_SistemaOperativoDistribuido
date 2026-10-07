package edu.unimet.avilaos.so;

public class PCB {

    // Contador compartido por todos los computadores: garantiza IDs unicos en todo el sistema
    private static int contadorIds = 0;

    private static synchronized int siguienteId() {
        contadorIds++;
        return contadorIds;
    }

    private final int id;
    private final String nombre;
    private final int idComputador;
    private final TipoProceso tipo;
    private final int prioridad;
    private final int memoria;
    private final int totalInstrucciones;

    private EstadoProceso estado;
    private MotivoBloqueo motivoBloqueo;
    private int deadline;          // ciclos que le quedan antes de vencer
    private int tiempoRestante;    // instrucciones que le faltan por ejecutar
    private int programCounter;
    private int mar;

    // Datos para las metricas
    private long cicloCreacion;
    private long cicloFin;
    private int ciclosBloqueado;

    public PCB(String nombre, int idComputador, TipoProceso tipo, int prioridad,
               int memoria, int totalInstrucciones, int deadline, long cicloCreacion) {
        this.id = siguienteId();
        this.nombre = nombre;
        this.idComputador = idComputador;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.memoria = memoria;
        this.totalInstrucciones = totalInstrucciones;
        this.deadline = deadline;
        this.tiempoRestante = totalInstrucciones;
        this.estado = EstadoProceso.NUEVO;
        this.motivoBloqueo = null;
        this.programCounter = 0;
        this.mar = 0;
        this.cicloCreacion = cicloCreacion;
        this.cicloFin = -1;
        this.ciclosBloqueado = 0;
    }

    // Ejecuta una instruccion: PC y MAR suben una unidad y baja el tiempo restante
    public void ejecutarInstruccion() {
        programCounter++;
        mar++;
        tiempoRestante--;
    }

    public void decrementarDeadline() {
        if (deadline > 0) {
            deadline--;
        }
    }

    public boolean deadlineVencido() {
        return deadline <= 0;
    }

    public boolean haTerminado() {
        return tiempoRestante <= 0;
    }

    public void sumarCicloBloqueado() {
        ciclosBloqueado++;
    }

    // Getters
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public int getIdComputador() { return idComputador; }
    public TipoProceso getTipo() { return tipo; }
    public int getPrioridad() { return prioridad; }
    public int getMemoria() { return memoria; }
    public int getTotalInstrucciones() { return totalInstrucciones; }
    public EstadoProceso getEstado() { return estado; }
    public MotivoBloqueo getMotivoBloqueo() { return motivoBloqueo; }
    public int getDeadline() { return deadline; }
    public int getTiempoRestante() { return tiempoRestante; }
    public int getProgramCounter() { return programCounter; }
    public int getMar() { return mar; }
    public long getCicloCreacion() { return cicloCreacion; }
    public long getCicloFin() { return cicloFin; }
    public int getCiclosBloqueado() { return ciclosBloqueado; }

    // Setters (solo de lo que cambia durante la simulacion)
    public void setEstado(EstadoProceso estado) { this.estado = estado; }
    public void setMotivoBloqueo(MotivoBloqueo motivo) { this.motivoBloqueo = motivo; }
    public void setCicloFin(long cicloFin) { this.cicloFin = cicloFin; }

    @Override
    public String toString() {
        return "P" + id + " (" + nombre + ")";
    }
}