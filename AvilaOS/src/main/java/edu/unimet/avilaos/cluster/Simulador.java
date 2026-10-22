package edu.unimet.avilaos.cluster;

import edu.unimet.avilaos.hardware.Reloj;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
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
    private volatile int quantum;
    private boolean iniciado = false;

    /**
     * @param tamanosRam RAM de cada computador; su largo es la cantidad de computadores
     * @param politicas  politica inicial de cada computador, mismo largo que tamanosRam
     */
    public Simulador(int duracionCicloMs, int quantum, int[] tamanosRam, PoliticaPlanificacion[] politicas) {
        if (tamanosRam.length < MIN_COMPUTADORES) {
            throw new IllegalArgumentException("Se necesitan al menos " + MIN_COMPUTADORES + " computadores.");
        }
        if (politicas.length != tamanosRam.length) {
            throw new IllegalArgumentException("Debe haber una politica por computador.");
        }
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor a 0.");
        }
        this.quantum = quantum;
        this.reloj = new Reloj(duracionCicloMs);
        this.computadores = new Computador[tamanosRam.length];
        // Los IDs de los computadores empiezan en 1
        for (int i = 0; i < tamanosRam.length; i++) {
            computadores[i] = new Computador(i + 1, tamanosRam[i], crearEstrategia(politicas[i], quantum), reloj);
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

    public int getCantidadComputadores() { return computadores.length; }
    public int getQuantum() { return quantum; }
    public long getCicloActual() { return reloj.getCicloActual(); }
    public int getDuracionCicloMs() { return reloj.getDuracionCicloMs(); }
    public boolean estaCorriendo() { return iniciado && !reloj.estaPausado(); }
}
