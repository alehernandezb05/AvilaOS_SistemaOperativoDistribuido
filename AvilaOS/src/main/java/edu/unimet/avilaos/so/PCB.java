package edu.unimet.avilaos.so;

public class PCB {

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
    private int deadline;          
    private int tiempoRestante;    
    private int programCounter;
    private int mar;

    private long cicloCreacion;
    private long cicloFin;
    private int ciclosBloqueado;

    private int ciclosParaES;      
    private int duracionES;        
    private int contadorCiclosES;   

    private String idBuffer;       
    private int elementosTotales;   
    private int elementosProcesados;

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

        this.ciclosParaES = 0;
        this.duracionES = 0;
        this.contadorCiclosES = 0;
        this.idBuffer = null;
        this.elementosTotales = 0;
        this.elementosProcesados = 0;
    }

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

    public int getCiclosParaES() { return ciclosParaES; }
    public void setCiclosParaES(int ciclosParaES) { this.ciclosParaES = ciclosParaES; }

    public int getDuracionES() { return duracionES; }
    public void setDuracionES(int duracionES) { this.duracionES = duracionES; }

    public int getContadorCiclosES() { return contadorCiclosES; }
    public void setContadorCiclosES(int contadorCiclosES) { this.contadorCiclosES = contadorCiclosES; }

    public String getIdBuffer() { return idBuffer; }
    public void setIdBuffer(String idBuffer) { this.idBuffer = idBuffer; }

    public int getElementosTotales() { return elementosTotales; }
    public void setElementosTotales(int elementosTotales) { this.elementosTotales = elementosTotales; }

    public int getElementosProcesados() { return elementosProcesados; }
    public void setElementosProcesados(int elementosProcesados) { this.elementosProcesados = elementosProcesados; }

    public void setEstado(EstadoProceso estado) { this.estado = estado; }
    public void setMotivoBloqueo(MotivoBloqueo motivo) { this.motivoBloqueo = motivo; }
    public void setCicloFin(long cicloFin) { this.cicloFin = cicloFin; }

    @Override
    public String toString() {
        return "P" + id + " (" + nombre + ")";
    }
}