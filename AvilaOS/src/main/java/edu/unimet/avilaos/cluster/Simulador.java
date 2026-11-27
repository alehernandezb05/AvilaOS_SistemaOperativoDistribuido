package edu.unimet.avilaos.cluster;

import edu.unimet.avilaos.config.ConfigProceso;
import edu.unimet.avilaos.config.ConfigDisco;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.DiskPolicies;
import edu.unimet.avilaos.hardware.disco.DiskSnapshot;
import edu.unimet.avilaos.hardware.disco.DiskStorage.InitialFile;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.Configuracion.ConfigBuffer;
import edu.unimet.avilaos.config.Configuracion.ConfigComputador;
import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.Reloj;
import edu.unimet.avilaos.so.Buffer;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.Log;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import edu.unimet.avilaos.so.ServicioCluster;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SesionUsuario;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.politicas.EDF;
import edu.unimet.avilaos.so.politicas.FCFS;
import edu.unimet.avilaos.so.politicas.PrioridadesApropiativas;
import edu.unimet.avilaos.so.politicas.RoundRobin;
import java.util.function.Supplier;

/**
 * Arma el sistema distribuido: el reloj global y los computadores del cluster.
 * Es el unico punto de entrada que conoce la GUI.
 */
public class Simulador implements ServicioCluster {

    public static final int MIN_COMPUTADORES = 1;

    private final Reloj reloj;
    private final Computador[] computadores;
    private final SistemaArchivos sistemaArchivos;
    private final boolean procesosCargados;
    private final Log log = new Log();
    private volatile SesionUsuario sesion = SesionUsuario.administrador();
    private final int latenciaRed;       // ciclos que cuesta cada acceso a un buffer remoto
    // Datos con los que se creo cada proceso, para guardarlos como carga inicial
    private final Lista<ConfigProceso> procesosCreados = new Lista<>();
    private final Lista<Buffer> buffers = new Lista<>();
    private volatile int quantum;
    private boolean iniciado = false;

    /**
     * Crea el cluster con los parametros de una configuracion ya validada y su carga
     * inicial. Primero los buffers (reciben B1, B2... en el mismo orden en que se
     * guardaron) y despues los procesos que los usan.
     */
    public Simulador(Configuracion config) {
        this(config, null);
    }

    public Simulador(Configuracion config, SistemaArchivos sistemaArchivos) {
        this(config.getDuracionCicloMs(), config.getQuantum(), config.getLatenciaRed(),
                config.getTamanosRam(), config.getPoliticas(), config.getDisco(), sistemaArchivos,
                config.tieneCargaProcesos());
        for (ConfigBuffer buffer : config.getBuffers()) {
            crearBuffer(buffer.getCapacidad(), buffer.getAnfitrion());
        }
        for (ConfigProceso proceso : config.getProcesos()) {
            agregarProceso(proceso.crearPCB(0));
        }
    }

    public Simulador(int duracionCicloMs, int quantum, int[] tamanosRam, PoliticaPlanificacion[] politicas) {
        this(duracionCicloMs, quantum, Configuracion.porDefecto().getLatenciaRed(), tamanosRam, politicas);
    }

    /**
     * @param tamanosRam RAM de cada computador; su largo es la cantidad de computadores
     * @param politicas  politica inicial de cada computador, mismo largo que tamanosRam
     */
    public Simulador(int duracionCicloMs, int quantum, int latenciaRed, int[] tamanosRam,
                     PoliticaPlanificacion[] politicas) {
        this(duracionCicloMs, quantum, latenciaRed, tamanosRam, politicas, new ConfigDisco());
    }

    public Simulador(int duracionCicloMs, int quantum, int latenciaRed, int[] tamanosRam,
                     PoliticaPlanificacion[] politicas, ConfigDisco configDisco) {
        this(duracionCicloMs, quantum, latenciaRed, tamanosRam, politicas, configDisco, null, false);
    }

    private Simulador(int duracionCicloMs, int quantum, int latenciaRed, int[] tamanosRam,
                      PoliticaPlanificacion[] politicas, ConfigDisco configDisco, SistemaArchivos sistemaArchivos,
                      boolean procesosCargados) {
        if (tamanosRam.length != MIN_COMPUTADORES) {
            throw new IllegalArgumentException("El Proyecto 2 requiere exactamente un computador.");
        }
        if (politicas.length != tamanosRam.length) {
            throw new IllegalArgumentException("Debe haber una politica por computador.");
        }
        if (quantum < Configuracion.MIN_QUANTUM || quantum > Configuracion.MAX_QUANTUM) {
            throw new IllegalArgumentException("El quantum debe estar entre 0 y " + Configuracion.MAX_QUANTUM + ".");
        }
        if (latenciaRed < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa.");
        }
        this.quantum = quantum;
        this.latenciaRed = latenciaRed;
        this.sistemaArchivos = sistemaArchivos == null ? null : sistemaArchivos.copia();
        this.procesosCargados = procesosCargados;
        this.reloj = new Reloj(duracionCicloMs);
        this.computadores = new Computador[tamanosRam.length];
        int bloques = sistemaArchivos == null ? configDisco.getTotalBlocks() : sistemaArchivos.bloquesDisco();
        int cabezal = sistemaArchivos == null ? configDisco.getInitialHead() : sistemaArchivos.cabezalInicial();
        InitialFile[] archivos = this.sistemaArchivos == null ? new InitialFile[0] : this.sistemaArchivos.archivosIniciales();
        // El JSON de procesos aporta swap y velocidad; la vista previa solo usa la geometría cargada.
        boolean vistaPreviaArchivos = sistemaArchivos != null && !procesosCargados;
        DiskScheduler disco = new DiskScheduler(bloques, cabezal, vistaPreviaArchivos ? 0 : configDisco.getSwapSizeBlocks(),
                vistaPreviaArchivos ? 1 : configDisco.getHeadSpeed(), DiskPolicies.create(configDisco.getInitialPolicy()), archivos);
        // Los IDs de los computadores empiezan en 1; todos comparten el mismo log global
        for (int i = 0; i < tamanosRam.length; i++) {
            computadores[i] = new Computador(i + 1, tamanosRam[i], crearEstrategia(politicas[i], quantum), reloj,
                    disco, this.sistemaArchivos);
            computadores[i].setLog(log);
            computadores[i].setServicio(this);
        }
    }

    /** Crea la estrategia que corresponde a cada valor del enum. */
    public static EstrategiaPlanificacion crearEstrategia(PoliticaPlanificacion politica, int quantum) {
        return switch (politica) {
            case FCFS -> new FCFS();
            case ROUND_ROBIN -> new RoundRobin(quantum);
            case EDF -> new EDF();
            case PRIORIDADES_APROPIATIVAS -> new PrioridadesApropiativas();
        };
    }

    /** Arranca el reloj la primera vez; despues solo lo reanuda. */
    public synchronized void iniciar() {
        String impedimento = impedimentoInicio();
        if (impedimento != null) throw new IllegalStateException(impedimento);
        if (!iniciado) {
            reloj.iniciar();
            iniciado = true;
        } else {
            reloj.reanudar();
        }
    }

    public String impedimentoInicio() {
        if (sistemaArchivos == null && !procesosCargados) return "Carga ambos JSON para iniciar.";
        if (sistemaArchivos == null) return "Falta el JSON del sistema de archivos.";
        if (!procesosCargados) return "Falta el JSON de procesos.";
        return null;
    }

    public void pausar() {
        reloj.pausar();
    }

    public synchronized void detener() {
        reloj.detener();
    }

    /** Copia valores del modelo entre ciclos, sin trabajo Swing bajo el semaforo. */
    public <T> T intentarObservar(Supplier<T> observacion) {
        return reloj.intentarObservar(observacion);
    }

    /** Operacion breve del modelo; no llamar otra operacion que tome el reloj desde aqui. */
    public <T> T observar(Supplier<T> observacion) {
        return reloj.entreCiclos(observacion);
    }

    public SistemaArchivos copiaSistemaArchivos() {
        // El cabezal también es parte del estado del disco que se conserva al guardar o recargar procesos.
        return observar(() -> sistemaArchivos == null ? null : sistemaArchivos.copia(getDisco().getCurrentHead()));
    }

    public void agregarProceso(PCB proceso) {
        observar(() -> { agregarProcesoInterno(proceso); return null; });
    }

    private void agregarProcesoInterno(PCB proceso) {
        getComputador(proceso.getIdComputador()).agregarProceso(proceso);
        synchronized (procesosCreados) {
            procesosCreados.agregar(ConfigProceso.desde(proceso));
        }
        log.registrar(getCicloActual(), "Proceso " + proceso + " creado en el computador "
                + proceso.getIdComputador() + " (" + proceso.getTipo() + ")");
    }

    /**
     * Proceso pedido desde la interfaz: se valida con el modo de uso actual antes de crearlo.
     * Un rechazo se registra, no crea el proceso y la simulacion sigue.
     */
    public void solicitarProceso(PCB proceso) {
        observar(() -> { solicitarProcesoInterno(proceso); return null; });
    }

    private void solicitarProcesoInterno(PCB proceso) {
        SesionUsuario actual = sesion;
        OperacionArchivo operacion = proceso.getOperacionArchivo();
        try {
            if (operacion == null) {
                exigirAdministrador("crear procesos que no son de archivos");
            } else {
                if (!actual.solicitante().equals(operacion.solicitante())) {
                    throw new IllegalArgumentException("la operación debe pedirla " + actual.solicitante() + ".");
                }
                if (sistemaArchivos == null) throw new IllegalArgumentException("falta cargar el sistema de archivos.");
                sistemaArchivos.comprobarPermiso(operacion);
            }
        } catch (IllegalArgumentException e) {
            String que = operacion == null ? "el proceso " + proceso.getNombre()
                    : operacion.tipo() + " " + operacion.ruta();
            log.registrar(getCicloActual(), "Se rechaza " + que + " en modo " + actual + ": " + e.getMessage());
            throw e;
        }
        agregarProcesoInterno(proceso);
    }

    public SesionUsuario getSesion() { return sesion; }

    public void setSesion(SesionUsuario nueva) {
        observar(() -> { setSesionInterno(nueva); return null; });
    }

    private void setSesionInterno(SesionUsuario nueva) {
        if (nueva == null) throw new IllegalArgumentException("Falta el modo de uso.");
        if (nueva.equals(sesion)) return;
        sesion = nueva;
        log.registrar(getCicloActual(), "Cambia el modo de uso a " + nueva);
        // Un usuario no puede soltar el disco: si quedó retenido, se suelta para que sus procesos avancen.
        DiskScheduler disco = getDisco();
        if (!nueva.esAdministrador() && disco != null && disco.isHeld()) {
            disco.setHeld(false);
            log.registrar(getCicloActual(), "Se suelta el disco al salir del modo administrador");
        }
    }

    /** Las politicas y el quantum los gestiona el administrador; tambien se valida aqui, no solo en la interfaz. */
    private void exigirAdministrador(String accion) {
        if (!sesion.esAdministrador()) {
            throw new IllegalArgumentException("En modo usuario no se puede " + accion + ".");
        }
    }

    public void cambiarPolitica(int idComputador, PoliticaPlanificacion politica) {
        observar(() -> { cambiarPoliticaInterno(idComputador, politica); return null; });
    }

    private void cambiarPoliticaInterno(int idComputador, PoliticaPlanificacion politica) {
        exigirAdministrador("cambiar la política de CPU");
        getComputador(idComputador).cambiarPolitica(crearEstrategia(politica, quantum));
    }

    /** Cambia el quantum en caliente; afecta a los computadores que ya usan Round Robin. */
    public void setQuantum(int quantum) {
        observar(() -> { setQuantumInterno(quantum); return null; });
    }

    private void setQuantumInterno(int quantum) {
        exigirAdministrador("cambiar el quantum");
        if (quantum < Configuracion.MIN_QUANTUM || quantum > Configuracion.MAX_QUANTUM) {
            throw new IllegalArgumentException("El quantum debe estar entre 0 y " + Configuracion.MAX_QUANTUM + ".");
        }
        if (quantum == 0 && computadores[0].getNucleo().getEstrategia().getPolitica()
                == PoliticaPlanificacion.ROUND_ROBIN) {
            throw new IllegalArgumentException("Round Robin necesita un quantum mayor a 0.");
        }
        this.quantum = quantum;
        for (Computador c : computadores) {
            if (c.getNucleo().getEstrategia() instanceof RoundRobin rr) {
                rr.setQuantum(quantum);
            }
        }
    }

    public void setDuracionCicloMs(int ms) {
        observar(() -> { reloj.setDuracionCicloMs(ms); return null; });
    }

    /** Devuelve el computador con ese ID (de 1 a la cantidad de computadores). */
    public Computador getComputador(int id) {
        if (id < 1 || id > computadores.length) {
            throw new IllegalArgumentException("No existe el computador " + id + ".");
        }
        return computadores[id - 1];
    }

    /**
     * Parametros actuales del cluster (con la politica que tiene ahora cada computador)
     * y los buffers y procesos creados hasta ahora como carga inicial, para guardarlos.
     */
    public Configuracion getConfiguracion() {
        return observar(this::copiarConfiguracion);
    }

    private Configuracion copiarConfiguracion() {
        Buffer[] existentes = getBuffers();
        ConfigBuffer[] datosBuffers = new ConfigBuffer[existentes.length];
        for (int i = 0; i < existentes.length; i++) {
            datosBuffers[i] = new ConfigBuffer(existentes[i].getCapacidad(), existentes[i].getIdAnfitrion());
        }
        ConfigComputador[] datos = new ConfigComputador[computadores.length];
        for (int i = 0; i < computadores.length; i++) {
            Computador c = computadores[i];
            datos[i] = new ConfigComputador(c.getRam().getTotal(), c.getNucleo().getEstrategia().getPolitica());
        }
        ConfigProceso[] procesos;
        synchronized (procesosCreados) {
            procesos = new ConfigProceso[procesosCreados.getTamano()];
            for (int i = 0; i < procesos.length; i++) {
                procesos[i] = procesosCreados.obtener(i);
            }
        }
        Configuracion config = new Configuracion(getDuracionCicloMs(), quantum, latenciaRed, datos, datosBuffers, procesos);
        DiskScheduler disco = getDisco();
        DiskSnapshot estado = disco.getSnapshot();
        config.setDisco(new ConfigDisco(disco.getTotalBlocks(), disco.getInitialHead(), disco.getSwapSizeBlocks(),
                estado.speed(), estado.policy()));
        config.conservarCargaProcesos(procesosCargados);
        return config;
    }

    @Override
    public void desbloquear(PCB proceso, long ciclo) {
        getComputador(proceso.getIdComputador()).getNucleo().desbloquear(proceso, ciclo);
    }

    public int getLatenciaRed() { return latenciaRed; }

    /**
     * Crea un buffer en el computador anfitrion y reserva su RAM.
     * @throws IllegalArgumentException si la capacidad o el anfitrion no son validos,
     *         o si el anfitrion no tiene RAM libre suficiente (en ese caso no se crea nada)
     */
    public Buffer crearBuffer(int capacidad, int idAnfitrion) {
        Computador anfitrion = getComputador(idAnfitrion);
        if (capacidad < Buffer.MIN_CAPACIDAD || capacidad > Buffer.MAX_CAPACIDAD) {
            throw new IllegalArgumentException("La capacidad del buffer debe estar entre " + Buffer.MIN_CAPACIDAD
                    + " y " + Buffer.MAX_CAPACIDAD + " (vale " + capacidad + ").");
        }
        int memoria = capacidad * Buffer.MEMORIA_POR_ELEMENTO;
        if (!anfitrion.getRam().reservar(memoria)) {
            throw new IllegalArgumentException("El computador " + idAnfitrion + " no tiene " + memoria
                    + " de RAM libre para el buffer (libre: " + anfitrion.getRam().getLibre() + ").");
        }
        Buffer buffer;
        synchronized (buffers) {
            // Se numeran desde B1 en cada simulacion, asi al recargar una configuracion
            // los buffers reciben los mismos IDs que usan sus productores y consumidores
            buffer = new Buffer("B" + (buffers.getTamano() + 1), capacidad, idAnfitrion);
            buffers.agregar(buffer);
        }
        log.registrar(getCicloActual(), "Buffer " + buffer.getId() + " creado en el computador " + idAnfitrion
                + " (capacidad " + capacidad + ", ocupa " + memoria + " de RAM)");
        return buffer;
    }

    /** Devuelve el buffer con ese ID, o null si no existe. */
    public Buffer getBuffer(String id) {
        synchronized (buffers) {
            for (int i = 0; i < buffers.getTamano(); i++) {
                if (buffers.obtener(i).getId().equals(id)) {
                    return buffers.obtener(i);
                }
            }
            return null;
        }
    }

    /** Copia de los buffers del cluster, en orden de creacion (para la GUI). */
    public Buffer[] getBuffers() {
        synchronized (buffers) {
            Buffer[] copia = new Buffer[buffers.getTamano()];
            for (int i = 0; i < copia.length; i++) {
                copia[i] = buffers.obtener(i);
            }
            return copia;
        }
    }

    /** IDs de los buffers, para elegir uno al crear productores y consumidores. */
    public String[] getIdsBuffers() {
        Buffer[] todos = getBuffers();
        String[] ids = new String[todos.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = todos[i].getId();
        }
        return ids;
    }

    public DiskScheduler getDisco() { return computadores[0].getDisco(); }
    public SistemaArchivos getSistemaArchivos() { return sistemaArchivos; }
    public void cambiarPoliticaDisco(DiskPolicy politica) {
        observar(() -> { cambiarPoliticaDiscoInterno(politica); return null; });
    }

    private void cambiarPoliticaDiscoInterno(DiskPolicy politica) {
        exigirAdministrador("cambiar la política de disco");
        getDisco().setPolicy(politica);
    }

    public void setVelocidadDisco(int velocidad) {
        observar(() -> { setVelocidadDiscoInterno(velocidad); return null; });
    }

    private void setVelocidadDiscoInterno(int velocidad) {
        exigirAdministrador("cambiar la velocidad del disco");
        getDisco().setSpeed(velocidad);
    }

    /** Retiene o suelta el disco; solo el administrador, igual que la política. */
    public void setDiscoRetenido(boolean retenido) {
        observar(() -> { setDiscoRetenidoInterno(retenido); return null; });
    }

    private void setDiscoRetenidoInterno(boolean retenido) {
        exigirAdministrador(retenido ? "retener el disco" : "soltar el disco");
        DiskScheduler disco = getDisco();
        if (disco.isHeld() == retenido) return;
        disco.setHeld(retenido);
        log.registrar(getCicloActual(), retenido ? "Se retiene el disco: las solicitudes esperan en la cola"
                : "Se suelta el disco con " + disco.getSnapshot().pendingCount() + " solicitudes en cola");
    }

    public Log getLog() { return log; }
    public int getCantidadComputadores() { return computadores.length; }
    public int getQuantum() { return quantum; }
    public long getCicloActual() { return reloj.getCicloActual(); }
    public int getDuracionCicloMs() { return reloj.getDuracionCicloMs(); }
    public boolean estaCorriendo() { return reloj.estaCorriendo(); }
}
