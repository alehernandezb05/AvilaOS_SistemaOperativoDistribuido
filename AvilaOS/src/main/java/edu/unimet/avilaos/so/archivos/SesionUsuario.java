package edu.unimet.avilaos.so.archivos;

import edu.unimet.avilaos.so.ModoUsuario;

/**
 * Modo de uso actual y la identidad que firma las operaciones de archivo.
 * Cada proceso guarda el solicitante con el que se creo, asi que cambiar de modo despues no le da permisos nuevos.
 */
public record SesionUsuario(ModoUsuario modo, String usuario) {

    public SesionUsuario {
        if (modo == null) throw new IllegalArgumentException("Falta el modo de uso.");
        if (modo == ModoUsuario.ADMINISTRADOR) {
            usuario = SistemaArchivos.ADMINISTRADOR;
        } else {
            usuario = usuario == null ? "" : usuario.strip();
            if (usuario.isEmpty() || !usuario.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("El usuario debe tener letras, numeros, punto, guion o guion bajo.");
            }
            if (usuario.equals(SistemaArchivos.ADMINISTRADOR)) {
                throw new IllegalArgumentException("\"admin\" esta reservado para el modo administrador.");
            }
        }
    }

    public static SesionUsuario administrador() {
        return new SesionUsuario(ModoUsuario.ADMINISTRADOR, null);
    }

    public boolean esAdministrador() { return modo == ModoUsuario.ADMINISTRADOR; }

    /** Identidad que se guarda en la operacion: "admin" o el nombre del usuario. */
    public String solicitante() { return usuario; }

    @Override
    public String toString() {
        return esAdministrador() ? "administrador" : "usuario " + usuario;
    }
}
