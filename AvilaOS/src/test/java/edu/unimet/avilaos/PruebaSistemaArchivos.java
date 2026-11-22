package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoSistemaArchivos;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ConfigDisco;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import edu.unimet.avilaos.hardware.disco.enums.DiskBlockType;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Permiso;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class PruebaSistemaArchivos {

    static void ejecutar() throws Exception {
        SistemaArchivos oficial = ArchivoSistemaArchivos.cargar(Path.of("../escenarios/08_sistema_archivos.json"));
        Simulador simulador = new Simulador(Configuracion.porDefecto(), oficial);
        comprobar(simulador.getDisco().getCurrentHead() == 50 && simulador.getDisco().getTotalBlocks() == 200
                && oficial.raiz().cantidadHijos() == 2 && oficial.cantidadArchivos() == 8,
                "Debe respetar la geometría y jerarquía del ejemplo oficial.");
        var notas = oficial.buscar("/usuarios/notas.txt").entrada();
        comprobar(notas.nombre().equals("notas.txt") && notas.bloques() == 1 && notas.primerBloque() == 34
                && notas.duenio().equals("user1") && notas.permiso() == Permiso.PRIVATE,
                "La entrada debe conservar los metadatos del JSON.");
        var bloques = simulador.getDisco().getBlocks();
        for (int i = 0; i < oficial.cantidadArchivos(); i++) {
            var archivo = oficial.archivo(i);
            var bloque = bloques[archivo.primerBloque()];
            comprobar(bloque.contentType() == DiskBlockType.FILE && bloque.filePath().equals(archivo.ruta()),
                    "Los bloques del JSON deben estar realmente asignados.");
        }

        Path temporal = Files.createTempFile("avilaos-sistema-archivos", ".json");
        try {
            String archivo = """
                    {"path":"/a/b/datos","type":"FILE","owner":"ana","permissions":"public",
                     "startBlock":6,"blocks":2,"blockChain":[6,3]}
                    """;
            String directorios = """
                    {"path":"/a/b","type":"DIRECTORY","owner":"ana","permissions":"private"},
                    {"path":"/a","type":"DIRECTORY","owner":"ana","permissions":"public"},
                    {"path":"/a/b/vacio","type":"DIRECTORY","owner":"ana","permissions":"public"}
                    """;
            SistemaArchivos anidado = cargar(temporal, archivo + "," + directorios);
            Configuracion config = Configuracion.porDefecto();
            config.setDisco(new ConfigDisco(200, 50, 2, 1, DiskPolicy.SSTF));
            ArchivoConfiguracion.guardar(config, temporal);
            Simulador nuevo = new Simulador(ArchivoConfiguracion.cargar(temporal), anidado);
            comprobar(nuevo.getDisco().getCurrentHead() == 7 && nuevo.getDisco().getActivePolicyEnum() == DiskPolicy.SSTF
                    && anidado.buscar("/a/b/vacio").cantidadHijos() == 0
                    && anidado.buscar("/a/b/datos").padre() == anidado.buscar("/a/b"),
                    "Debe resolver padres aun si aparecen después de sus hijos.");
            comprobar(Arrays.equals(nuevo.getDisco().getStorage().getFileChain("/a/b/datos", 6), new int[] {6, 3}),
                    "La cadena fragmentada debe quedar disponible en el disco.");
            System.out.println("OK: JSON oficial, geometría, directorios vacíos y cadena fragmentada.");

            rechazar(temporal, archivo, "padre");
            rechazar(temporal, archivo + "," + archivo + "," + directorios, "duplicada");
            rechazar(temporal, archivo + "," + archivo.replace("/a/b/datos", "/a/b/copia") + "," + directorios, "repetido");
            rechazar(temporal, archivo.replace("[6,3]", "[6,8]") + "," + directorios, "fuera de rango");
            rechazar(temporal, archivo.replace(",\"blockChain\":[6,3]", "") + "," + directorios, "blockChain");
            rechazar(temporal, archivo.replace("\"startBlock\":6", "\"startBlock\":\"6\"") + "," + directorios, "entero");
            rechazar(temporal, archivo.replace("\"public\"", "true") + "," + directorios, "permissions");
            rechazar(temporal, archivo + "," + archivo.replace("/a/b/datos", "/a/b/datos/hijo")
                    .replace("\"startBlock\":6", "\"startBlock\":5").replace("[6,3]", "[5,4]") + "," + directorios, "padre");
            try {
                ArchivoConfiguracion.guardar(Configuracion.porDefecto(), temporal);
                new Simulador(ArchivoConfiguracion.cargar(temporal), anidado);
                throw new AssertionError("La carga no debe reducir el swap para que quepa.");
            } catch (IllegalArgumentException esperado) {
                comprobar(esperado.getMessage().contains("swap"), "La incompatibilidad de swap debe explicarse.");
            }
            comprobar(simulador.getSistemaArchivos() == oficial && simulador.getDisco().getCurrentHead() == 50,
                    "Un candidato inválido no modifica la simulación anterior.");
            System.out.println("OK: rutas, datos y bloques inválidos se rechazan antes de publicar la carga.");
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static SistemaArchivos cargar(Path ruta, String entradas) throws Exception {
        Files.writeString(ruta, """
                {"diskConfig":{"totalBlocks":8,"initialHead":7},"fileSystem":[%s]}
                """.formatted(entradas));
        return ArchivoSistemaArchivos.cargar(ruta);
    }

    private static void rechazar(Path ruta, String entradas, String mensaje) throws Exception {
        try {
            cargar(ruta, entradas);
            throw new AssertionError("Se esperaba rechazar " + entradas);
        } catch (ConfiguracionInvalidaException esperado) {
            comprobar(esperado.getMessage().contains(mensaje), esperado.getMessage());
        }
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
