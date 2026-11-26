package edu.unimet.avilaos.metricas;

public class ResumenMetricas {

    private final String nombre;
    private final int terminados;
    private final int completados;
    private final double throughput;
    private final double utilizacion;
    private final double respuestaPromedio;
    private final double cumplimiento;
    private final double equidad;
    private final long suspensiones;
    private final long reactivaciones;

    public ResumenMetricas(String nombre, int terminados, int completados, double throughput,
                           double utilizacion, double respuestaPromedio, double cumplimiento,
                           double equidad, long suspensiones, long reactivaciones) {
        this.nombre = nombre;
        this.terminados = terminados;
        this.completados = completados;
        this.throughput = throughput;
        this.utilizacion = utilizacion;
        this.respuestaPromedio = respuestaPromedio;
        this.cumplimiento = cumplimiento;
        this.equidad = equidad;
        this.suspensiones = suspensiones;
        this.reactivaciones = reactivaciones;
    }

    public String getNombre() { return nombre; }
    public int getTerminados() { return terminados; }
    public int getCompletados() { return completados; }
    /** Procesos completados por ciclo; la GUI lo expresa por 100 ciclos. */
    public double getThroughput() { return throughput; }
    public double getUtilizacion() { return utilizacion; }
    public double getRespuestaPromedio() { return respuestaPromedio; }
    public double getCumplimiento() { return cumplimiento; }
    public double getEquidad() { return equidad; }
    public long getSuspensiones() { return suspensiones; }
    public long getReactivaciones() { return reactivaciones; }
}