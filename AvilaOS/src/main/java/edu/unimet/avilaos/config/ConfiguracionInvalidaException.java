package edu.unimet.avilaos.config;

/** La configuracion tiene valores fuera de rango o el archivo no se pudo interpretar. */
public class ConfiguracionInvalidaException extends Exception {

    public ConfiguracionInvalidaException(String mensaje) {
        super(mensaje);
    }

    public ConfiguracionInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
