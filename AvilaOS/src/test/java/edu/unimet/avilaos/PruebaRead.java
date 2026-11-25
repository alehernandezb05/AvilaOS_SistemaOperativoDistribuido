package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ArchivoSistemaArchivos;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import edu.unimet.avilaos.hardware.ModoEjecucion;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.metricas.CalculadorMetricas;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.MotivoBloqueo;
import edu.unimet.avilaos.so.MotivoTerminacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class PruebaRead {

    static void ejecutar() throws Exception {
        Configuracion oficial = ArchivoConfiguracion.cargar(Path.of("../escenarios/09_procesos_archivos.json"));
        SistemaArchivos archivos = ArchivoSistemaArchivos.cargar(Path.of("../escenarios/08_sistema_archivos.json"));
        comprobarBloqueo(new Simulador(Configuracion.porDefecto()));
        comprobarBloqueo(new Simulador(oficial));
        comprobarBloqueo(new Simulador(Configuracion.porDefecto(), archivos));

        Simulador sim = new Simulador(oficial, archivos);
        comprobar(sim.impedimentoInicio() == null && oficial.getProcesos().length == 8
                && sim.getDisco().getSwapSizeBlocks() == 100 && sim.getQuantum() == 0,
                "Los dos documentos se combinan antes de iniciar.");
        unCicloCompleto(sim);
        System.out.println("OK: ambos JSON, esquema oficial y READ integrado con las políticas de CPU y disco.");

        Path puntual = Files.createTempFile("avilaos-read", ".json");
        try {
            Files.writeString(puntual, """
                    {"diskConfig":{"totalBlocks":10,"initialHead":0},"fileSystem":[
                      {"path":"/a","type":"FILE","startBlock":2,"blocks":2,"blockChain":[2,4],
                       "owner":"ana","permissions":"private"}
                    ]}
                    """);
            SistemaArchivos fragmentado = ArchivoSistemaArchivos.cargar(puntual);
            comprobarBloqueo(new Simulador(Configuracion.porDefecto(), fragmentado));
            cicloRead(fragmentado);
            vencimientoDuranteRead(fragmentado);

            Files.writeString(puntual, """
                    {"globalConfig":{"cycleDurationMs":300,"swapSizeBlocks":0,"initialDiskPolicy":"FIFO"},
                     "computer":{"ramCapacity":32,"policy":"FCFS"},
                     "processes":[{"name":"X","memory":"16","instructions":2,"deadline":500,"operation":"READ",
                                   "targetPath":"/a"}]}
                    """);
            try {
                ArchivoConfiguracion.cargar(puntual);
                throw new AssertionError("Una carga de procesos con memoria textual no es válida.");
            } catch (ConfiguracionInvalidaException esperado) {
                comprobar(esperado.getMessage().contains("memory"), "Debe identificar el campo inválido.");
            }
        } finally {
            Files.deleteIfExists(puntual);
        }
        System.out.println("OK: READ en modo SO, espera real, fin con una instrucción y permisos del dueño.");
    }

    private static void comprobarBloqueo(Simulador sim) {
        try {
            sim.iniciar();
            throw new AssertionError("No debe iniciar sin ambos JSON.");
        } catch (IllegalStateException esperado) {
            comprobar(sim.getCicloActual() == 0 && !sim.estaCorriendo(), "El reloj debe quedar detenido.");
        } finally {
            sim.detener();
        }
    }

    private static void unCicloCompleto(Simulador sim) {
        for (int ciclo = 1; ciclo <= 400; ciclo++) avanzar(sim, ciclo);
        var colas = sim.getComputador(1).getNucleo().getColas();
        int correctos = 0;
        int rechazados = 0;
        for (int i = 0; i < colas.cantidadTerminados(); i++) {
            PCB proceso = colas.obtenerTerminado(i);
            if (proceso.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO) correctos++;
            if (proceso.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES) rechazados++;
        }
        comprobar(correctos == 5 && rechazados == 3 && sim.getDisco().getTotalRequestsServed() == 5,
                "El ejemplo ejecuta sus cinco READ y muestra los tres rechazos de escritura.");
        var metricas = CalculadorMetricas.deComputador(sim.getComputador(1), 400);
        comprobar(metricas.getTerminados() == 8 && metricas.getCompletados() == 5
                && metricas.getCumplimiento() == 100.0, "Un rechazo de E/S no cuenta como deadline vencido.");
        String log = String.join("\n", sim.getLog().obtenerDesde(0));
        comprobar(log.contains("UPDATE requiere newName") && log.contains("DELETE denegado")
                && log.contains("encola solicitud") && log.contains("El disco atiende") && log.contains("bloqueado -> listo"),
                "El registro explica encolado, atención, fin y rechazos.");
    }

    private static void cicloRead(SistemaArchivos fragmentado) {
        Simulador sim = new Simulador(Configuracion.porDefecto(), fragmentado);
        PCB lector = lector("ana");
        PCB sinPermiso = lector(null);
        sim.agregarProceso(lector);
        sim.agregarProceso(sinPermiso);
        var computador = sim.getComputador(1);
        var colas = computador.getNucleo().getColas();
        comprobar(lector.getEstado() == EstadoProceso.NUEVO, "El proceso empieza Nuevo.");
        avanzar(sim, 1);
        comprobar(lector.getEstado() == EstadoProceso.EJECUCION && lector.getProgramCounter() == 1
                && lector.getTiempoRestante() == 0 && computador.getCpu().getModo() == ModoEjecucion.SISTEMA_OPERATIVO,
                "La instrucción READ se ejecuta en CPU en modo SO.");
        avanzar(sim, 2);
        DiskRequest[] pendientes = sim.getDisco().getPendingRequests();
        comprobar(lector.getEstado() == EstadoProceso.BLOQUEADO && lector.getMotivoBloqueo() == MotivoBloqueo.ESPERA_DISCO
                && computador.getCpu().getProcesoActual() == sinPermiso && pendientes.length == 1
                && pendientes[0].getProcessId() == lector.getId() && Arrays.equals(pendientes[0].getBlockNumbers(), new int[] {2, 4}),
                "Entrega la CPU y encola una solicitud con la cadena y el ID del proceso.");
        for (int ciclo = 3; ciclo <= 7; ciclo++) {
            avanzar(sim, ciclo);
            comprobar(lector.getEstado() == EstadoProceso.BLOQUEADO, "Solo el resultado real despierta al proceso.");
        }
        comprobar(sinPermiso.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES,
                "El invitado con el mismo nombre de proceso no puede leer el archivo privado.");
        avanzar(sim, 8);
        comprobar(lector.getEstado() == EstadoProceso.LISTO && colas.obtenerListo(0) == lector
                && sim.getDisco().getTotalRequestsServed() == 1 && computador.getRam().getUsada() == 1,
                "Al transferir el último bloque vuelve a Listo, con su RAM aún asignada.");
        avanzar(sim, 9);
        comprobar(lector.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO
                && lector.getProgramCounter() == 1 && lector.getMar() == 1 && computador.getRam().getUsada() == 0,
                "Completa sin ejecutar otra instrucción y libera su memoria.");
    }

    private static PCB lector(String solicitante) {
        return new PCB("Mismo nombre", 1, TipoProceso.IO_BOUND, 1, 1, 1, 30, 0,
                new OperacionArchivo(DiskOperation.READ, "/a", solicitante, 0, null));
    }

    private static void vencimientoDuranteRead(SistemaArchivos fragmentado) {
        Simulador sim = new Simulador(Configuracion.porDefecto(), fragmentado);
        PCB lector = new PCB("Lector con deadline", 1, TipoProceso.IO_BOUND, 1, 1, 2, 3, 0,
                new OperacionArchivo(DiskOperation.READ, "/a", "ana", 0, null));
        sim.agregarProceso(lector);
        for (int ciclo = 1; ciclo <= 10; ciclo++) avanzar(sim, ciclo);
        comprobar(lector.getMotivoTerminacion() == MotivoTerminacion.DEADLINE_VENCIDO
                && lector.getEstado() == EstadoProceso.TERMINADO && sim.getComputador(1).getRam().getUsada() == 0,
                "Una finalización tardía del disco no revive al lector ni reserva RAM nuevamente.");
    }

    private static void avanzar(Simulador sim, long ciclo) {
        var computador = sim.getComputador(1);
        sim.getDisco().alCiclo(ciclo);
        computador.getNucleo().alCiclo(ciclo);
        computador.getCpu().alCiclo(ciclo);
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
