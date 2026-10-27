package edu.unimet.avilaos.metricas;

public class ResumenBuffer {

    private final String id;
    private final long producidos;
    private final long consumidos;
    private final double bloqueoPromedio;
    private final int procesos;

    public ResumenBuffer(String id, long producidos, long consumidos, double bloqueoPromedio, int procesos) {
        this.id = id;
        this.producidos = producidos;
        this.consumidos = consumidos;
        this.bloqueoPromedio = bloqueoPromedio;
        this.procesos = procesos;
    }

    public String getId() { return id; }
    public long getProducidos() { return producidos; }
    public long getConsumidos() { return consumidos; }
    public double getBloqueoPromedio() { return bloqueoPromedio; }
    public int getProcesos() { return procesos; }
}