package edu.unimet.avilaos.so;

public enum PoliticaPlanificacion {
    FCFS("FCFS"),
    ROUND_ROBIN("Round Robin"),
    EDF("EDF"),
    PRIORIDADES_APROPIATIVAS("Prioridades apropiativas");

    private final String nombre;

    PoliticaPlanificacion(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}