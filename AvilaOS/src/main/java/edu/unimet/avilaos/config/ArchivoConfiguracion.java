package edu.unimet.avilaos.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Guarda y carga una Configuracion en un archivo JSON usando Gson. */
public final class ArchivoConfiguracion {

    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();

    private ArchivoConfiguracion() {
    }

    /** Valida la configuracion y la escribe en el archivo (lo reemplaza si existe). */
    public static void guardar(Configuracion config, Path archivo)
            throws IOException, ConfiguracionInvalidaException {
        config.validar();
        Files.writeString(archivo, GSON.toJson(config), StandardCharsets.UTF_8);
    }

    /**
     * Lee y valida una configuracion. IOException si no se puede leer el archivo;
     * ConfiguracionInvalidaException si no es un JSON valido o tiene valores fuera de rango.
     */
    public static Configuracion cargar(Path archivo) throws IOException, ConfiguracionInvalidaException {
        String json = Files.readString(archivo, StandardCharsets.UTF_8);
        Configuracion config;
        try {
            var raiz = CamposJson.objeto(GSON.fromJson(json, JsonElement.class), "la carga de procesos");
            config = raiz.has("globalConfig") || raiz.has("computer") || raiz.has("processes")
                    ? ArchivoProcesos.leer(raiz) : GSON.fromJson(raiz, Configuracion.class);
        } catch (JsonParseException e) {
            throw new ConfiguracionInvalidaException("El archivo no tiene un formato de configuracion valido.", e);
        } catch (IllegalArgumentException e) {
            throw new ConfiguracionInvalidaException(e.getMessage(), e);
        }
        config.validar();
        config.conservarCargaProcesos(true);
        return config;
    }
}
