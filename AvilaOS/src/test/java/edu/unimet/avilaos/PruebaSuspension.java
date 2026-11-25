package edu.unimet.avilaos;

import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.hardware.RAM;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.enums.DiskBlockType;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.so.ColasSO;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.Log;
import edu.unimet.avilaos.so.MotivoBloqueo;
import edu.unimet.avilaos.so.NucleoSO;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.so.politicas.FCFS;

/** Planificador a mediano plazo: escritura de Bloqueados al swap para admitir Nuevos. */
public final class PruebaSuspension {

    private PruebaSuspension() { }

    public static void main(String[] args) {
        ejecutar();
    }

    static void ejecutar() {
        sieteEstados();
        suspendeAlBloqueadoMasAntiguo();
        eventoDuranteLaEscritura();
        sinVictimaNiSwap();
    }

    /** Un computador de 32 de RAM con disco de 200 bloques y 100 de swap; el cabezal empieza en 50. */
    private record Escenario(CPU cpu, RAM ram, DiskScheduler disco, NucleoSO nucleo, Log log) {
        static Escenario nuevo(int ram, int swap) {
            CPU cpu = new CPU();
            RAM memoria = new RAM(ram);
            DiskScheduler disco = new DiskScheduler(200, 50, swap, DiskPolicy.FIFO);
            NucleoSO nucleo = new NucleoSO(1, cpu, memoria, new FCFS(), disco, null);
            Log log = new Log();
            nucleo.setLog(log);
            disco.setLog(log);
            return new Escenario(cpu, memoria, disco, nucleo, log);
        }

        void avanzar(long desde, long hasta) {
            for (long c = desde; c <= hasta; c++) {
                disco.alCiclo(c);
                nucleo.alCiclo(c);
                cpu.alCiclo(c);
            }
        }

        ColasSO colas() { return nucleo.getColas(); }

        String registro() { return String.join("\n", log.obtenerDesde(0)); }

        int bloquesSwapDe(int idProceso) {
            int n = 0;
            for (DiskBlock b : disco.getBlocks()) {
                if (b.contentType() == DiskBlockType.SWAP_OCCUPIED && b.processId() == idProceso) n++;
            }
            return n;
        }
    }

    private static PCB conES(String nombre, int duracionES) {
        PCB p = new PCB(nombre, 1, TipoProceso.IO_BOUND, 1, 16, 50, 5000, 0);
        p.setCiclosParaES(1);
        p.setDuracionES(duracionES);
        return p;
    }

    private static PCB cpu(String nombre) {
        return new PCB(nombre, 1, TipoProceso.CPU_BOUND, 1, 16, 50, 5000, 0);
    }

    private static void sieteEstados() {
        comprobar(EstadoProceso.values().length == 7
                && EstadoProceso.valueOf("LISTO_SUSPENDIDO") != null
                && EstadoProceso.valueOf("BLOQUEADO_SUSPENDIDO") != null,
                "El modelo tiene exactamente siete estados.");
        System.out.println("OK: modelo de siete estados.");
    }

    private static void suspendeAlBloqueadoMasAntiguo() {
        Escenario e = Escenario.nuevo(32, 100);
        PCB primero = conES("A", 500);
        PCB segundo = conES("B", 500);
        e.nucleo().agregarProceso(primero);
        e.nucleo().agregarProceso(segundo);
        e.avanzar(1, 8);
        comprobar(primero.getEstado() == EstadoProceso.BLOQUEADO && segundo.getEstado() == EstadoProceso.BLOQUEADO
                && e.ram().getLibre() == 0, "Los dos procesos de E/S ocupan toda la RAM y quedan bloqueados.");

        PCB nuevo = cpu("C");
        e.nucleo().agregarProceso(nuevo);
        e.avanzar(9, 9);
        comprobar(nuevo.getEstado() == EstadoProceso.NUEVO && e.ram().getLibre() == 0
                && e.bloquesSwapDe(primero.getId()) == 16 && e.bloquesSwapDe(segundo.getId()) == 0,
                "Se reserva swap para el que mas lleva bloqueado sin liberar todavia su RAM.");
        comprobar(e.disco().getPendingRequests().length == 1
                && e.disco().getPendingRequests()[0].getOperation() == DiskOperation.SUSPEND,
                "La escritura al swap usa la misma cola del disco.");

        e.avanzar(10, 40);
        comprobar(contar(e.registro(), "suspende ") == 1 && primero.getEstado() == EstadoProceso.BLOQUEADO,
                "Mientras se escribe no se elige otra victima ni se cambia el estado.");

        e.avanzar(41, 100);
        comprobar(primero.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO
                && primero.getMotivoBloqueo() == MotivoBloqueo.ESPERA_ES && primero.isEnSwap()
                && e.colas().cantidadBloqueadosSuspendidos() == 1,
                "Al terminar la escritura pasa a Bloqueado/Suspendido y conserva su motivo.");
        comprobar(nuevo.getEstado() != EstadoProceso.NUEVO && e.ram().getUsada() == 32
                && segundo.getEstado() == EstadoProceso.BLOQUEADO,
                "La RAM liberada una sola vez admite al Nuevo.");
        String log = e.registro();
        comprobar(log.contains("suspende " + primero) && log.contains("para admitir " + nuevo)
                && log.contains("16 bloques al swap") && log.contains("(bloqueado -> bloqueado/suspendido)"),
                "El registro indica victima, motivo, bloques y proceso que se intenta admitir.");
        System.out.println("OK: suspende al bloqueado mas antiguo, escribe al swap y libera RAM al terminar.");
    }

    private static void eventoDuranteLaEscritura() {
        Escenario e = Escenario.nuevo(32, 100);
        PCB corto = conES("Corto", 30);
        PCB largo = conES("Largo", 500);
        e.nucleo().agregarProceso(corto);
        e.nucleo().agregarProceso(largo);
        e.avanzar(1, 8);
        e.nucleo().agregarProceso(cpu("C"));
        // La escritura recorre 50 -> 0 y transfiere 16 bloques; la E/S de 30 ciclos termina antes
        e.avanzar(9, 100);
        comprobar(corto.getEstado() == EstadoProceso.LISTO_SUSPENDIDO && corto.getMotivoBloqueo() == null
                && e.colas().cantidadListosSuspendidos() == 1 && corto.isEnSwap(),
                "Si la E/S termina durante la escritura, queda Listo/Suspendido y no vuelve a Listo.");
        comprobar(e.registro().contains("mientras se escribe al swap")
                && e.registro().contains("(bloqueado -> listo/suspendido)"),
                "El registro explica el evento ocurrido durante la escritura.");

        // E/S de 120 ciclos: la escritura termina antes y el evento llega con el proceso ya en swap
        Escenario d = Escenario.nuevo(32, 100);
        PCB despues = conES("Despues", 120);
        d.nucleo().agregarProceso(despues);
        d.nucleo().agregarProceso(conES("Queda", 500));
        d.avanzar(1, 8);
        d.nucleo().agregarProceso(cpu("C"));
        d.avanzar(9, 100);
        comprobar(despues.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO, "Primero queda Bloqueado/Suspendido.");
        d.avanzar(101, 200);
        comprobar(despues.getEstado() == EstadoProceso.LISTO_SUSPENDIDO
                && d.registro().contains("(bloqueado/suspendido -> listo/suspendido)"),
                "El evento posterior lo lleva de Bloqueado/Suspendido a Listo/Suspendido.");
        System.out.println("OK: evento durante la escritura al swap y Listo/Suspendido.");
    }

    private static void sinVictimaNiSwap() {
        Escenario sinVictima = Escenario.nuevo(32, 100);
        sinVictima.nucleo().agregarProceso(cpu("A"));
        sinVictima.nucleo().agregarProceso(cpu("B"));
        PCB espera = cpu("C");
        sinVictima.nucleo().agregarProceso(espera);
        sinVictima.avanzar(1, 20);
        comprobar(espera.getEstado() == EstadoProceso.NUEVO && sinVictima.ram().getUsada() == 32
                && contar(sinVictima.registro(), "no hay procesos bloqueados para suspender") == 1,
                "Sin bloqueados elegibles el Nuevo espera y el aviso no se repite cada ciclo.");

        Escenario sinSwap = Escenario.nuevo(32, 8);
        PCB bloqueado = conES("A", 500);
        sinSwap.nucleo().agregarProceso(bloqueado);
        sinSwap.nucleo().agregarProceso(conES("B", 500));
        sinSwap.avanzar(1, 8);
        PCB otro = cpu("C");
        sinSwap.nucleo().agregarProceso(otro);
        sinSwap.avanzar(9, 60);
        comprobar(otro.getEstado() == EstadoProceso.NUEVO && sinSwap.ram().getUsada() == 32
                && bloqueado.getEstado() == EstadoProceso.BLOQUEADO && sinSwap.bloquesSwapDe(bloqueado.getId()) == 0
                && sinSwap.registro().contains("el swap no tiene 16 bloques libres"),
                "Sin swap suficiente no se reserva nada y la RAM queda intacta.");
        System.out.println("OK: sin victima o sin swap el Nuevo espera sin corromper RAM ni colas.");
    }

    private static int contar(String texto, String patron) {
        int n = 0;
        for (int i = texto.indexOf(patron); i >= 0; i = texto.indexOf(patron, i + 1)) n++;
        return n;
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
