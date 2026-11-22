package edu.unimet.avilaos.so.archivos;

import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;

/** Operación solicitada por un proceso. Sin solicitante, el acceso se limita a archivos públicos. */
public record OperacionArchivo(DiskOperation tipo, String ruta, String solicitante,
                               int bloques, String nuevoNombre) {

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
    }
}
