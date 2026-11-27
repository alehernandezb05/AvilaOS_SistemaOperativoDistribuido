package edu.unimet.avilaos.so;

import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;

public class PCB {

    private static final int PASOS_PROTOCOLO = 5;

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
    private final OperacionArchivo operacionArchivo;
    private boolean instruccionArchivoEjecutada;
    private DiskRequest solicitudDisco;
    private DiskRequest solicitudSwap;
    private DiskRequest solicitudReactivacion;
    private boolean enSwap;
    private boolean eventoDuranteSuspension;
    private int ciclosBloqueoActual;
    private String avisoAdmision;

    private EstadoProceso estado;
    private MotivoBloqueo motivoBloqueo;
    private MotivoTerminacion motivoTerminacion;
    private int deadline;
    private int tiempoRestante;
    private int programCounter;
    private int mar;

    private long cicloCreacion;
    private long cicloPrimeraEjecucion;
    private long cicloFin;
    private int ciclosBloqueado;
    private int ciclosRestantesBloqueo;
    private int ciclosBloqueadoSemaforo;

    private int ciclosParaES;
    private int duracionES;
    private int contadorCiclosES;

    private String idBuffer;
    private int ciclosPorElemento;
    private int elementosTotales;
    private int elementosProcesados;

    private PasoProtocolo paso;
    private int ciclosTrabajados;
    private boolean conteoAdquirido;
    private boolean mutexAdquirido;
    private boolean accionHecha;
    private boolean accesoPagado;
    private Semaforo semaforoEsperado;

    public PCB(String nombre, int idComputador, TipoProceso tipo, int prioridad,
               int memoria, int totalInstrucciones, int deadline, long cicloCreacion) {
        this(nombre, idComputador, tipo, prioridad, memoria, totalInstrucciones, deadline, cicloCreacion, null);
    }

    public PCB(String nombre, int idComputador, TipoProceso tipo, int prioridad,
               int memoria, int totalInstrucciones, int deadline, long cicloCreacion, OperacionArchivo operacionArchivo) {
        if (operacionArchivo != null && tipo != TipoProceso.IO_BOUND) {
            throw new IllegalArgumentException("Una operación de archivo requiere un proceso de E/S.");
        }
        this.id = siguienteId();
        this.nombre = nombre;
        this.idComputador = idComputador;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.memoria = memoria;
        this.totalInstrucciones = totalInstrucciones;
        this.operacionArchivo = operacionArchivo;
        this.deadline = deadline;
        this.tiempoRestante = totalInstrucciones;
        this.estado = EstadoProceso.NUEVO;
        this.motivoBloqueo = null;
        this.motivoTerminacion = null;
        this.programCounter = 0;
        this.mar = 0;
        this.cicloCreacion = cicloCreacion;
        this.cicloPrimeraEjecucion = -1;
        this.cicloFin = -1;
        this.ciclosBloqueado = 0;
        this.ciclosRestantesBloqueo = 0;

        this.ciclosParaES = 0;
        this.duracionES = 0;
        this.contadorCiclosES = 0;
        this.idBuffer = null;
        this.ciclosPorElemento = 0;
        this.elementosTotales = 0;
        this.elementosProcesados = 0;

        this.paso = tipo == TipoProceso.CONSUMIDOR ? PasoProtocolo.ESPERAR_CONTEO : PasoProtocolo.TRABAJO;
        this.ciclosTrabajados = 0;
        this.conteoAdquirido = false;
        this.mutexAdquirido = false;
        this.accionHecha = false;
        this.accesoPagado = false;
        this.semaforoEsperado = null;
    }

    public boolean esProductorConsumidor() {
        return tipo == TipoProceso.PRODUCTOR || tipo == TipoProceso.CONSUMIDOR;
    }

    public void ejecutarInstruccion() {
        if (debeEnviarSolicitudDisco()) return;
        programCounter++;
        mar++;
        if (esProductorConsumidor()) {
            return;
        }
        tiempoRestante--;
        if (ejecutaraOperacionArchivo()) {
            instruccionArchivoEjecutada = true;
        } else if (tipo == TipoProceso.IO_BOUND && operacionArchivo == null) {
            contadorCiclosES++;
        }
    }

    boolean ejecutaraOperacionArchivo() { return operacionArchivo != null && !instruccionArchivoEjecutada; }
    boolean debeEnviarSolicitudDisco() { return instruccionArchivoEjecutada && solicitudDisco == null; }
    void registrarSolicitudDisco(DiskRequest solicitud) { solicitudDisco = solicitud; }
    DiskRequest getSolicitudDisco() { return solicitudDisco; }

    // Intercambio: la escritura al swap esta en curso mientras solicitudSwap no es null.
    // La RAM se libera solo cuando esa escritura termina (enSwap pasa a true).
    void iniciarSuspension(DiskRequest escritura) {
        solicitudSwap = escritura;
        eventoDuranteSuspension = false;
    }

    void completarSuspension() {
        solicitudSwap = null;
        enSwap = true;
    }

    /** El evento que esperaba ocurrio mientras se escribia al swap: termina en Listo/Suspendido. */
    void registrarEventoDuranteSuspension() { eventoDuranteSuspension = true; }

    // Reactivacion: la RAM ya esta reservada y se lee el swap; enSwap sigue en true hasta terminar.
    void iniciarReactivacion(DiskRequest lectura) { solicitudReactivacion = lectura; }

    void completarReactivacion() {
        solicitudReactivacion = null;
        enSwap = false;
    }

    boolean reactivacionEnCurso() { return solicitudReactivacion != null; }
    DiskRequest getSolicitudReactivacion() { return solicitudReactivacion; }

    boolean suspensionEnCurso() { return solicitudSwap != null; }
    DiskRequest getSolicitudSwap() { return solicitudSwap; }
    boolean isEventoDuranteSuspension() { return eventoDuranteSuspension; }
    public boolean isEnSwap() { return enSwap; }

    String getAvisoAdmision() { return avisoAdmision; }
    void setAvisoAdmision(String aviso) { avisoAdmision = aviso; }

    void iniciarBloqueo() { ciclosBloqueoActual = 0; }
    /** Ciclos seguidos en el bloqueo actual; criterio para elegir la victima de suspension. */
    public int getCiclosBloqueoActual() { return ciclosBloqueoActual; }

    public void decrementarDeadline() {
        if (deadline > 0) {
            deadline--;
        }
    }

    public boolean deadlineVencido() {
        return deadline <= 0;
    }

    public boolean haTerminado() {
        if (esProductorConsumidor()) {
            boolean completo = elementosProcesados >= elementosTotales;
            if (tipo == TipoProceso.PRODUCTOR) {
                return completo && paso == PasoProtocolo.TRABAJO;
            }
            return completo && paso == PasoProtocolo.ESPERAR_CONTEO;
        }
        return tiempoRestante <= 0
                && (operacionArchivo == null || solicitudDisco != null && solicitudDisco.getResult() != null);
    }

    public boolean debePedirES() {
        return tipo == TipoProceso.IO_BOUND
                && operacionArchivo == null
                && ciclosParaES > 0
                && duracionES > 0
                && contadorCiclosES >= ciclosParaES
                && tiempoRestante > 0;
    }

    public void iniciarES() {
        contadorCiclosES = 0;
        ciclosRestantesBloqueo = duracionES;
    }

    public void iniciarEsperaRed(int ciclos) {
        ciclosRestantesBloqueo = ciclos;
    }

    public boolean avanzarEspera() {
        if (ciclosRestantesBloqueo > 0) {
            ciclosRestantesBloqueo--;
        }
        return ciclosRestantesBloqueo == 0;
    }

    public boolean avanzarTrabajo() {
        ciclosTrabajados++;
        if (ciclosTrabajados >= ciclosPorElemento) {
            ciclosTrabajados = 0;
            return true;
        }
        return false;
    }

    public void siguientePaso() {
        switch (paso) {
            case TRABAJO -> paso = PasoProtocolo.ESPERAR_CONTEO;
            case ESPERAR_CONTEO -> paso = PasoProtocolo.ESPERAR_MUTEX;
            case ESPERAR_MUTEX -> paso = PasoProtocolo.ACCEDER_BUFFER;
            case ACCEDER_BUFFER -> paso = PasoProtocolo.LIBERAR_MUTEX;
            case LIBERAR_MUTEX -> paso = PasoProtocolo.LIBERAR_CONTEO;
            case LIBERAR_CONTEO -> paso = PasoProtocolo.TRABAJO;
        }
    }

    public void confirmarAdquisicion() {
        if (paso == PasoProtocolo.ESPERAR_CONTEO) {
            conteoAdquirido = true;
        } else if (paso == PasoProtocolo.ESPERAR_MUTEX) {
            mutexAdquirido = true;
        }
    }

    public void confirmarEsperaCumplida() {
        if (paso == PasoProtocolo.ESPERAR_MUTEX) {
            conteoAdquirido = true;
        } else if (paso == PasoProtocolo.ACCEDER_BUFFER) {
            mutexAdquirido = true;
        }
        semaforoEsperado = null;
    }

    public void registrarAccion() {
        accionHecha = true;
        elementosProcesados++;
        recalcularRestante();
    }

    public void finalizarElemento() {
        conteoAdquirido = false;
        mutexAdquirido = false;
        accionHecha = false;
        accesoPagado = false;
        ciclosTrabajados = 0;
        recalcularRestante();
    }

    private void recalcularRestante() {
        if (esProductorConsumidor()) {
            int faltan = Math.max(0, elementosTotales - elementosProcesados);
            tiempoRestante = faltan * (ciclosPorElemento + PASOS_PROTOCOLO);
        }
    }

    public void terminar(MotivoTerminacion motivo, long ciclo) {
        this.motivoTerminacion = motivo;
        this.estado = EstadoProceso.TERMINADO;
        this.motivoBloqueo = null;
        this.ciclosRestantesBloqueo = 0;
        this.semaforoEsperado = null;
        this.cicloFin = ciclo;
    }

    public boolean cumplioDeadline() {
        return motivoTerminacion == MotivoTerminacion.COMPLETADO;
    }

    public void sumarCicloBloqueado() {
        ciclosBloqueado++;
        ciclosBloqueoActual++;
    }

    public void sumarCicloBloqueadoSemaforo() {
        ciclosBloqueadoSemaforo++;
    }

    public void registrarPrimeraEjecucion(long ciclo) {
        if (cicloPrimeraEjecucion < 0) {
            cicloPrimeraEjecucion = ciclo;
        }
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public int getIdComputador() { return idComputador; }
    public TipoProceso getTipo() { return tipo; }
    public int getPrioridad() { return prioridad; }
    public int getMemoria() { return memoria; }
    public int getTotalInstrucciones() { return totalInstrucciones; }
    public OperacionArchivo getOperacionArchivo() { return operacionArchivo; }
    public EstadoProceso getEstado() { return estado; }
    public MotivoBloqueo getMotivoBloqueo() { return motivoBloqueo; }
    public MotivoTerminacion getMotivoTerminacion() { return motivoTerminacion; }
    public int getDeadline() { return deadline; }
    public int getTiempoRestante() { return tiempoRestante; }
    public int getProgramCounter() { return programCounter; }
    public int getMar() { return mar; }
    public long getCicloCreacion() { return cicloCreacion; }
    public long getCicloPrimeraEjecucion() { return cicloPrimeraEjecucion; }
    public long getCicloFin() { return cicloFin; }
    public int getCiclosBloqueado() { return ciclosBloqueado; }
    public int getCiclosBloqueadoSemaforo() { return ciclosBloqueadoSemaforo; }
    public int getCiclosRestantesBloqueo() { return ciclosRestantesBloqueo; }

    public PasoProtocolo getPaso() { return paso; }
    public Semaforo getSemaforoEsperado() { return semaforoEsperado; }
    public void setSemaforoEsperado(Semaforo semaforo) { this.semaforoEsperado = semaforo; }
    public boolean isConteoAdquirido() { return conteoAdquirido; }
    public boolean isMutexAdquirido() { return mutexAdquirido; }
    public void setMutexAdquirido(boolean valor) { this.mutexAdquirido = valor; }
    public boolean isAccionHecha() { return accionHecha; }
    public boolean isAccesoPagado() { return accesoPagado; }
    public void setAccesoPagado(boolean valor) { this.accesoPagado = valor; }

    public int getCiclosParaES() { return ciclosParaES; }
    public void setCiclosParaES(int ciclosParaES) {
        this.ciclosParaES = noNegativo(ciclosParaES, "ciclosParaES");
    }

    public int getDuracionES() { return duracionES; }
    public void setDuracionES(int duracionES) {
        this.duracionES = noNegativo(duracionES, "duracionES");
    }

    public int getContadorCiclosES() { return contadorCiclosES; }
    public void setContadorCiclosES(int contadorCiclosES) {
        this.contadorCiclosES = noNegativo(contadorCiclosES, "contadorCiclosES");
    }

    public String getIdBuffer() { return idBuffer; }
    public void setIdBuffer(String idBuffer) {
        if (idBuffer != null && idBuffer.isBlank()) {
            throw new IllegalArgumentException("idBuffer no puede estar vacio.");
        }
        this.idBuffer = idBuffer;
    }

    public int getCiclosPorElemento() { return ciclosPorElemento; }
    public void setCiclosPorElemento(int ciclosPorElemento) {
        if (ciclosPorElemento < 1) {
            throw new IllegalArgumentException("ciclosPorElemento debe ser al menos 1 (vale " + ciclosPorElemento + ").");
        }
        this.ciclosPorElemento = ciclosPorElemento;
        recalcularRestante();
    }

    public int getElementosTotales() { return elementosTotales; }
    public void setElementosTotales(int elementosTotales) {
        this.elementosTotales = noNegativo(elementosTotales, "elementosTotales");
        recalcularRestante();
    }

    public int getElementosProcesados() { return elementosProcesados; }
    public void setElementosProcesados(int elementosProcesados) {
        if (elementosProcesados < 0 || elementosProcesados > elementosTotales) {
            throw new IllegalArgumentException("elementosProcesados debe estar entre 0 y " + elementosTotales
                    + " (vale " + elementosProcesados + ").");
        }
        this.elementosProcesados = elementosProcesados;
        recalcularRestante();
    }

    private static int noNegativo(int valor, String campo) {
        if (valor < 0) {
            throw new IllegalArgumentException(campo + " no puede ser negativo (vale " + valor + ").");
        }
        return valor;
    }

    public void setEstado(EstadoProceso estado) { this.estado = estado; }
    public void setMotivoBloqueo(MotivoBloqueo motivo) { this.motivoBloqueo = motivo; }
    public void setCicloFin(long cicloFin) { this.cicloFin = cicloFin; }

    @Override
    public String toString() {
        return "P" + id + " (" + nombre + ")";
    }
}
