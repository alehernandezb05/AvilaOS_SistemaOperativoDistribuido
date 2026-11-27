package edu.unimet.avilaos.so.archivos;

import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Permiso;

/**
 * Operación solicitada por un proceso. Sin solicitante, el acceso se limita a archivos públicos.
 * En CREATE, dueño y permiso son opcionales: por defecto la entrada es del solicitante y privada.
 */
public record OperacionArchivo(DiskOperation tipo, String ruta, String solicitante,
                               int bloques, String nuevoNombre, String duenio, Permiso permiso) {

    public OperacionArchivo(DiskOperation tipo, String ruta, String solicitante, int bloques, String nuevoNombre) {
        this(tipo, ruta, solicitante, bloques, nuevoNombre, null, null);
    }

    public OperacionArchivo {
        if (tipo == null || tipo == DiskOperation.SUSPEND || tipo == DiskOperation.REACTIVATE) {
            throw new IllegalArgumentException("La operación de archivo debe ser CREATE, READ, UPDATE o DELETE.");
        }
        SistemaArchivos.validarRuta(ruta);
        if (bloques < 0) throw new IllegalArgumentException("fileSizeBlocks debe ser mayor o igual a 0.");
        if (solicitante != null && solicitante.isBlank()) {
            throw new IllegalArgumentException("requester debe identificar a un usuario.");
        }
        if (nuevoNombre != null && (nuevoNombre.isBlank() || nuevoNombre.contains("/")
                || nuevoNombre.equals(".") || nuevoNombre.equals(".."))) {
            throw new IllegalArgumentException("newName debe ser un nombre de archivo válido.");
        }
        if (duenio != null) {
            duenio = duenio.strip();
            if (!duenio.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("El dueño debe tener letras, números, punto, guion o guion bajo.");
            }
        }
        if ((duenio != null || permiso != null) && tipo != DiskOperation.CREATE) {
            throw new IllegalArgumentException("Dueño y permiso (owner, permissions) solo se indican al crear.");
        }
    }

    /** Dueño con el que nace la entrada en un CREATE. */
    public String duenioEfectivo() { return duenio != null ? duenio : solicitante; }

    /** Permiso con el que nace la entrada en un CREATE. */
    public Permiso permisoEfectivo() { return permiso != null ? permiso : Permiso.PRIVATE; }
}
