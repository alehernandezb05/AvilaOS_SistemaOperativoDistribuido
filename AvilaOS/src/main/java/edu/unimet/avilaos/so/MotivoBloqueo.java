package edu.unimet.avilaos.so;

public enum MotivoBloqueo {
    ESPERA_ES,          // proceso I/O bound esperando que termine su E/S
    ESPERA_DISCO,       // espera la finalización de una solicitud real al disco
    ESPERA_SEMAFORO,    // buffer lleno, buffer vacio o region critica ocupada
    ESPERA_RED          // acceso a un buffer remoto (latencia de red)
}
