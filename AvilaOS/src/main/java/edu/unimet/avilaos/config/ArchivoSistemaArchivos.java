package edu.unimet.avilaos.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Entrada;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Permiso;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Tipo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArchivoSistemaArchivos {

    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private ArchivoSistemaArchivos() { }

    public static SistemaArchivos cargar(Path archivo) throws IOException, ConfiguracionInvalidaException {
        String json = Files.readString(archivo);
        try {
            JsonObject raiz = objeto(GSON.fromJson(json, JsonElement.class), "el documento");
            JsonObject disco = objeto(raiz.get("diskConfig"), "diskConfig");
            int total = entero(disco.get("totalBlocks"), "diskConfig.totalBlocks");
            int cabezal = entero(disco.get("initialHead"), "diskConfig.initialHead");
            if (total < 1 || total > DiskScheduler.MAX_BLOCKS) {
                throw new IllegalArgumentException("diskConfig.totalBlocks debe estar entre 1 y " + DiskScheduler.MAX_BLOCKS + ".");
            }
            if (cabezal < 0 || cabezal >= total) {
                throw new IllegalArgumentException("diskConfig.initialHead está fuera del disco.");
            }
            JsonElement contenido = raiz.get("fileSystem");
            if (contenido == null || !contenido.isJsonArray()) {
                throw new IllegalArgumentException("fileSystem debe ser un arreglo.");
            }
            var items = contenido.getAsJsonArray();
            Entrada[] entradas = new Entrada[items.size()];
            for (int i = 0; i < entradas.length; i++) {
                try {
                    entradas[i] = leerEntrada(objeto(items.get(i), "la entrada"), total);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("fileSystem[" + i + "]: " + e.getMessage(), e);
                }
            }
            return new SistemaArchivos(total, cabezal, entradas);
        } catch (JsonParseException e) {
            throw new ConfiguracionInvalidaException("El archivo del sistema de archivos no es un JSON válido.", e);
        } catch (IllegalArgumentException e) {
            throw new ConfiguracionInvalidaException(e.getMessage(), e);
        }
    }

    private static Entrada leerEntrada(JsonObject objeto, int bloquesDisco) {
        String ruta = texto(objeto, "path");
        Tipo tipo = switch (texto(objeto, "type")) {
            case "FILE" -> Tipo.FILE;
            case "DIRECTORY" -> Tipo.DIRECTORY;
            default -> throw new IllegalArgumentException("type debe ser FILE o DIRECTORY en " + ruta + ".");
        };
        String duenio = texto(objeto, "owner");
        Permiso permiso = switch (texto(objeto, "permissions")) {
            case "public" -> Permiso.PUBLIC;
            case "private" -> Permiso.PRIVATE;
            default -> throw new IllegalArgumentException("permissions debe ser public o private en " + ruta + ".");
        };
        if (tipo == Tipo.DIRECTORY) {
            if (objeto.has("startBlock") || objeto.has("blocks") || objeto.has("blockChain")) {
                throw new IllegalArgumentException("Un directorio no tiene bloques: " + ruta);
            }
            return new Entrada(ruta, tipo, duenio, permiso, new int[0]);
        }
        return new Entrada(ruta, tipo, duenio, permiso, leerCadena(objeto, ruta, bloquesDisco));
    }

    private static int[] leerCadena(JsonObject objeto, String ruta, int bloquesDisco) {
        int inicio = entero(objeto.get("startBlock"), "startBlock");
        int cantidad = entero(objeto.get("blocks"), "blocks");
        if (cantidad < 1 || cantidad > bloquesDisco) {
            throw new IllegalArgumentException("blocks debe estar entre 1 y " + bloquesDisco + " en " + ruta + ".");
        }
        JsonElement cadena = objeto.get("blockChain");
        if (cadena == null && cantidad == 1) return new int[] {inicio};
        if (cadena == null || !cadena.isJsonArray() || cadena.getAsJsonArray().size() != cantidad) {
            throw new IllegalArgumentException("blockChain debe indicar las " + cantidad + " posiciones de " + ruta + ".");
        }
        int[] posiciones = new int[cantidad];
        for (int i = 0; i < cantidad; i++) {
            posiciones[i] = entero(cadena.getAsJsonArray().get(i), "blockChain[" + i + "]");
        }
        if (posiciones[0] != inicio) {
            throw new IllegalArgumentException("blockChain debe empezar en startBlock en " + ruta + ".");
        }
        return posiciones;
    }

    private static JsonObject objeto(JsonElement valor, String campo) {
        if (valor == null || !valor.isJsonObject()) {
            throw new IllegalArgumentException("Se esperaba un objeto JSON para " + campo + ".");
        }
        return valor.getAsJsonObject();
    }

    private static String texto(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isString()
                && !valor.getAsString().isBlank()) return valor.getAsString();
        throw new IllegalArgumentException(campo + " debe ser un texto no vacío.");
    }

    private static int entero(JsonElement valor, String campo) {
        if (valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isNumber()) {
            try {
                return valor.getAsBigDecimal().intValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                throw new IllegalArgumentException(campo + " debe ser un entero dentro del rango permitido.", e);
            }
        }
        throw new IllegalArgumentException(campo + " debe ser un entero.");
    }
}
