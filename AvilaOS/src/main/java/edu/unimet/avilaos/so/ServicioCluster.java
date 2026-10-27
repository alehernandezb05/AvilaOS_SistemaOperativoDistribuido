package edu.unimet.avilaos.so;

public interface ServicioCluster {

    Buffer getBuffer(String id);

    int getLatenciaRed();

    void desbloquear(PCB proceso, long ciclo);
}