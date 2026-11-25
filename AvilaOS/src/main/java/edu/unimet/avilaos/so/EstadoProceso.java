package edu.unimet.avilaos.so;

/** Modelo de siete estados: los dos suspendidos tienen su imagen en el swap del disco. */
public enum EstadoProceso {
    NUEVO,
    LISTO,
    EJECUCION,
    BLOQUEADO,
    LISTO_SUSPENDIDO,
    BLOQUEADO_SUSPENDIDO,
    TERMINADO
}
