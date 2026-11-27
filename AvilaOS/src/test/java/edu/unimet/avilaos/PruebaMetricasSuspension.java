package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Computador;
import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ConfigDisco;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.metricas.CalculadorMetricas;
import edu.unimet.avilaos.metricas.ResumenMetricas;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.MotivoTerminacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import edu.unimet.avilaos.so.TipoProceso;

public final class PruebaMetricasSuspension {
    public static void main(String[] args) {
        comprobarIntercambio(5000, 1, 1);
        comprobarIntercambio(12, 0, 0);
        comprobarIntercambio(115, 1, 0);
        escenarioDemostracion();
        System.out.println("OK: metricas de suspension y CPU; cancelaciones sin conteo falso.");
    }

    private static void comprobarIntercambio(int deadline, long suspensiones, long reactivaciones) {
        Simulador s = new Simulador(100, 0, 0, new int[]{32},
                new PoliticaPlanificacion[]{PoliticaPlanificacion.FCFS},
                new ConfigDisco(200, 50, 100, 1, DiskPolicy.FIFO));
        Computador c = s.getComputador(1);
        PCB a = entradaSalida("A", 30, deadline);
        PCB b = entradaSalida("B", 2000, 5000);
        s.agregarProceso(a);
        s.agregarProceso(b);
        avanzar(c, 1, 8);
        PCB nuevo = new PCB("C", 1, TipoProceso.CPU_BOUND, 1, 16, 5, 5000, 0);
        s.agregarProceso(nuevo);
        avanzar(c, 9, 9);
        ResumenMetricas inicio = CalculadorMetricas.deComputador(c, 9);
        comprobar(inicio.getSuspensiones() == 0 && inicio.getReactivaciones() == 0,
                "Encolar no cuenta como completar.");
        boolean vioSuspendido = false, vioLectura = false;
        for (long ciclo = 10; ciclo <= 300; ciclo++) {
            avanzar(c, ciclo, ciclo);
            ResumenMetricas r = CalculadorMetricas.deComputador(c, ciclo);
            if (a.getEstado() == EstadoProceso.LISTO_SUSPENDIDO
                    || a.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO) {
                vioSuspendido = true;
                comprobar(r.getSuspensiones() == 1 && r.getReactivaciones() == 0,
                        "La escritura completada cuenta una vez; la lectura aun no.");
                PCB[] todos = CalculadorMetricas.procesosDe(c);
                comprobar(todos.length == 3 && contiene(todos, a),
                        "Las metricas incluyen el suspendido sin perder procesos.");
                comprobar(r.getTerminados() == (nuevo.getEstado() == EstadoProceso.TERMINADO ? 1 : 0),
                        "Suspender no termina el proceso.");
                comprobar(Math.abs(r.getRespuestaPromedio() - respuesta(todos)) < 1e-9,
                        "La respuesta incluye procesos suspendidos.");
                for (DiskRequest solicitud : c.getDisco().getPendingRequests()) {
                    if (solicitud.getOperation() == DiskOperation.REACTIVATE) vioLectura = true;
                }
            }
        }
        ResumenMetricas r = CalculadorMetricas.deComputador(c, 300);
        ResumenMetricas global = CalculadorMetricas.delSistema(s,
                CalculadorMetricas.procesosDelSistema(s), 300);
        comprobar(r.getSuspensiones() == suspensiones && r.getReactivaciones() == reactivaciones,
                "Conteos de swap incorrectos para deadline " + deadline);
        comprobar(global.getSuspensiones() == suspensiones && global.getReactivaciones() == reactivaciones,
                "Los totales globales deben coincidir.");
        comprobar(r.getTerminados() == (deadline == 5000 ? 1 : 2) && r.getCompletados() == 1,
                "Los traslados no cuentan como terminaciones ni completados.");
        comprobar(Math.abs(r.getThroughput() * 100 - 100.0 / 300) < 1e-9,
                "Throughput en procesos por 100 ciclos.");
        comprobar(Math.abs(r.getUtilizacion() - 100.0 * c.getCpu().getCiclosOcupada() / 300) < 1e-9,
                "Utilizacion CPU conservada.");
        comprobar(r.getCumplimiento() == (deadline == 5000 ? 100 : 50), "Cumplimiento de deadlines.");
        comprobar(r.getEquidad() > 0 && r.getEquidad() <= 1, "Indice de Jain valido.");
        comprobar(vioSuspendido == (suspensiones > 0) && vioLectura == (suspensiones > 0),
                "Se deben observar los estados y las lecturas esperadas.");
        if (deadline != 5000) comprobar(a.getMotivoTerminacion() == MotivoTerminacion.DEADLINE_VENCIDO,
                "El intercambio cancelado debe terminar por deadline.");
        System.out.println("OK: deadline=" + deadline + ", suspensiones=" + suspensiones
                + ", reactivaciones=" + reactivaciones);
    }

    // Misma carga inicial que escenarios/10_metricas_suspension.json.
    private static void escenarioDemostracion() {
        Simulador s = new Simulador(100, 0, 0, new int[]{32},
                new PoliticaPlanificacion[]{PoliticaPlanificacion.FCFS},
                new ConfigDisco(200, 50, 100, 10, DiskPolicy.FIFO));
        Computador c = s.getComputador(1);
        PCB a = new PCB("A_E/S", 1, TipoProceso.IO_BOUND, 1, 16, 2, 5000, 0);
        a.setCiclosParaES(1);
        a.setDuracionES(30);
        s.agregarProceso(a);
        s.agregarProceso(entradaSalida("B_E/S", 2000, 5000));
        s.agregarProceso(new PCB("C_CPU", 1, TipoProceso.CPU_BOUND, 1, 16, 5, 5000, 0));
        avanzar(c, 1, 150);
        ResumenMetricas r = CalculadorMetricas.deComputador(c, 150);
        comprobar(r.getSuspensiones() == 1 && r.getReactivaciones() == 1
                && r.getCompletados() == 2 && r.getTerminados() == 2,
                "El escenario de demostracion debe completar un intercambio.");
        System.out.println("OK: escenario de demostracion, 1 suspension y 1 reactivacion.");
    }

    private static PCB entradaSalida(String nombre, int espera, int deadline) {
        PCB p = new PCB(nombre, 1, TipoProceso.IO_BOUND, 1, 16, 50, deadline, 0);
        p.setCiclosParaES(1);
        p.setDuracionES(espera);
        return p;
    }

    private static void avanzar(Computador c, long desde, long hasta) {
        for (long ciclo = desde; ciclo <= hasta; ciclo++) {
            c.getDisco().alCiclo(ciclo);
            c.getNucleo().alCiclo(ciclo);
            c.getCpu().alCiclo(ciclo);
        }
    }

    private static boolean contiene(PCB[] procesos, PCB buscado) {
        for (PCB p : procesos) if (p == buscado) return true;
        return false;
    }

    private static double respuesta(PCB[] procesos) {
        long suma = 0;
        int n = 0;
        for (PCB p : procesos) if (p.getCicloPrimeraEjecucion() >= 0) {
            suma += p.getCicloPrimeraEjecucion() - p.getCicloCreacion();
            n++;
        }
        return (double) suma / n;
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
