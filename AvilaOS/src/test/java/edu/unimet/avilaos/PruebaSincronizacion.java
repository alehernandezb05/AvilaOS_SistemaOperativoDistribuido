package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfigDisco;
import edu.unimet.avilaos.hardware.Reloj;
import edu.unimet.avilaos.hardware.disco.enums.*;
import edu.unimet.avilaos.gui.ModeloTablaPCB;
import edu.unimet.avilaos.metricas.CalculadorMetricas;
import edu.unimet.avilaos.so.*;
import edu.unimet.avilaos.so.archivos.*;
import javax.swing.SwingUtilities;
import java.util.concurrent.Semaphore;

/** Prueba el reloj real, lecturas desde EDT y mutaciones desde otro hilo. */
public final class PruebaSincronizacion {
    public static void main(String[] args) throws Exception {
        interrupcion();
        coherencia(PoliticaPlanificacion.FCFS);
        coherencia(PoliticaPlanificacion.EDF);
        System.out.println("OK: sincronizacion y observacion coherente del sistema.");
    }

    private static void interrupcion() throws Exception {
        Reloj reloj = new Reloj(1);
        Semaphore dentro = new Semaphore(0), salida = new Semaphore(0);
        reloj.registrar(ciclo -> { dentro.release(); salida.acquireUninterruptibly(); });
        reloj.iniciar();
        comprobar(dentro.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS), "El reloj debe iniciar.");
        comprobar(reloj.intentarObservar(reloj::getCicloActual) == null, "No observar medio ciclo.");
        boolean[] conservada = {false};
        Thread pausa = new Thread(() -> {
            Thread.currentThread().interrupt();
            reloj.pausar();
            conservada[0] = Thread.currentThread().isInterrupted();
        });
        pausa.start();
        salida.release();
        // El reloj puede volver a entrar antes que la pausa: liberar un eventual segundo ciclo.
        long limite = System.nanoTime() + 5_000_000_000L;
        while (pausa.isAlive() && System.nanoTime() < limite) { salida.release(); pausa.join(10); }
        comprobar(!pausa.isAlive() && conservada[0], "La pausa debe preservar la interrupcion y terminar.");
        long ciclo = reloj.getCicloActual();
        Thread.sleep(5);
        comprobar(reloj.getCicloActual() == ciclo, "Pausa estable.");
        reloj.detener();
        System.out.println("OK: interrupcion, pausa y observacion sin espera durante ciclo ocupado.");
    }

    private static void coherencia(PoliticaPlanificacion inicial) throws Exception {
        Configuracion cfg = new Configuracion(50, 3, 0,
                new Configuracion.ConfigComputador[]{new Configuracion.ConfigComputador(32, inicial)});
        cfg.setDisco(new ConfigDisco(200, 50, 100, 10, DiskPolicy.FIFO));
        cfg.conservarCargaProcesos(true);
        var sistema = new SistemaArchivos(200, 50, new SistemaArchivos.Entrada[0]);
        Simulador sim = new Simulador(cfg, sistema);
        var c = sim.getComputador(1);
        // FCFS y EDF deben seleccionar primero A, incluso si cambia la politica antes del primer ciclo.
        PCB a = new PCB("A", 1, TipoProceso.IO_BOUND, 1, 16, 2, 2000, 0);
        a.setCiclosParaES(1); a.setDuracionES(30);
        PCB b = new PCB("B", 1, TipoProceso.IO_BOUND, 1, 16, 50, 5000, 0);
        b.setCiclosParaES(1); b.setDuracionES(500);
        sim.agregarProceso(a); sim.agregarProceso(b);
        sim.agregarProceso(new PCB("C", 1, TipoProceso.CPU_BOUND, 1, 16, 5, 5000, 0));
        ModeloTablaPCB tabla = new ModeloTablaPCB();
        SwingUtilities.invokeAndWait(() -> tabla.setProcesos(new PCB[]{a}));
        comprobar(tabla.getValueAt(0,3) == EstadoProceso.NUEVO, "Fila inicial.");
        sim.setDuracionCicloMs(2);
        sim.iniciar();
        try {
            // El cambio de politica de disco puede variar la duracion del intercambio.
            // Esperar la condicion real, con limite de ciclos y verificacion concurrente.
            while (!sim.observar(() -> c.getNucleo().getSuspensionesCompletadas() > 0
                    && c.getNucleo().getReactivacionesCompletadas() > 0)) {
                comprobar(sim.getCicloActual() < 350, "Debe ocurrir un intercambio completo antes de 350 ciclos.");
                esperar(sim, sim.getCicloActual() + 5);
            }
            comprobar(tabla.getValueAt(0,3) == EstadoProceso.NUEVO, "La fila no puede cambiar sin refresco.");
            PCB crear = pedir(sim, DiskOperation.CREATE, "/prueba", 3, null);
            esperarFin(sim, crear);
            PCB leer = pedir(sim, DiskOperation.READ, "/prueba", 0, null);
            esperarFin(sim, leer);
            PCB renombrar = pedir(sim, DiskOperation.UPDATE, "/prueba", 0, "nuevo");
            esperarFin(sim, renombrar);
            PCB eliminar = pedir(sim, DiskOperation.DELETE, "/nuevo", 0, null);
            esperarFin(sim, eliminar);
            sim.pausar();
            long ciclo = sim.getCicloActual();
            Thread.sleep(8);
            comprobar(sim.getCicloActual() == ciclo, "Pausa debe cerrar un ciclo.");
            comprobar(sim.copiaSistemaArchivos().cantidadArchivos() == 0, "CRUD completo.");
            comprobar(c.getDisco().getStorage().getReservedFileBlockCount() == 0, "Sin reservas de archivos huerfanas.");
            comprobar(c.getNucleo().getSuspensionesCompletadas() > 0 && c.getNucleo().getReactivacionesCompletadas() > 0,
                    "Debe ocurrir un intercambio completo.");
            verificar(sim);
            System.out.println("OK: CRUD, cambios de politica, RAM, colas, swap y copia de guardado con reloj activo (inicio " + inicial + ").");
            System.out.println("OK: tabla conserva valores copiados y no referencias PCB vivas.");
        } finally { sim.detener(); }
    }

    private static PCB pedir(Simulador s, DiskOperation op, String ruta, int bloques, String nombre) {
        PCB p = new PCB(op.name(), 1, TipoProceso.IO_BOUND, 1, 1, 1, 5000, s.getCicloActual(),
                new OperacionArchivo(op, ruta, "admin", bloques, nombre));
        s.solicitarProceso(p); return p;
    }

    private static void esperar(Simulador s, long ciclo) throws Exception {
        long limite = System.nanoTime() + 10_000_000_000L;
        int i = 0;
        while (s.getCicloActual() < ciclo) {
            comprobar(System.nanoTime() < limite, "Reloj bloqueado.");
            s.cambiarPoliticaDisco(DiskPolicy.values()[i++ % DiskPolicy.values().length]);
            s.cambiarPolitica(1, i % 2 == 0 ? PoliticaPlanificacion.FCFS : PoliticaPlanificacion.EDF);
            s.getConfiguracion(); s.copiaSistemaArchivos();
            SwingUtilities.invokeAndWait(() -> s.intentarObservar(() -> { verificar(s); return true; }));
            Thread.sleep(2);
        }
    }

    private static void esperarFin(Simulador s, PCB p) throws Exception {
        long limite = s.getCicloActual() + 1000;
        while (s.observar(p::getEstado) != EstadoProceso.TERMINADO) {
            comprobar(s.getCicloActual() < limite, "Operacion no termino: " + p);
            esperar(s, s.getCicloActual() + 5);
        }
        comprobar(p.cumplioDeadline(), "Operacion no completada: " + p);
    }

    private static void verificar(Simulador s) {
        var c = s.getComputador(1);
        var disco = c.getDisco();
        var vista = disco.getQueueView();
        long ciclo = s.getCicloActual();
        comprobar(vista.state().cycle() == ciclo && c.getCpu().getCiclosTotales() == ciclo, "Ciclos mezclados.");
        var colas = c.getNucleo().getColas();
        PCB[] todos = CalculadorMetricas.procesosDe(c);
        PCB[] enColas = colas.copiaDeTodos();
        int ram = 0, reservas = 0;
        var entradas = vista.entries();
        for (int i=0; i<enColas.length; i++) {
            comprobar(enColas[i] != c.getCpu().getProcesoActual(), "PCB en CPU y cola.");
            for (int j=0; j<i; j++) comprobar(enColas[i] != enColas[j], "PCB duplicado en colas.");
        }
        for (PCB p : todos) {
            boolean escritura = false, lectura = false;
            for (var e : entradas) if (e.processId() == p.getId()) {
                escritura |= e.operation() == DiskOperation.SUSPEND;
                lectura |= e.operation() == DiskOperation.REACTIVATE;
                comprobar(p.getEstado() != EstadoProceso.TERMINADO, "Solicitud de PCB terminado.");
            }
            int swap = disco.getStorage().getSwapBlocks(p.getId()).length;
            if (p.getEstado() == EstadoProceso.TERMINADO || p.getEstado() == EstadoProceso.NUEVO) {
                comprobar(swap == 0, "Swap de proceso no residente.");
            } else {
                if (!p.isEnSwap() || lectura) ram += p.getMemoria();
                comprobar(swap == ((p.isEnSwap() || escritura) ? p.getMemoria() : 0), "Propiedad de swap.");
            }
        }
        comprobar(ram == c.getRam().getUsada(), "RAM no coincide con residencia.");
        for (var e : entradas) if (e.operation() == DiskOperation.CREATE) reservas += e.blockCount();
        comprobar(reservas == disco.getStorage().getReservedFileBlockCount(), "Reserva de CREATE huerfana.");
        var archivos = s.getSistemaArchivos().observar(disco.getStorage());
        var bloques = archivos.bloques();
        boolean[] vistos = new boolean[bloques.length];
        for (int i=0; i<archivos.archivos().cantidadArchivos(); i++) {
            var archivo = archivos.archivos().archivo(i);
            int[] cadena = archivo.cadena();
            for (int j=0; j<cadena.length; j++) {
                int n=cadena[j];
                comprobar(!vistos[n] && bloques[n].contentType()==DiskBlockType.FILE
                    && archivo.ruta().equals(bloques[n].filePath()) && bloques[n].nextBlock()==(j+1<cadena.length?cadena[j+1]:-1), "Cadena incoherente.");
                vistos[n]=true;
            }
        }
        for (int i=0; i<bloques.length; i++) {
            comprobar(vistos[i]==(bloques[i].contentType()==DiskBlockType.FILE), "Bloque de archivo huerfano.");
            if (bloques[i].contentType()==DiskBlockType.SWAP_OCCUPIED) {
                boolean existe=false;
                for (PCB p : todos) if(p.getId()==bloques[i].processId() && p.getEstado()!=EstadoProceso.TERMINADO) existe=true;
                comprobar(existe,"Bloque swap huerfano.");
            }
        }
        var r = CalculadorMetricas.deComputador(c, ciclo);
        comprobar(r.getCompletados()<=r.getTerminados() && r.getUtilizacion()>=0 && r.getUtilizacion()<=100, "Metricas incoherentes.");
        if (entradas.length>0) {
            entradas[0]=null;
            comprobar(vista.entries()[0]!=null,"La vista debe proteger su arreglo.");
        }
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
