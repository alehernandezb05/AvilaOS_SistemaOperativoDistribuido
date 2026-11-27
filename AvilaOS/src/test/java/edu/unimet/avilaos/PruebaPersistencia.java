package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ArchivoSistemaArchivos;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.enums.DiskBlockType;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Entrada;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Nodo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Guardar el sistema de archivos real y recargarlo con las mismas cadenas, dueños y permisos. */
public final class PruebaPersistencia {

    private PruebaPersistencia() { }

    public static void main(String[] args) throws Exception {
        ejecutar();
    }

    static void ejecutar() throws Exception {
        Path carpeta = Files.createTempDirectory("avilaos-persistencia");
        try {
            cicloCompleto(carpeta);
            validaciones(carpeta);
            fallosNoCorrompen(carpeta);
        } finally {
            try (var archivos = Files.walk(carpeta)) {
                archivos.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void cicloCompleto(Path carpeta) throws Exception {
        Configuracion oficial = ArchivoConfiguracion.cargar(Path.of("../escenarios/09_procesos_archivos.json"));
        Simulador sim = new Simulador(oficial,
                ArchivoSistemaArchivos.cargar(Path.of("../escenarios/08_sistema_archivos.json")));
        sim.getDisco().setSpeed(10);
        PCB crear = operacion(DiskOperation.CREATE, "/usuarios/datos.bin", 60, null);
        PCB carpetaNueva = operacion(DiskOperation.CREATE, "/usuarios/proyectos", 0, null);
        sim.solicitarProceso(crear);
        sim.solicitarProceso(carpetaNueva);
        long ciclo = avanzarHasta(sim, 1, crear, carpetaNueva);
        PCB renombrar = operacion(DiskOperation.UPDATE, "/usuarios/video.mp4", 0, "pelicula.mp4");
        PCB borrar = operacion(DiskOperation.DELETE, "/sistema/core.dll", 0, null);
        sim.solicitarProceso(renombrar);
        sim.solicitarProceso(borrar);
        avanzarHasta(sim, ciclo, renombrar, borrar);

        SistemaArchivos actual = sim.getSistemaArchivos();
        Entrada datos = actual.buscar("/usuarios/datos.bin").entrada();
        comprobar(datos.bloques() == 60 && !contigua(datos.cadena()), "El archivo creado ocupa una cadena no contigua.");
        comprobar(actual.buscar("/usuarios/pelicula.mp4") != null && actual.buscar("/sistema/core.dll") == null,
                "El estado actual tiene el renombre y el borrado.");

        Path guardado = carpeta.resolve("disco.json");
        ArchivoSistemaArchivos.guardar(actual, guardado);
        String json = Files.readString(guardado);
        comprobar(json.contains("\"blockChain\"") && json.contains("pelicula.mp4") && !json.contains("core.dll"),
                "El JSON guarda el estado real con la cadena exacta.");

        SistemaArchivos recargado = ArchivoSistemaArchivos.cargar(guardado);
        comprobar(Arrays.equals(describir(actual), describir(recargado)),
                "Recargar da la misma jerarquía, dueños, permisos y cadenas.");
        comprobar(recargado.bloquesDisco() == actual.bloquesDisco()
                && recargado.cabezalInicial() == actual.cabezalInicial(), "Se conserva la geometría del disco.");

        // Reiniciar con otra carga de procesos sobre el disco guardado
        Simulador otro = new Simulador(ArchivoConfiguracion.cargar(
                Path.of("../escenarios/07_un_computador.json")), recargado);
        DiskBlock[] mapa = otro.getDisco().getBlocks();
        for (int bloque : datos.cadena()) {
            comprobar(mapa[bloque].contentType() == DiskBlockType.FILE
                    && "/usuarios/datos.bin".equals(mapa[bloque].filePath()),
                    "El disco recargado marca la cadena en el mapa: bloque " + bloque);
        }
        int swap = 0;
        for (DiskBlock b : mapa) if (b.contentType() == DiskBlockType.SWAP_RESERVED) swap++;
        comprobar(swap == 100 && otro.impedimentoInicio() == null,
                "La reserva de swap no choca con los archivos y la simulación puede iniciar.");
        // Ejecutar la nueva carga, no solo comprobar que puede iniciar.
        var computador = otro.getComputador(1);
        for (long c = 1; c <= 100; c++) {
            otro.getDisco().alCiclo(c);
            computador.getNucleo().alCiclo(c);
            computador.getCpu().alCiclo(c);
        }
        var metricas = edu.unimet.avilaos.metricas.CalculadorMetricas.deComputador(computador, 100);
        comprobar(metricas.getTerminados() == 3 && metricas.getCompletados() == 2
                && computador.getRam().getUsada() == 0,
                "La segunda carga ejecuta CPU y E/S; el proceso que no cabe vence.");
        comprobar(Arrays.equals(describir(actual), describir(otro.getSistemaArchivos())),
                "La segunda ejecucion conserva las modificaciones y cadenas del disco recargado.");
        System.out.println("OK: segunda carga CPU/RAM ejecutada sobre disco fragmentado recargado.");
        System.out.println("OK: crear, renombrar, borrar, guardar y recargar con equivalencia y otra carga.");
    }

    private static void validaciones(Path carpeta) throws Exception {
        invalido(carpeta, "{\"diskConfig\":{\"totalBlocks\":50,\"initialHead\":0},\"fileSystem\":["
                + "{\"path\":\"/a\",\"type\":\"FILE\",\"startBlock\":3,\"blocks\":3,\"blockChain\":[3,4,3],"
                + "\"owner\":\"admin\",\"permissions\":\"public\"}]}", "ciclo en la cadena");
        invalido(carpeta, "{\"diskConfig\":{\"totalBlocks\":50,\"initialHead\":0},\"fileSystem\":["
                + "{\"path\":\"/a\",\"type\":\"FILE\",\"startBlock\":3,\"blocks\":3,\"blockChain\":[3,4],"
                + "\"owner\":\"admin\",\"permissions\":\"public\"}]}", "tamaño inconsistente");
        invalido(carpeta, "{\"diskConfig\":{\"totalBlocks\":50,\"initialHead\":0},\"fileSystem\":["
                + "{\"path\":\"/a\",\"type\":\"FILE\",\"startBlock\":3,\"blocks\":2,\"blockChain\":[3,90],"
                + "\"owner\":\"admin\",\"permissions\":\"public\"}]}", "enlace fuera del disco");
        invalido(carpeta, "{\"diskConfig\":{\"totalBlocks\":50,\"initialHead\":0},\"fileSystem\":["
                + "{\"path\":\"/a\",\"type\":\"FILE\",\"startBlock\":3,\"blocks\":1,\"owner\":\"admin\",\"permissions\":\"public\"},"
                + "{\"path\":\"/b\",\"type\":\"FILE\",\"startBlock\":2,\"blocks\":2,\"blockChain\":[2,3],"
                + "\"owner\":\"admin\",\"permissions\":\"public\"}]}", "doble asignación");

        Path lleno = carpeta.resolve("lleno.json");
        Files.writeString(lleno, "{\"diskConfig\":{\"totalBlocks\":120,\"initialHead\":0},\"fileSystem\":["
                + "{\"path\":\"/grande\",\"type\":\"FILE\",\"startBlock\":0,\"blocks\":30,\"blockChain\":["
                + rango(0, 30) + "],\"owner\":\"admin\",\"permissions\":\"public\"}]}");
        SistemaArchivos sinEspacio = ArchivoSistemaArchivos.cargar(lleno);
        try {
            new Simulador(ArchivoConfiguracion.cargar(Path.of("../escenarios/09_procesos_archivos.json")), sinEspacio);
            throw new AssertionError("Los archivos no deben chocar con la reserva de swap.");
        } catch (IllegalArgumentException e) {
            comprobar(e.getMessage().contains("swap"), "El choque con el swap se explica.");
        }
        System.out.println("OK: se rechazan ciclos, enlaces inválidos, tamaños, doble asignación y choque con swap.");
    }

    private static void fallosNoCorrompen(Path carpeta) throws Exception {
        SistemaArchivos sistema = ArchivoSistemaArchivos.cargar(Path.of("../escenarios/08_sistema_archivos.json"));
        Path valido = carpeta.resolve("valido.json");
        ArchivoSistemaArchivos.guardar(sistema, valido);
        byte[] antes = Files.readAllBytes(valido);

        Path ocupado = carpeta.resolve("ocupado");
        Files.createDirectories(ocupado.resolve("dentro"));
        try {
            ArchivoSistemaArchivos.guardar(sistema, ocupado);
            throw new AssertionError("Guardar sobre un directorio con contenido debe fallar.");
        } catch (IOException e) {
            // Se informa el fallo
        }
        try (var restos = Files.list(carpeta)) {
            comprobar(restos.noneMatch(p -> p.toString().endsWith(".tmp")), "Un fallo no deja temporales.");
        }
        comprobar(Arrays.equals(antes, Files.readAllBytes(valido)) && Files.isDirectory(ocupado.resolve("dentro")),
                "Un fallo no toca el archivo válido anterior ni el destino.");

        Path roto = carpeta.resolve("roto.json");
        Files.writeString(roto, "{ esto no es json");
        try {
            ArchivoSistemaArchivos.cargar(roto);
            throw new AssertionError("Un JSON roto debe rechazarse.");
        } catch (ConfiguracionInvalidaException e) {
            // El estado activo no cambia porque la carga se valida antes de publicarse
        }
        System.out.println("OK: un fallo de lectura o escritura se informa sin corromper estados ni archivos.");
    }

    private static PCB operacion(DiskOperation tipo, String ruta, int bloques, String nuevoNombre) {
        return new PCB(tipo + " " + ruta, 1, TipoProceso.IO_BOUND, 1, 1, 1, 3000, 0,
                new OperacionArchivo(tipo, ruta, "admin", bloques, nuevoNombre));
    }

    private static long avanzarHasta(Simulador sim, long desde, PCB... procesos) {
        long ciclo = desde;
        while (ciclo < desde + 5000) {
            boolean listos = true;
            for (PCB p : procesos) listos &= p.getEstado() == EstadoProceso.TERMINADO;
            if (listos) break;
            var computador = sim.getComputador(1);
            sim.getDisco().alCiclo(ciclo);
            computador.getNucleo().alCiclo(ciclo);
            computador.getCpu().alCiclo(ciclo);
            ciclo++;
        }
        for (PCB p : procesos) {
            comprobar(p.getEstado() == EstadoProceso.TERMINADO && p.cumplioDeadline(),
                    p.getNombre() + " debe completarse.");
        }
        return ciclo;
    }

    /** Una línea por entrada, en preorden, con todo lo que debe conservarse. */
    private static String[] describir(SistemaArchivos sistema) {
        StringBuilder texto = new StringBuilder();
        describir(sistema.vista().raiz(), texto);
        return texto.toString().split("\n");
    }

    private static void describir(Nodo nodo, StringBuilder destino) {
        for (int i = 0; i < nodo.cantidadHijos(); i++) {
            Entrada e = nodo.hijo(i).entrada();
            destino.append(e.ruta()).append('|').append(e.tipo()).append('|').append(e.duenio()).append('|')
                    .append(e.permiso()).append('|').append(Arrays.toString(e.cadena())).append('\n');
            describir(nodo.hijo(i), destino);
        }
    }

    private static boolean contigua(int[] cadena) {
        for (int i = 1; i < cadena.length; i++) if (cadena[i] != cadena[i - 1] + 1) return false;
        return true;
    }

    private static String rango(int desde, int cantidad) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < cantidad; i++) s.append(i == 0 ? "" : ",").append(desde + i);
        return s.toString();
    }

    private static void invalido(Path carpeta, String json, String caso) throws IOException {
        Path archivo = carpeta.resolve("invalido.json");
        Files.writeString(archivo, json);
        try {
            ArchivoSistemaArchivos.cargar(archivo);
        } catch (ConfiguracionInvalidaException e) {
            return;
        }
        throw new AssertionError("Debe rechazarse: " + caso);
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
