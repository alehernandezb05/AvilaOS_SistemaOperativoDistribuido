package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ArchivoSistemaArchivos;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Entrada unica para la validacion de entrega en NetBeans, sin frameworks. */
public final class PruebaEntrega {
    public static void main(String[] args) throws Exception {
        System.out.println("JAVA: " + System.getProperty("java.version"));
        System.out.println("ENTORNO: " + System.getProperty("os.name"));
        System.out.println("DIRECTORIO: " + Path.of("").toAbsolutePath());
        Path escenarios = Path.of("..", "escenarios");
        comprobar(Files.isRegularFile(escenarios.resolve("08_sistema_archivos.json")),
                "Ejecutar desde la carpeta AvilaOS (NetBeans: Run File).");
        entradas(escenarios);
        PruebaUnComputador.main(new String[0]);
        System.out.println("OK ENTREGA 2/7: CPU, RAM y configuracion de un computador.");
        PruebaDisco.main(new String[0]);
        System.out.println("OK ENTREGA 3/7: disco, CRUD, permisos, swap y persistencia.");
        PruebaDeadlines.main(new String[0]);
        System.out.println("OK ENTREGA 4/7: deadlines y cancelacion de transferencias.");
        PruebaMetricasDisco.main(new String[0]);
        PruebaMetricasSuspension.main(new String[0]);
        System.out.println("OK ENTREGA 5/7: metricas de disco, suspension y CPU.");
        PruebaSincronizacion.main(new String[0]);
        System.out.println("OK ENTREGA 6/7: sincronizacion.");
        PruebaCasosOficiales.main(new String[]{"target/entrega/resultados_disco"});
        System.out.println("OK ENTREGA 7/7: casos oficiales y comparacion de politicas.");
        System.out.println("ENTREGA: 7/7 grupos aprobados. Validacion visual independiente en NetBeans.");
    }

    private static void entradas(Path escenarios) throws Exception {
        var config = ArchivoConfiguracion.cargar(escenarios.resolve("09_procesos_archivos.json"));
        var archivos = ArchivoSistemaArchivos.cargar(escenarios.resolve("08_sistema_archivos.json"));
        sinInicio(new Simulador(Configuracion.porDefecto()));
        sinInicio(new Simulador(config));
        sinInicio(new Simulador(Configuracion.porDefecto(), archivos));
        Simulador sim = new Simulador(config, archivos);
        comprobar(sim.impedimentoInicio() == null, "Ambos JSON validos habilitan inicio.");
        Path temporal = Files.createTempDirectory("avilaos-entrega-");
        Path invalido = temporal.resolve("invalido.json");
        String procesos = Files.readString(escenarios.resolve("09_procesos_archivos.json"));
        String disco = Files.readString(escenarios.resolve("08_sistema_archivos.json"));
        try {
            sim.setDuracionCicloMs(50);
            sim.iniciar();
            esperarCiclo(sim, 1);
            rechazar(invalido, procesos.replace("\"memory\": 16", "\"memory\": \"mal\""), true);
            rechazar(invalido, procesos.replace("\"ramCapacity\": 1024", "\"ramCapacity\": 0"), true);
            rechazar(invalido, procesos.replace("/sistema/config.sys", "ruta-relativa"), true);
            rechazar(invalido, disco.replace("\"startBlock\": 11", "\"startBlock\": 200"), false);
            rechazar(invalido, disco.replace("\"type\": \"FILE\"", "\"type\": \"INVALIDO\""), false);
            try {
                ArchivoConfiguracion.cargar(temporal.resolve("no-existe.json"));
                throw new AssertionError("Una ruta de archivo inexistente debe rechazarse.");
            } catch (IOException esperado) { }
            long antes = sim.getCicloActual();
            esperarCiclo(sim, antes + 2);
            comprobar(sim.estaCorriendo() && sim.copiaSistemaArchivos().buscar("/sistema/config.sys") != null,
                    "Los errores de carga no deben detener ni reemplazar la simulacion activa.");
        } finally {
            sim.detener();
            Files.deleteIfExists(invalido);
            Files.deleteIfExists(temporal);
        }
        System.out.println("OK ENTREGA 1/7: ambos JSON obligatorios; tipo, rango y ruta invalidos rechazados con reloj activo.");
    }

    private static void sinInicio(Simulador sim) {
        comprobar(sim.impedimentoInicio() != null, "Sin ambos JSON no se debe habilitar inicio.");
        try {
            sim.iniciar();
            throw new AssertionError("Inicio indebido sin ambos JSON.");
        } catch (IllegalStateException esperado) { }
        finally { sim.detener(); }
        comprobar(sim.getCicloActual() == 0, "El reloj no debe avanzar.");
    }

    private static void rechazar(Path archivo, String contenido, boolean procesos) throws Exception {
        Files.writeString(archivo, contenido);
        try {
            if (procesos) ArchivoConfiguracion.cargar(archivo);
            else ArchivoSistemaArchivos.cargar(archivo);
        } catch (ConfiguracionInvalidaException esperado) {
            return;
        }
        throw new AssertionError("Entrada invalida aceptada: " + (procesos ? "procesos" : "disco"));
    }

    private static void esperarCiclo(Simulador sim, long objetivo) throws Exception {
        long limite = System.nanoTime() + 5_000_000_000L;
        while (sim.getCicloActual() < objetivo && System.nanoTime() < limite) Thread.sleep(5);
        comprobar(sim.getCicloActual() >= objetivo, "El reloj no avanzo.");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
