package edu.unimet.avilaos;

import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.hardware.RAM;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequestStatus;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.MotivoTerminacion;
import edu.unimet.avilaos.so.NucleoSO;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.so.politicas.FCFS;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;

public final class PruebaDeadlines {

public static void main(String[] args) {
    vencimientoDuranteSwapOut();
    vencimientoDuranteSwapIn();
    vencimientoEnColaDisco();
    vencimientoSuspendido(30, EstadoProceso.LISTO_SUSPENDIDO);
    vencimientoSuspendido(500, EstadoProceso.BLOQUEADO_SUSPENDIDO);
    verificarMetricas();
}

    private static void vencimientoDuranteSwapOut() {
        CPU cpu = new CPU();
        RAM ram = new RAM(32);

        DiskScheduler disco = new DiskScheduler(
                200, 50, 100, DiskPolicy.FIFO);

        NucleoSO nucleo = new NucleoSO(
                1, cpu, ram, new FCFS(), disco, null);

        PCB a = new PCB("A", 1, TipoProceso.IO_BOUND,
                1, 16, 50, 12, 0);

        PCB b = new PCB("B", 1, TipoProceso.IO_BOUND,
                1, 16, 50, 5000, 0);

        a.setCiclosParaES(1);
        a.setDuracionES(500);

        b.setCiclosParaES(1);
        b.setDuracionES(500);

        nucleo.agregarProceso(a);
        nucleo.agregarProceso(b);

        avanzar(cpu, nucleo, disco, 1, 8);

        PCB c = new PCB("C", 1, TipoProceso.CPU_BOUND,
                1, 16, 500, 5000, 0);

        nucleo.agregarProceso(c);

        avanzar(cpu, nucleo, disco, 9, 9);

        DiskRequest[] pendientes = disco.getPendingRequests();

        comprobar(pendientes.length == 1,
                "Debe existir una solicitud de suspension.");

        DiskRequest solicitud = pendientes[0];

        comprobar(solicitud.getOperation() == DiskOperation.SUSPEND
                && solicitud.getProcessId() == a.getId(),
                "La solicitud debe suspender a A.");

        avanzar(cpu, nucleo, disco, 10, 10);

        comprobar(solicitud.getStatus() == DiskRequestStatus.ACTIVE,
                "La escritura al swap debe estar activa.");

        avanzar(cpu, nucleo, disco, 11, 20);

        comprobar(a.getEstado() == EstadoProceso.TERMINADO,
                "A debe terminar por deadline.");

        comprobar(a.getMotivoTerminacion()
                == MotivoTerminacion.DEADLINE_VENCIDO,
                "El motivo debe ser deadline vencido.");

        comprobar(solicitud.getStatus() == DiskRequestStatus.CANCELLED,
                "La solicitud de swap debe cancelarse.");

        comprobar(disco.getStorage().getSwapBlocks(a.getId()).length == 0,
                "Los bloques de swap de A deben liberarse.");

        comprobar(ram.getUsada() == 32,
                "La RAM de B y C debe conservarse.");

        avanzar(cpu, nucleo, disco, 21, 200);

        comprobar(a.getEstado() == EstadoProceso.TERMINADO,
                "A no debe revivir.");

        comprobar(ram.getUsada() == 32,
                "No debe liberarse RAM de otros procesos.");

        comprobar(disco.getTotalRequestsServed() == 0,
                "Una solicitud cancelada no cuenta como completada.");

        System.out.println(
                "OK: deadline durante swap-out, cancelacion y recursos.");
    }
        private static void vencimientoDuranteSwapIn() {
        CPU cpu = new CPU();
        RAM ram = new RAM(32);

        DiskScheduler disco = new DiskScheduler(
                200, 50, 100, DiskPolicy.FIFO);

        NucleoSO nucleo = new NucleoSO(
                1, cpu, ram, new FCFS(), disco, null);

        PCB a = new PCB("A", 1, TipoProceso.IO_BOUND,
                1, 16, 50, 115, 0);

        PCB b = new PCB("B", 1, TipoProceso.IO_BOUND,
                1, 16, 50, 5000, 0);

        a.setCiclosParaES(1);
        a.setDuracionES(30);

        b.setCiclosParaES(1);
        b.setDuracionES(2000);

        nucleo.agregarProceso(a);
        nucleo.agregarProceso(b);

        avanzar(cpu, nucleo, disco, 1, 8);

        PCB c = new PCB("C", 1, TipoProceso.CPU_BOUND,
                1, 16, 5, 5000, 0);

        nucleo.agregarProceso(c);

        DiskRequest lectura = null;
        long ciclo = 9;

        while (lectura == null && ciclo < 115) {
            avanzar(cpu, nucleo, disco, ciclo, ciclo);
            ciclo++;

            for (DiskRequest solicitud : disco.getPendingRequests()) {
                if (solicitud.getOperation() == DiskOperation.REACTIVATE
                        && solicitud.getProcessId() == a.getId()) {
                    lectura = solicitud;
                }
            }
        }

        comprobar(lectura != null,
                "Debe existir una solicitud de reactivacion.");

        avanzar(cpu, nucleo, disco, ciclo, ciclo);
        ciclo++;

        comprobar(lectura.getStatus() == DiskRequestStatus.ACTIVE,
                "La lectura del swap debe estar activa.");

        comprobar(ram.getUsada() == 32,
                "A debe tener RAM reservada durante swap-in.");

        avanzar(cpu, nucleo, disco, ciclo, 200);

        comprobar(a.getEstado() == EstadoProceso.TERMINADO,
                "A debe terminar sin regresar a Listo.");

        comprobar(a.getMotivoTerminacion()
                == MotivoTerminacion.DEADLINE_VENCIDO,
                "A debe terminar por deadline.");

        comprobar(lectura.getStatus() == DiskRequestStatus.CANCELLED,
                "La reactivacion debe cancelarse.");

        comprobar(disco.getStorage().getSwapBlocks(a.getId()).length == 0,
                "El swap de A debe liberarse.");

        comprobar(ram.getUsada() == 16,
                "Solo debe permanecer ocupada la RAM de B.");

        comprobar(c.cumplioDeadline(),
                "C debe conservar su finalizacion correcta.");

        comprobar(disco.getTotalRequestsServed() == 1,
                "Solo debe contar la escritura al swap completada.");

        System.out.println(
                "OK: deadline durante swap-in y liberacion de recursos.");
    }
        private static void vencimientoEnColaDisco() {
        CPU cpu = new CPU();
        RAM ram = new RAM(32);

        DiskScheduler disco = new DiskScheduler(
                10, 9, 0, DiskPolicy.FIFO);

        SistemaArchivos archivos = new SistemaArchivos(
                10, 9, new SistemaArchivos.Entrada[0]);

        NucleoSO nucleo = new NucleoSO(
                1, cpu, ram, new FCFS(), disco, archivos);

        PCB a = new PCB("A", 1, TipoProceso.IO_BOUND,
                1, 1, 1, 100, 0,
                new OperacionArchivo(DiskOperation.CREATE,
                        "/primero", "admin", 6, null));

        PCB b = new PCB("B", 1, TipoProceso.IO_BOUND,
                1, 1, 1, 5, 0,
                new OperacionArchivo(DiskOperation.CREATE,
                        "/vencido", "admin", 4, null));

        nucleo.agregarProceso(a);
        nucleo.agregarProceso(b);

        avanzar(cpu, nucleo, disco, 1, 3);

        DiskRequest[] pendientes = disco.getPendingRequests();

        comprobar(pendientes.length == 1,
                "B debe estar esperando en la cola del disco.");

        DiskRequest solicitud = pendientes[0];

        comprobar(solicitud.getProcessId() == b.getId(),
                "La solicitud pendiente debe pertenecer a B.");

        comprobar(solicitud.getStatus() == DiskRequestStatus.QUEUED,
                "B todavia no debe estar siendo atendido.");

        avanzar(cpu, nucleo, disco, 4, 8);

        comprobar(b.getMotivoTerminacion()
                == MotivoTerminacion.DEADLINE_VENCIDO,
                "B debe vencer mientras espera al disco.");

        comprobar(solicitud.getStatus() == DiskRequestStatus.CANCELLED,
                "La solicitud pendiente debe cancelarse.");

        comprobar(disco.getPendingRequests().length == 0,
                "La solicitud cancelada debe salir de la cola.");

        comprobar(ram.getUsada() == 1,
                "A debe conservar su memoria.");

        PCB c = new PCB("C", 1, TipoProceso.IO_BOUND,
                1, 1, 1, 100, 8,
                new OperacionArchivo(DiskOperation.CREATE,
                        "/reutilizado", "admin", 4, null));

        nucleo.agregarProceso(c);

        avanzar(cpu, nucleo, disco, 9, 50);

        comprobar(archivos.buscar("/vencido") == null,
                "El archivo cancelado nunca debe aparecer.");

        comprobar(a.cumplioDeadline() && c.cumplioDeadline(),
                "A y C deben completar correctamente.");

        comprobar(archivos.buscar("/reutilizado") != null,
                "C debe reutilizar los bloques liberados.");

        comprobar(disco.getTotalRequestsServed() == 2,
                "Solo deben contar las solicitudes completadas.");

        comprobar(ram.getUsada() == 0,
                "La RAM debe quedar libre.");

        System.out.println(
                "OK: deadline en cola de disco y recuperacion de reservas.");
    }
        private static void vencimientoSuspendido(
            int duracionES, EstadoProceso estadoEsperado) {

        CPU cpu = new CPU();
        RAM ram = new RAM(32);

        DiskScheduler disco = new DiskScheduler(
                200, 50, 100, DiskPolicy.FIFO);

        NucleoSO nucleo = new NucleoSO(
                1, cpu, ram, new FCFS(), disco, null);

        PCB a = new PCB("A", 1, TipoProceso.IO_BOUND,
                1, 16, 50, 150, 0);

        PCB b = new PCB("B", 1, TipoProceso.IO_BOUND,
                1, 16, 50, 5000, 0);

        a.setCiclosParaES(1);
        a.setDuracionES(duracionES);

        b.setCiclosParaES(1);
        b.setDuracionES(2000);

        nucleo.agregarProceso(a);
        nucleo.agregarProceso(b);

        avanzar(cpu, nucleo, disco, 1, 8);

        PCB c = new PCB("C", 1, TipoProceso.CPU_BOUND,
                1, 16, 500, 5000, 0);

        nucleo.agregarProceso(c);

        avanzar(cpu, nucleo, disco, 9, 100);

        comprobar(a.getEstado() == estadoEsperado,
                "A debe encontrarse en el estado suspendido esperado.");

        comprobar(a.isEnSwap(),
                "A debe estar almacenado en swap.");

        comprobar(ram.getUsada() == 32,
                "B y C deben ocupar toda la RAM.");

        avanzar(cpu, nucleo, disco, 101, 200);

        comprobar(a.getEstado() == EstadoProceso.TERMINADO,
                "A debe terminar mientras esta suspendido.");

        comprobar(a.getMotivoTerminacion()
                == MotivoTerminacion.DEADLINE_VENCIDO,
                "A debe terminar por deadline.");

        comprobar(disco.getStorage()
                .getSwapBlocks(a.getId()).length == 0,
                "Los bloques de swap deben liberarse.");

        comprobar(ram.getUsada() == 32,
                "La RAM de B y C debe mantenerse intacta.");

        comprobar(nucleo.getColas()
                .cantidadListosSuspendidos() == 0,
                "No deben quedar listos suspendidos.");

        comprobar(nucleo.getColas()
                .cantidadBloqueadosSuspendidos() == 0,
                "No deben quedar bloqueados suspendidos.");

        System.out.println(
                "OK: deadline en estado " + estadoEsperado);
    }
        private static void verificarMetricas() {
        DiskScheduler disco = new DiskScheduler(
                10, 9, 0, DiskPolicy.FIFO);

        SistemaArchivos archivos = new SistemaArchivos(
                10, 9, new SistemaArchivos.Entrada[0]);

        var reloj = new edu.unimet.avilaos.hardware.Reloj(300);

        var computador = new edu.unimet.avilaos.cluster.Computador(
                1, 32, new FCFS(), reloj, disco, archivos);

        CPU cpu = computador.getCpu();
        NucleoSO nucleo = computador.getNucleo();

        PCB vencido = new PCB("Vencido", 1,
                TipoProceso.IO_BOUND, 1, 1, 1, 2, 0,
                new OperacionArchivo(DiskOperation.CREATE,
                        "/tarde", "admin", 0, null));

        nucleo.agregarProceso(vencido);

        avanzar(cpu, nucleo, disco, 1, 10);

        comprobar(vencido.getMotivoTerminacion()
                == MotivoTerminacion.DEADLINE_VENCIDO,
                "El proceso debe vencer.");

        comprobar(archivos.buscar("/tarde") == null,
                "Una finalizacion tardia no debe crear el directorio.");

        PCB correcto = new PCB("Correcto", 1,
                TipoProceso.CPU_BOUND, 1, 1, 2, 100, 10);

        PCB rechazado = new PCB("Rechazado", 1,
                TipoProceso.IO_BOUND, 1, 1, 1, 100, 10,
                new OperacionArchivo(DiskOperation.READ,
                        "/inexistente", "admin", 0, null));

        nucleo.agregarProceso(correcto);
        nucleo.agregarProceso(rechazado);

        avanzar(cpu, nucleo, disco, 11, 200);

        comprobar(correcto.cumplioDeadline(),
                "El proceso correcto conserva su resultado.");

        comprobar(rechazado.getMotivoTerminacion()
                == MotivoTerminacion.ERROR_ES,
                "Un error de E/S no es un deadline vencido.");

        var metricas = edu.unimet.avilaos.metricas.CalculadorMetricas
                .deComputador(computador, 200);

        comprobar(metricas.getTerminados() == 3,
                "Deben existir tres procesos terminados.");

        comprobar(metricas.getCompletados() == 1,
                "Solo uno debe contar como completado.");

        comprobar(metricas.getCumplimiento() == 50.0,
                "El error de E/S no debe alterar el cumplimiento.");

        comprobar(computador.getRam().getUsada() == 0,
                "Toda la RAM debe quedar libre.");

        System.out.println(
                "OK: metricas y finalizaciones tardias.");
    }
        private static void avanzar(CPU cpu, NucleoSO nucleo,
            DiskScheduler disco, long desde, long hasta) {

        for (long ciclo = desde; ciclo <= hasta; ciclo++) {
            disco.alCiclo(ciclo);
            nucleo.alCiclo(ciclo);
            cpu.alCiclo(ciclo);
        }
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}