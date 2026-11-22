package edu.unimet.avilaos.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;

import static edu.unimet.avilaos.config.CamposJson.entero;
import static edu.unimet.avilaos.config.CamposJson.objeto;
import static edu.unimet.avilaos.config.CamposJson.texto;

/** Traduce el formato del enunciado a los parámetros del simulador. */
final class ArchivoProcesos {

    private ArchivoProcesos() { }

    static Configuracion leer(JsonObject raiz) {
        JsonObject global = objeto(raiz.get("globalConfig"), "globalConfig");
        JsonObject computador = objeto(raiz.get("computer"), "computer");
        PoliticaPlanificacion politica = switch (texto(computador, "policy")) {
            case "FCFS" -> PoliticaPlanificacion.FCFS;
            case "RR", "ROUND_ROBIN" -> PoliticaPlanificacion.ROUND_ROBIN;
            case "EDF" -> PoliticaPlanificacion.EDF;
            case "PRIORITY", "PRIORIDADES_APROPIATIVAS" -> PoliticaPlanificacion.PRIORIDADES_APROPIATIVAS;
            default -> throw new IllegalArgumentException("computer.policy no identifica una política de CPU.");
        };
        DiskPolicy politicaDisco = switch (texto(global, "initialDiskPolicy")) {
            case "FIFO" -> DiskPolicy.FIFO;
            case "SSTF" -> DiskPolicy.SSTF;
            case "SCAN" -> DiskPolicy.SCAN;
            case "C-SCAN", "C_SCAN" -> DiskPolicy.C_SCAN;
            default -> throw new IllegalArgumentException("initialDiskPolicy no identifica una política de disco.");
        };

        JsonElement contenido = raiz.get("processes");
        if (contenido == null || !contenido.isJsonArray()) {
            throw new IllegalArgumentException("processes debe ser un arreglo.");
        }
        var items = contenido.getAsJsonArray();
        ConfigProceso[] procesos = new ConfigProceso[items.size()];
        for (int i = 0; i < procesos.length; i++) {
            try {
                procesos[i] = leerProceso(objeto(items.get(i), "el proceso"));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("processes[" + i + "]: " + e.getMessage(), e);
            }
        }

        int swap = entero(global.get("swapSizeBlocks"), "swapSizeBlocks");
        int velocidad = enteroOpcional(global, "headSpeed", 1);
        Configuracion config = new Configuracion(entero(global.get("cycleDurationMs"), "cycleDurationMs"),
                enteroOpcional(computador, "quantum", 3), 0,
                new Configuracion.ConfigComputador[] {
                    new Configuracion.ConfigComputador(entero(computador.get("ramCapacity"), "ramCapacity"), politica)
                }, new Configuracion.ConfigBuffer[0], procesos);
        // Geometría de previsualización hasta combinar este documento con diskConfig.
        config.setDisco(new ConfigDisco(Math.max(200, Math.max(swap, velocidad)), 50, swap, velocidad, politicaDisco));
        return config;
    }

    private static ConfigProceso leerProceso(JsonObject entrada) {
        DiskOperation tipo = switch (texto(entrada, "operation")) {
            case "READ" -> DiskOperation.READ;
            case "CREATE" -> DiskOperation.CREATE;
            case "UPDATE" -> DiskOperation.UPDATE;
            case "DELETE" -> DiskOperation.DELETE;
            default -> throw new IllegalArgumentException("operation debe ser CREATE, READ, UPDATE o DELETE.");
        };
        OperacionArchivo operacion = new OperacionArchivo(tipo, texto(entrada, "targetPath"),
                textoOpcional(entrada, "requester"), tipo == DiskOperation.CREATE
                        ? entero(entrada.get("fileSizeBlocks"), "fileSizeBlocks")
                        : enteroOpcional(entrada, "fileSizeBlocks", 0),
                textoOpcional(entrada, "newName"));
        return ConfigProceso.deArchivo(texto(entrada, "name"), enteroOpcional(entrada, "priority", 1),
                entero(entrada.get("memory"), "memory"), entero(entrada.get("instructions"), "instructions"),
                entero(entrada.get("deadline"), "deadline"), operacion);
    }

    private static int enteroOpcional(JsonObject objeto, String campo, int porDefecto) {
        return objeto.has(campo) ? entero(objeto.get(campo), campo) : porDefecto;
    }

    private static String textoOpcional(JsonObject objeto, String campo) {
        return objeto.has(campo) ? texto(objeto, campo) : null;
    }
}
