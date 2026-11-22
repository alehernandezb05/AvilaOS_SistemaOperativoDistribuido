package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Computador;
import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import edu.unimet.avilaos.metricas.CalculadorMetricas;
import edu.unimet.avilaos.metricas.ResumenMetricas;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.MotivoTerminacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import edu.unimet.avilaos.so.TipoProceso;
import java.nio.file.Files;
import java.nio.file.Path;

/** Comprobaciones ejecutables con Java, sin bibliotecas de pruebas externas. */
public final class PruebaUnComputador {

    public static void main(String[] args) throws Exception {
        if (seleccionada(args, "inicio")) inicio();
        if (seleccionada(args, "quantum")) quantum();
        if (seleccionada(args, "cantidad")) cantidad();
        if (seleccionada(args, "politicas")) politicas();
        if (seleccionada(args, "memoria")) memoria();
        if (seleccionada(args, "es")) entradaSalida();
        if (seleccionada(args, "json")) json();
        if (seleccionada(args, "legado")) legado();
        if (args.length > 0 && !args[0].matches("inicio|quantum|cantidad|politicas|memoria|es|json|legado")) {
            throw new IllegalArgumentException("Prueba desconocida: " + args[0]);
        }
    }

    private static boolean seleccionada(String[] args, String nombre) {
        return args.length == 0 || args[0].equals(nombre);
    }

    private static Configuracion config(PoliticaPlanificacion politica, int quantum, int ram) {
        return new Configuracion(100, quantum, 0, new Configuracion.ConfigComputador[] {
            new Configuracion.ConfigComputador(ram, politica)
        });
    }

    private static PCB proceso(String nombre, int memoria, int instrucciones, int deadline, int prioridad) {
        return new PCB(nombre, 1, TipoProceso.CPU_BOUND, prioridad, memoria, instrucciones, deadline, 0);
    }

    /** Usa los mismos componentes publicos y el mismo orden que el reloj global. */
    private static void ciclo(Computador computador, long numero) {
        computador.getNucleo().alCiclo(numero);
        computador.getCpu().alCiclo(numero);
    }

    private static void inicio() throws Exception {
        Configuracion config = Configuracion.porDefecto();
        config.validar();
        comprobar(config.getCantidadComputadores() == 1, "La configuracion inicial debe tener un computador.");
        Simulador simulador = new Simulador(config);
        comprobar(simulador.getCantidadComputadores() == 1, "El simulador debe crear un computador.");
        comprobar(simulador.getBuffers().length == 0, "No se necesitan buffers para iniciar.");
        comprobar(simulador.getComputador(1).getRam().getLibre() == 256, "La RAM inicial queda disponible.");
        simulador.setDuracionCicloMs(250);
        comprobar(simulador.getDuracionCicloMs() == 250, "Se puede cambiar la duracion de ciclo.");
        System.out.println("OK: inicio con un computador, sin buffers ni reserva de RAM oculta.");
    }

    private static void quantum() throws Exception {
        for (PoliticaPlanificacion politica : PoliticaPlanificacion.values()) {
            if (politica != PoliticaPlanificacion.ROUND_ROBIN) {
                Configuracion config = config(politica, 0, 64);
                config.validar();
                Simulador simulador = new Simulador(config);
                comprobar(simulador.getQuantum() == 0, politica + " admite quantum 0.");
                rechazar(() -> simulador.cambiarPolitica(1, PoliticaPlanificacion.ROUND_ROBIN));
                comprobar(simulador.getComputador(1).getNucleo().getEstrategia().getPolitica() == politica,
                        "Un cambio invalido no sustituye la politica anterior.");
                simulador.setQuantum(3);
                simulador.cambiarPolitica(1, PoliticaPlanificacion.ROUND_ROBIN);
                rechazar(() -> simulador.setQuantum(0));
                comprobar(simulador.getQuantum() == 3, "Round Robin conserva su quantum tras rechazar cero.");
                simulador.cambiarPolitica(1, PoliticaPlanificacion.FCFS);
                simulador.setQuantum(0);
                comprobar(simulador.getQuantum() == 0, "Cero vuelve a ser valido sin Round Robin.");
            }
        }
        rechazarConfiguracion(config(PoliticaPlanificacion.ROUND_ROBIN, 0, 64));
        rechazar(() -> new Simulador(config(PoliticaPlanificacion.ROUND_ROBIN, 0, 64)));
        rechazar(() -> new Simulador(config(PoliticaPlanificacion.FCFS, -1, 64)));
        rechazar(() -> new Simulador(config(PoliticaPlanificacion.FCFS, 51, 64)));
        System.out.println("OK: quantum 0 solo sin Round Robin y cambios en caliente validados.");
    }

    private static void cantidad() throws Exception {
        Configuracion vacia = new Configuracion(100, 3, 0, new Configuracion.ConfigComputador[0]);
        Configuracion dos = new Configuracion(100, 3, 0, new Configuracion.ConfigComputador[] {
            new Configuracion.ConfigComputador(64, PoliticaPlanificacion.FCFS),
            new Configuracion.ConfigComputador(64, PoliticaPlanificacion.EDF)
        });
        rechazarConfiguracion(vacia);
        rechazarConfiguracion(dos);
        rechazar(() -> new Simulador(vacia));
        rechazar(() -> new Simulador(dos));
        Simulador simulador = new Simulador(Configuracion.porDefecto());
        rechazar(() -> simulador.getComputador(2));
        System.out.println("OK: no se configura ni crea un segundo computador.");
    }

    private static void politicas() throws Exception {
        for (PoliticaPlanificacion politica : PoliticaPlanificacion.values()) {
            Simulador simulador = new Simulador(config(politica, 3, 64));
            PCB largo = proceso("Largo", 16, 6, 100, 5);
            PCB urgente = proceso("Urgente", 16, 1, 10, 1);
            simulador.agregarProceso(largo);
            simulador.agregarProceso(urgente);
            Computador c = simulador.getComputador(1);
            ciclo(c, 1);
            PCB esperado = politica == PoliticaPlanificacion.EDF
                    || politica == PoliticaPlanificacion.PRIORIDADES_APROPIATIVAS ? urgente : largo;
            comprobar(c.getCpu().getProcesoActual() == esperado, politica + " conserva su criterio de seleccion.");
            comprobar(esperado.getProgramCounter() == 1 && esperado.getMar() == 1,
                    "PC y MAR avanzan una instruccion.");
        }
        Simulador simulador = new Simulador(config(PoliticaPlanificacion.FCFS, 3, 64));
        PCB largo = proceso("Largo", 16, 6, 100, 5);
        PCB corto = proceso("Corto", 16, 1, 100, 1);
        simulador.agregarProceso(largo);
        simulador.agregarProceso(corto);
        Computador c = simulador.getComputador(1);
        ciclo(c, 1);
        simulador.cambiarPolitica(1, PoliticaPlanificacion.ROUND_ROBIN);
        simulador.setQuantum(2);
        ciclo(c, 2);
        comprobar(largo.getProgramCounter() == 2, "El proceso conserva CPU hasta consumir su quantum.");
        ciclo(c, 3);
        comprobar(c.getCpu().getProcesoActual() == corto && corto.getProgramCounter() == 1,
                "El quantum cambiado se aplica a la ejecucion real.");
        System.out.println("OK: cuatro politicas, PC/MAR y expropiacion con quantum cambiado.");
    }

    private static void memoria() {
        Simulador simulador = new Simulador(config(PoliticaPlanificacion.FCFS, 0, 32));
        PCB primero = proceso("Primero", 16, 2, 100, 1);
        PCB espera = proceso("Espera", 24, 1, 100, 1);
        PCB imposible = proceso("No cabe", 80, 1, 2, 1);
        simulador.agregarProceso(primero);
        simulador.agregarProceso(espera);
        simulador.agregarProceso(imposible);
        Computador c = simulador.getComputador(1);
        ciclo(c, 1);
        comprobar(c.getRam().getUsada() == 16 && espera.getEstado() == EstadoProceso.NUEVO,
                "El segundo proceso espera cuando no cabe.");
        ciclo(c, 2);
        comprobar(imposible.getMotivoTerminacion() == MotivoTerminacion.DEADLINE_VENCIDO,
                "El proceso que nunca cabe vence en Nuevos.");
        comprobar(c.getRam().getUsada() == 16, "Un Nuevo vencido no libera RAM de otro proceso.");
        ciclo(c, 3);
        comprobar(espera.getEstado() == EstadoProceso.EJECUCION && c.getRam().getUsada() == 24,
                "Al terminar el primero se admite el que esperaba.");
        ciclo(c, 4);
        comprobar(c.getRam().getUsada() == 0, "Al completar se libera toda la RAM reservada.");
        ResumenMetricas r = CalculadorMetricas.deComputador(c, 4);
        comprobar(r.getTerminados() == 3 && r.getCompletados() == 2, "Las metricas distinguen deadline de completado.");
        comprobar(r.getThroughput() == 0.5 && r.getUtilizacion() == 75.0,
                "Se conservan throughput y utilizacion al ciclo de corte.");
        System.out.println("OK: admision, liberacion, deadline de Nuevos y metricas CPU.");
    }

    private static void entradaSalida() {
        Simulador simulador = new Simulador(config(PoliticaPlanificacion.FCFS, 0, 64));
        PCB p = new PCB("E/S", 1, TipoProceso.IO_BOUND, 1, 16, 2, 100, 0);
        p.setCiclosParaES(1);
        p.setDuracionES(2);
        simulador.agregarProceso(p);
        Computador c = simulador.getComputador(1);
        ciclo(c, 1);
        ciclo(c, 2);
        comprobar(p.getEstado() == EstadoProceso.BLOQUEADO && c.getCpu().estaLibre(),
                "Se conserva el bloqueo de E/S del Proyecto 1 mientras se integra el disco.");
        comprobar(c.getRam().getUsada() == 16, "Bloquear no libera RAM.");
        ciclo(c, 3);
        ciclo(c, 4);
        ciclo(c, 5);
        comprobar(p.cumplioDeadline() && c.getRam().getUsada() == 0,
                "El proceso completa y libera su memoria tras desbloquear.");
        System.out.println("OK: E/S existente sin regresion ni buffers.");
    }

    private static void json() throws Exception {
        Path archivo = Files.createTempFile("avilaos-un-computador-", ".json");
        try {
            Simulador simulador = new Simulador(config(PoliticaPlanificacion.FCFS, 0, 64));
            simulador.agregarProceso(proceso("Guardado", 16, 2, 100, 1));
            ArchivoConfiguracion.guardar(simulador.getConfiguracion(), archivo);
            Configuracion cargada = ArchivoConfiguracion.cargar(archivo);
            Simulador nuevo = new Simulador(cargada);
            comprobar(nuevo.getCantidadComputadores() == 1 && nuevo.getQuantum() == 0,
                    "Guardar/cargar conserva el computador y quantum cero.");
            comprobar(nuevo.getComputador(1).getNucleo().getColas().cantidadNuevos() == 1,
                    "Guardar/cargar conserva la carga de procesos.");
        } finally {
            Files.deleteIfExists(archivo);
        }
        System.out.println("OK: JSON de configuracion y procesos de un computador.");
    }

    private static void rechazarConfiguracion(Configuracion config) throws Exception {
        try {
            config.validar();
        } catch (ConfiguracionInvalidaException esperado) {
            return;
        }
        throw new AssertionError("La configuracion invalida fue aceptada.");
    }

    private static void legado() throws Exception {
        Configuracion config = new Configuracion(100, 3, 0,
                new Configuracion.ConfigComputador[] {
                    new Configuracion.ConfigComputador(64, PoliticaPlanificacion.FCFS)
                }, new Configuracion.ConfigBuffer[] {
                    new Configuracion.ConfigBuffer(8, 1)
                }, new edu.unimet.avilaos.config.ConfigProceso[0]);
        rechazarConfiguracion(config);
        System.out.println("OK: no se cargan buffers invisibles del Proyecto 1.");
    }

    private static void rechazar(Runnable accion) {
        try {
            accion.run();
        } catch (IllegalArgumentException esperado) {
            return;
        }
        throw new AssertionError("La operacion invalida fue aceptada.");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
