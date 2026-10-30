package edu.unimet.avilaos.so;

public enum MotivoBloqueo {
    ESPERA_ES,          // proceso I/O bound esperando que termine su E/S
    ESPERA_SEMAFORO,    // buffer lleno, buffer vacio o region critica ocupada
    ESPERA_RED          // acceso a un buffer remoto (latencia de red)
}