package edu.unimet.avilaos.metricas;

import edu.unimet.avilaos.cluster.Computador;
import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.so.Buffer;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.PCB;

public final class CalculadorMetricas {

    private CalculadorMetricas() {
    }

    public static PCB[] procesosDe(Computador computador) {
        PCB[] enColas = computador.getNucleo().getColas().copiaDeTodos();
        PCB enCpu = computador.getCpu().getProcesoActual();
        if (enCpu == null || contiene(enColas, enCpu)) {
            return enColas;
        }
        PCB[] todos = new PCB[enColas.length + 1];
        for (int i = 0; i < enColas.length; i++) {
            todos[i] = enColas[i];
        }
        todos[enColas.length] = enCpu;
        return todos;
    }

    public static PCB[] procesosDelSistema(Simulador simulador) {
        int cantidad = simulador.getCantidadComputadores();
        PCB[][] partes = new PCB[cantidad][];
        int total = 0;
        for (int i = 0; i < cantidad; i++) {
            partes[i] = procesosDe(simulador.getComputador(i + 1));
            total += partes[i].length;
        }
        PCB[] todos = new PCB[total];
        int k = 0;
        for (PCB[] parte : partes) {
            for (PCB p : parte) {
                todos[k++] = p;
            }
        }
        return todos;
    }

    public static ResumenMetricas deComputador(Computador computador, long ciclo) {
        CPU cpu = computador.getCpu();
        return calcular("Computador " + computador.getId(), procesosDe(computador), ciclo,
                cpu.getCiclosOcupada(), cpu.getCiclosTotales());
    }

    public static ResumenMetricas delSistema(Simulador simulador, PCB[] todos, long ciclo) {
        long ocupada = 0;
        long totales = 0;
        for (int i = 1; i <= simulador.getCantidadComputadores(); i++) {
            CPU cpu = simulador.getComputador(i).getCpu();
            ocupada += cpu.getCiclosOcupada();
            totales += cpu.getCiclosTotales();
        }
        return calcular("Sistema", todos, ciclo, ocupada, totales);
    }

    public static ResumenBuffer deBuffer(Buffer buffer, PCB[] todos) {
        long suma = 0;
        int usuarios = 0;
        for (PCB p : todos) {
            if (p.esProductorConsumidor() && buffer.getId().equals(p.getIdBuffer())) {
                suma += p.getCiclosBloqueadoSemaforo();
                usuarios++;
            }
        }
        double promedio = usuarios > 0 ? (double) suma / usuarios : Double.NaN;
        return new ResumenBuffer(buffer.getId(), buffer.getProducidos(), buffer.getConsumidos(),
                promedio, usuarios);
    }

    public static double bloqueoPromedioSemaforos(PCB[] todos) {
        long suma = 0;
        int usuarios = 0;
        for (PCB p : todos) {
            if (p.esProductorConsumidor()) {
                suma += p.getCiclosBloqueadoSemaforo();
                usuarios++;
            }
        }
        return usuarios > 0 ? (double) suma / usuarios : Double.NaN;
    }

    private static ResumenMetricas calcular(String nombre, PCB[] procesos, long ciclo,
                                            long ocupada, long totales) {
        int terminados = 0;
        int completados = 0;
        long sumaRespuesta = 0;
        int conRespuesta = 0;
        double sumaX = 0;
        double sumaX2 = 0;
        int n = 0;
        for (PCB p : procesos) {
            EstadoProceso estado = p.getEstado();
            if (estado == EstadoProceso.TERMINADO) {
                terminados++;
                if (p.cumplioDeadline()) {
                    completados++;
                }
            }
            if (p.getCicloPrimeraEjecucion() >= 0) {
                sumaRespuesta += p.getCicloPrimeraEjecucion() - p.getCicloCreacion();
                conRespuesta++;
            }
            if (estado != EstadoProceso.NUEVO) {
                long fin = estado == EstadoProceso.TERMINADO ? p.getCicloFin() : ciclo;
                long vida = fin - p.getCicloCreacion();
                if (vida > 0) {
                    double x = (double) p.getProgramCounter() / vida;
                    sumaX += x;
                    sumaX2 += x * x;
                    n++;
                }
            }
        }
        double throughput = ciclo > 0 ? (double) completados / ciclo : 0;
        double utilizacion = totales > 0 ? 100.0 * ocupada / totales : 0;
        double respuesta = conRespuesta > 0 ? (double) sumaRespuesta / conRespuesta : Double.NaN;
        double cumplimiento = terminados > 0 ? 100.0 * completados / terminados : Double.NaN;
        double equidad = (n > 0 && sumaX2 > 0) ? (sumaX * sumaX) / (n * sumaX2) : Double.NaN;
        return new ResumenMetricas(nombre, terminados, completados, throughput, utilizacion,
                respuesta, cumplimiento, equidad);
    }

    private static boolean contiene(PCB[] procesos, PCB buscado) {
        for (PCB p : procesos) {
            if (p == buscado) {
                return true;
            }
        }
        return false;
    }
}