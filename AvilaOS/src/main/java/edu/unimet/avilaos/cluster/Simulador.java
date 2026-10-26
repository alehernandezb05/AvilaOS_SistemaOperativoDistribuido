package edu.unimet.avilaos.cluster;

import edu.unimet.avilaos.config.ConfigProceso;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.Configuracion.ConfigComputador;
import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.Reloj;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.Log;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import edu.unimet.avilaos.so.politicas.EDF;
import edu.unimet.avilaos.so.politicas.FCFS;
import edu.unimet.avilaos.so.politicas.PrioridadesApropiativas;
import edu.unimet.avilaos.so.politicas.RoundRobin;

/**
 * Arma el sistema distribuido: el reloj global y los computadores del cluster.
 * Es el unico punto de entrada que conoce la GUI.
 */
public class Simulador {

    public static final int MIN_COMPUTADORES = 2;

    private final Reloj reloj;
    private final Computador[] computadores;
    private final Log log = new Log();
    private final int latenciaRed;       // ciclos que cuesta cada acceso a un buffer remoto
    // Datos con los que se creo cada proceso, para guardarlos como carga inicial
    private final Lista<ConfigProceso> procesosCreados = new Lista<>();
    private volatile int quantum;
    private boolean iniciado = false;

    /** Crea el cluster con los parametros de una configuracion ya validada y su carga inicial de procesos. */
    public Simulador(Configuracion config) {
        this(config.getDuracionCicloMs(), config.getQuantum(), config.getLatenciaRed(),
                config.getTamanosRam(), config.getPoliticas());
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
        if (tamanosRam.length < MIN_COMPUTADORES) {
            throw new IllegalArgumentException("Se necesitan al menos " + MIN_COMPUTADORES + " computadores.");
        }
        if (politicas.length != tamanosRam.length) {
            throw new IllegalArgumentException("Debe haber una politica por computador.");
        }
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor a 0.");
        }
        if (latenciaRed < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa.");
        }
        this.quantum = quantum;
        this.latenciaRed = latenciaRed;
        this.reloj = new Reloj(duracionCicloMs);
        this.computadores = new Computador[tamanosRam.length];
        // Los IDs de los computadores empiezan en 1; todos comparten el mismo log global
        for (int i = 0; i < tamanosRam.length; i++) {
            computadores[i] = new Computador(i + 1, tamanosRam[i], crearEstrategia(politicas[i], quantum), reloj);
            computadores[i].setLog(log);
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
        if (!iniciado) {
            reloj.iniciar();
            iniciado = true;
        } else {
            reloj.reanudar();
        }
    }

    public void pausar() {
        reloj.pausar();
    }

    public synchronized void detener() {
        reloj.detener();
    }

    public void agregarProceso(PCB proceso) {
        getComputador(proceso.getIdComputador()).agregarProceso(proceso);
        synchronized (procesosCreados) {
            procesosCreados.agregar(ConfigProceso.desde(proceso));
        }
        log.registrar(getCicloActual(), "Proceso " + proceso + " creado en el computador "
                + proceso.getIdComputador() + " (" + proceso.getTipo() + ")");
    }

    public void cambiarPolitica(int idComputador, PoliticaPlanificacion politica) {
        getComputador(idComputador).cambiarPolitica(crearEstrategia(politica, quantum));
    }

    /** Cambia el quantum en caliente; afecta a los computadores que ya usan Round Robin. */
    public void setQuantum(int quantum) {
        if (quantum <= 0) {
            return;
        }
        this.quantum = quantum;
        for (Computador c : computadores) {
            if (c.getNucleo().getEstrategia() instanceof RoundRobin rr) {
                rr.setQuantum(quantum);
            }
        }
    }

    public void setDuracionCicloMs(int ms) {
        reloj.setDuracionCicloMs(ms);
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
     * y los procesos creados hasta ahora como carga inicial, para guardarlos.
     */
    public Configuracion getConfiguracion() {
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
        return new Configuracion(getDuracionCicloMs(), quantum, latenciaRed, datos, procesos);
    }

    public int getLatenciaRed() { return latenciaRed; }
    public Log getLog() { return log; }
    public int getCantidadComputadores() { return computadores.length; }
    public int getQuantum() { return quantum; }
    public long getCicloActual() { return reloj.getCicloActual(); }
    public int getDuracionCicloMs() { return reloj.getDuracionCicloMs(); }
    public boolean estaCorriendo() { return iniciado && !reloj.estaPausado(); }
}
