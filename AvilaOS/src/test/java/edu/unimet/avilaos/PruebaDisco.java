package edu.unimet.avilaos;

import com.google.gson.Gson;
import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfigDisco;
import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.DiskPolicies;
import edu.unimet.avilaos.hardware.disco.DiskSnapshot;
import edu.unimet.avilaos.hardware.disco.enums.DiskDirection;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequestStatus;
import edu.unimet.avilaos.hardware.disco.interfaces.IDiskSchedulerPolicy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import edu.unimet.avilaos.hardware.ComponenteReloj;
import edu.unimet.avilaos.hardware.Reloj;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.DiskCompletion;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.policies.FifoDiskSchedulerPolicy;

/** Escenarios de disco ejecutables con Java, sin frameworks adicionales. */
public final class PruebaDisco {

    public static void main(String[] args) throws Exception {
        DiskScheduler disco = new DiskScheduler(200, 50, 0, DiskPolicy.FIFO);
        disco.addRequest(new DiskRequest(1, "Prueba", DiskOperation.READ, 52));
        comprobar(disco instanceof ComponenteReloj, "El disco debe obedecer al reloj global.");
        Reloj reloj = new Reloj(1);
        reloj.registrar((ComponenteReloj) (Object) disco);
        try {
            reloj.iniciar();
            long limite = System.nanoTime() + 2_000_000_000L;
            while (disco.getTotalRequestsServed() == 0 && System.nanoTime() < limite) {
                Thread.sleep(1);
            }
            reloj.pausar();
            comprobar(disco.getTotalRequestsServed() == 1 && disco.getCurrentHead() == 52,
                    "El reloj debe mover y atender la solicitud sin un Timer de Swing.");
        } finally {
            reloj.detener();
        }
        System.out.println("OK: disco gobernado por el reloj global.");
        transferencia();
        politicas();
        inyeccionYCambio();
        validacion();
        pausaCompartida();
        interrupcion();
        configuracion();
    }

    private static void transferencia() {
        DiskScheduler disco = new DiskScheduler(200, 50, 0, 2, new FifoDiskSchedulerPolicy());
        DiskRequest request = new DiskRequest(7, "P7", DiskOperation.READ, new int[] {53, 55});
        comprobar(disco.addRequest(request), "Se debe aceptar la solicitud.");
        disco.alCiclo(1); // 50 -> 52
        disco.alCiclo(1); // Repetir una notificacion no avanza dos veces.
        comprobar(disco.getCurrentHead() == 52, "Un avance de dos posiciones por ciclo, sin duplicados.");
        disco.alCiclo(2); // 52 -> 53
        comprobar(request.getResult() == null, "Llegar al bloque no transfiere en ese mismo ciclo.");
        disco.alCiclo(3); // Transferir 53
        comprobar(request.getResult() == null, "Todavia falta el segundo bloque.");
        disco.alCiclo(4); // 53 -> 55
        disco.alCiclo(5); // Transferir 55
        DiskCompletion result = disco.pollCompleted();
        comprobar(result != null && result.processId() == 7 && result.requestId() == request.getId(),
                "El resultado identifica proceso y solicitud.");
        comprobar(result.operation() == DiskOperation.READ && result.arrivalCycle() == 0
                && result.startCycle() == 1 && result.completionCycle() == 5,
                "Se conservan operacion y ciclos de llegada, seleccion y finalizacion.");
        comprobar(disco.getTotalHeadMovement() == 5 && disco.getTotalRequestsServed() == 1,
                "Cinco posiciones y una solicitud con dos bloques.");
        comprobar(disco.pollCompleted() == null && request.getResult() == result,
                "Una unica notificacion y un resultado consultable.");
        System.out.println("OK: velocidad, transferencia por bloque, identidad y finalizacion unica.");
    }

    private static DiskRequest lectura(int processId, int block) {
        return new DiskRequest(processId, "P" + processId, DiskOperation.READ, block);
    }

    private static void politicas() {
        int[] annex = {82, 17, 43, 140, 24, 16, 190};
        recorrido(DiskPolicy.FIFO, 50, annex, new int[] {1, 2, 3, 4, 5, 6, 7}, 518);
        recorrido(DiskPolicy.SSTF, 50, annex, new int[] {3, 5, 2, 6, 1, 4, 7}, 208);
        int[] blocks = {95, 180, 34, 119, 11, 123, 62, 64};
        recorrido(DiskPolicy.FIFO, 50, blocks, new int[] {1, 2, 3, 4, 5, 6, 7, 8}, 644);
        recorrido(DiskPolicy.SSTF, 50, blocks, new int[] {7, 8, 3, 5, 1, 4, 6, 2}, 236);
        // A igual distancia se atiende primero la solicitud que llego primero.
        recorrido(DiskPolicy.SSTF, 50, new int[] {40, 60, 40}, new int[] {1, 3, 2}, 30);
        System.out.println("OK: FIFO, SSTF y empates por orden de llegada.");
    }

    private static void recorrido(DiskPolicy policy, int head, int[] blocks, int[] order, long movement) {
        DiskScheduler disk = new DiskScheduler(200, head, 0, policy);
        for (int i = 0; i < blocks.length; i++) disk.addRequest(lectura(i + 1, blocks[i]));
        for (int cycle = 1; cycle <= 3000 && disk.getTotalRequestsServed() < blocks.length; cycle++) {
            disk.alCiclo(cycle);
        }
        for (int process : order) {
            DiskCompletion completion = disk.pollCompleted();
            comprobar(completion != null && completion.processId() == process, "Orden incorrecto de " + policy);
        }
        comprobar(disk.getTotalHeadMovement() == movement, "Desplazamiento incorrecto de " + policy
                + ": " + disk.getTotalHeadMovement());
        comprobar(disk.getTotalRequestsServed() == blocks.length && disk.pollCompleted() == null,
                "Cada solicitud debe terminar exactamente una vez.");
    }

    private static void inyeccionYCambio() {
        IDiskSchedulerPolicy lastFirst = new IDiskSchedulerPolicy() {
            @Override public DiskPolicy getPolicy() { return DiskPolicy.FIFO; }
            @Override public DiskRequest selectNextRequest(Lista<DiskRequest> queue, int head, DiskDirection direction) {
                DiskRequest request = queue.obtener(queue.getTamano() - 1);
                queue.remover(request);
                return request;
            }
        };
        DiskScheduler disk = new DiskScheduler(200, 50, 0, 1, lastFirst);
        disk.addRequest(lectura(1, 50));
        DiskRequest active = lectura(2, 52);
        disk.addRequest(active);
        disk.alCiclo(1);
        comprobar(disk.getSnapshot().activeRequestId() == active.getId(), "Se usa la politica inyectada.");
        disk.setPolicy(DiskPolicies.create(DiskPolicy.SSTF));
        disk.alCiclo(2);
        disk.alCiclo(3);
        comprobar(disk.pollCompleted().processId() == 2, "Cambiar politica no interrumpe la solicitud activa.");
        comprobar(active.getResult().policy() == DiskPolicy.FIFO && disk.getServedCount(DiskPolicy.FIFO) == 1,
                "La finalizacion conserva la politica que selecciono la solicitud.");
        for (int cycle = 4; cycle <= 6; cycle++) disk.alCiclo(cycle);
        comprobar(disk.pollCompleted().policy() == DiskPolicy.SSTF, "La nueva politica selecciona la siguiente.");
        System.out.println("OK: estrategia inyectable y cambio sin apropiacion ni doble conteo.");
    }

    private static void validacion() {
        invalido(() -> new DiskScheduler(0, 0, 0, DiskPolicy.FIFO));
        invalido(() -> new DiskScheduler(501, 0, 0, DiskPolicy.FIFO));
        invalido(() -> new DiskScheduler(200, 200, 0, DiskPolicy.FIFO));
        invalido(() -> new DiskScheduler(200, -1, 0, DiskPolicy.FIFO));
        invalido(() -> new DiskScheduler(200, 0, 201, DiskPolicy.FIFO));
        DiskScheduler disk = new DiskScheduler(200, 50, 0, DiskPolicy.FIFO);
        invalido(() -> disk.setSpeed(0));
        invalido(() -> disk.setSpeed(201));
        invalido(() -> disk.addRequest(lectura(1, 200)));
        invalido(() -> lectura(1, -1));
        invalido(() -> new DiskRequest(0, "P", DiskOperation.READ, 0));
        invalido(() -> new DiskRequest(1, "P", DiskOperation.READ, new int[0]));
        int[] blocks = {50};
        DiskRequest request = new DiskRequest(1, "P", DiskOperation.UPDATE, blocks);
        blocks[0] = 199;
        request.getBlockNumbers()[0] = 198;
        disk.alCiclo(4);
        disk.addRequest(request);
        comprobar(request.getArrivalCycle() == 4, "Llegada medida con el reloj del disco.");
        disk.getPendingRequests()[0] = null;
        disk.getBlocks()[50].setOccupied(true);
        comprobar(!disk.getBlocks()[50].isOccupied(), "La vista no puede mutar los bloques.");
        invalido(() -> disk.addRequest(request));
        invalido(() -> new DiskScheduler(200, 0, 0, DiskPolicy.FIFO).addRequest(request));
        disk.alCiclo(5);
        comprobar(disk.pollCompleted().completionCycle() == 5, "Las copias no alteran el recorrido ni la cola.");
        invalido(() -> disk.addRequest(request));
        System.out.println("OK: limites, copias defensivas y rechazo de reenvios.");
    }

    private static void pausaCompartida() throws Exception {
        Simulador sim = new Simulador(Configuracion.porDefecto());
        sim.setDuracionCicloMs(1);
        DiskRequest request = lectura(1, 199);
        sim.getDisco().addRequest(request);
        try {
            sim.iniciar();
            esperar(() -> sim.getDisco().getSnapshot().cycle() >= 3);
            sim.pausar();
            DiskSnapshot paused = sim.getDisco().getSnapshot();
            comprobar(paused.cycle() == sim.getCicloActual()
                    && paused.cycle() == sim.getComputador(1).getCpu().getCiclosTotales(),
                    "Disco y CPU comparten cada ciclo global.");
            Thread.sleep(20);
            comprobar(sim.getDisco().getSnapshot().equals(paused) && !sim.estaCorriendo(),
                    "Pausar devuelve un estado estable, sin avance oculto del disco.");
            sim.iniciar();
            esperar(() -> request.getResult() != null);
            sim.pausar();
            comprobar(sim.getDisco().pollCompleted().requestId() == request.getId()
                    && sim.getDisco().pollCompleted() == null && sim.getDisco().getTotalRequestsServed() == 1,
                    "Reanudar no pierde ni duplica solicitudes.");
        } finally {
            sim.detener();
        }
        System.out.println("OK: reloj compartido con CPU, pausa estable y reanudacion.");
    }

    private static void interrupcion() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        IDiskSchedulerPolicy blocking = new IDiskSchedulerPolicy() {
            @Override public DiskPolicy getPolicy() { return DiskPolicy.FIFO; }
            @Override public DiskRequest selectNextRequest(Lista<DiskRequest> queue, int head, DiskDirection direction) {
                entered.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("No se libero la politica.");
                } catch (InterruptedException e) { throw new AssertionError(e); }
                return new FifoDiskSchedulerPolicy().selectNextRequest(queue, head, direction);
            }
        };
        DiskScheduler disk = new DiskScheduler(200, 50, 0, 1, blocking);
        disk.addRequest(lectura(1, 50));
        Thread cycle = new Thread(() -> {
            try { disk.alCiclo(1); } catch (Throwable error) { failure.set(error); }
        });
        DiskRequest cancelled = lectura(2, 60);
        Thread producer = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                comprobar(!disk.addRequest(cancelled) && Thread.currentThread().isInterrupted(),
                        "Acquire interrumpido no debe aceptar ni borrar la interrupcion.");
            } catch (Throwable error) { failure.set(error); }
        });
        CountDownLatch observed = new CountDownLatch(1);
        CountDownLatch observing = new CountDownLatch(1);
        Thread observer = new Thread(() -> { observing.countDown(); disk.getSnapshot(); observed.countDown(); });
        cycle.start();
        try {
            comprobar(entered.await(1, TimeUnit.SECONDS), "La politica debe retener el semaforo.");
            producer.start();
            producer.join(1000);
            comprobar(!producer.isAlive(), "La interrupcion debe devolver el control.");
            observer.start();
            comprobar(observing.await(1, TimeUnit.SECONDS), "El observador debe arrancar.");
            comprobar(!observed.await(30, TimeUnit.MILLISECONDS),
                    "Un acquire interrumpido no puede liberar el permiso del hilo que ejecuta el ciclo.");
        } finally {
            release.countDown();
            cycle.join(2000);
            observer.join(2000);
        }
        if (failure.get() != null) throw new AssertionError(failure.get());
        comprobar(!cycle.isAlive() && !observer.isAlive(), "No quedan hilos bloqueados.");
        comprobar(cancelled.getStatus() == DiskRequestStatus.NEW && disk.getTotalRequestsServed() == 1,
                "La solicitud interrumpida sigue disponible para reintentar.");
        comprobar(disk.addRequest(cancelled), "El semaforo sigue utilizable.");
        System.out.println("OK: acquire interrumpido no libera permisos ajenos ni pierde solicitudes.");
    }

    private static void configuracion() throws Exception {
        Gson gson = new Gson();
        Configuracion old = gson.fromJson(gson.toJson(Configuracion.porDefecto()), Configuracion.class);
        old.validar();
        comprobar(old.getDisco().getTotalBlocks() == 200, "Compatibilidad con JSON sin objeto disco.");
        old.setDisco(new ConfigDisco(120, 10, 20, 4, DiskPolicy.SSTF));
        old.validar();
        Simulador sim = new Simulador(gson.fromJson(gson.toJson(old), Configuracion.class));
        sim.getDisco().addRequest(lectura(1, 14));
        sim.getDisco().alCiclo(1);
        ConfigDisco saved = sim.getConfiguracion().getDisco();
        comprobar(saved.getTotalBlocks() == 120 && saved.getInitialHead() == 10
                && saved.getHeadSpeed() == 4 && saved.getInitialPolicy() == DiskPolicy.SSTF,
                "Guardar conserva geometria y parametros iniciales, sin confundirlos con un checkpoint.");
        old.setDisco(new ConfigDisco(10, 10, 0, 1, DiskPolicy.FIFO));
        try { old.validar(); throw new AssertionError("Debe rechazar el cabezal fuera del disco."); }
        catch (edu.unimet.avilaos.config.ConfiguracionInvalidaException expected) { }
        System.out.println("OK: JSON anterior compatible y parametros de disco conservados.");
    }

    private static void invalido(Runnable action) {
        try { action.run(); throw new AssertionError("Se esperaba rechazar la entrada invalida."); }
        catch (IllegalArgumentException expected) { }
    }

    private static void esperar(java.util.function.BooleanSupplier condition) throws Exception {
        long limit = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < limit) Thread.sleep(1);
        comprobar(condition.getAsBoolean(), "Se agoto la espera del reloj.");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
