package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.Configuracion.ConfigComputador;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.enums.DiskBlockType;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.MotivoTerminacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Entrada;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Permiso;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Tipo;
import java.util.Arrays;

/** UPDATE (renombrar) y DELETE (recursivo) hechos por procesos de E/S, nunca por la interfaz. */
final class PruebaActualizarEliminar {

    static void ejecutar() {
        renombrarArchivo();
        renombrarDirectorio();
        rechazosDeActualizacion();
        eliminarArchivo();
        eliminarDirectorio();
        rechazosDeEliminacion();
        conflictosEntreOperaciones();
        vencimientoDeUnaEliminacion();
        System.out.println("OK: UPDATE y DELETE confirman desde el disco, propagan rutas, liberan bloques y rechazan.");
    }

    private static Simulador simulador() {
        SistemaArchivos sistema = new SistemaArchivos(12, 0, new Entrada[] {
            new Entrada("/ana", Tipo.DIRECTORY, "ana", Permiso.PUBLIC, new int[0]),
            new Entrada("/ana/previo", Tipo.FILE, "ana", Permiso.PRIVATE, new int[] {3, 5}),
            new Entrada("/ana/sub", Tipo.DIRECTORY, "ana", Permiso.PUBLIC, new int[0]),
            new Entrada("/ana/sub/a.txt", Tipo.FILE, "ana", Permiso.PUBLIC, new int[] {7}),
            new Entrada("/otro", Tipo.FILE, "bob", Permiso.PUBLIC, new int[] {1, 2})
        });
        Configuracion config = new Configuracion(100, 0, 0, new ConfigComputador[] {
            new ConfigComputador(32, PoliticaPlanificacion.FCFS)
        });
        return new Simulador(config, sistema);
    }

    private static PCB proceso(DiskOperation tipo, String ruta, String solicitante, int bloques, String nuevo,
                               int plazo) {
        return new PCB(tipo.name(), 1, TipoProceso.IO_BOUND, 1, 1, 1, plazo, 0,
                new OperacionArchivo(tipo, ruta, solicitante, bloques, nuevo));
    }

    private static PCB renombrar(String ruta, String solicitante, String nuevo) {
        return proceso(DiskOperation.UPDATE, ruta, solicitante, 0, nuevo, 500);
    }

    private static PCB eliminar(String ruta, String solicitante) {
        return proceso(DiskOperation.DELETE, ruta, solicitante, 0, null, 500);
    }

    private static void renombrarArchivo() {
        Simulador sim = simulador();
        SistemaArchivos sistema = sim.getSistemaArchivos();
        PCB pcb = renombrar("/otro", "admin", "nuevo");
        sim.agregarProceso(pcb);

        avanzar(sim, 1);
        avanzar(sim, 2);
        verificar(pcb.getEstado() == EstadoProceso.BLOQUEADO && sim.getDisco().getPendingRequests().length == 1,
                "UPDATE bloquea al proceso con una solicitud en la cola del disco.");
        verificar(sistema.buscar("/otro") != null && sistema.buscar("/nuevo") == null
                && rutaDelBloque(sim, 1).equals("/otro"), "Durante la E/S el árbol y el mapa siguen igual.");
        avanzar(sim, 3);
        Entrada nueva = sistema.buscar("/nuevo").entrada();
        verificar(sistema.buscar("/otro") == null && nueva.duenio().equals("bob")
                && nueva.permiso() == Permiso.PUBLIC && rutaDelBloque(sim, 1).equals("/nuevo")
                && rutaDelBloque(sim, 2).equals("/nuevo")
                && Arrays.equals(sim.getDisco().getStorage().getFileChain("/nuevo", nueva.primerBloque()),
                        new int[] {1, 2}), "Al confirmar cambia la ruta y se conservan dueño, permiso y cadena.");
        avanzar(sim, 4);
        verificar(pcb.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO
                && logContiene(sim, "renombra /otro a nuevo"), "El proceso termina y el log explica el cambio.");
    }

    private static void renombrarDirectorio() {
        Simulador sim = simulador();
        SistemaArchivos sistema = sim.getSistemaArchivos();
        PCB renombre = renombrar("/ana", "admin", "carlos");
        PCB lecturaVieja = proceso(DiskOperation.READ, "/ana/previo", "ana", 0, null, 500);
        sim.agregarProceso(renombre);
        correr(sim, 1, 6);
        verificar(renombre.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO && sistema.buscar("/ana") == null
                && sistema.buscar("/carlos") != null && sistema.buscar("/carlos/sub") != null
                && sistema.vista().cantidadArchivos() == 3, "El directorio conserva a sus descendientes.");
        verificar(Arrays.equals(sim.getDisco().getStorage().getFileChain("/carlos/previo", 3), new int[] {3, 5})
                && Arrays.equals(sim.getDisco().getStorage().getFileChain("/carlos/sub/a.txt", 7), new int[] {7})
                && rutaDelBloque(sim, 3).equals("/carlos/previo") && rutaDelBloque(sim, 5).equals("/carlos/previo")
                && rutaDelBloque(sim, 7).equals("/carlos/sub/a.txt") && sistema.buscar("/otro") != null,
                "Las cadenas de bloques y el mapa siguen la nueva ruta; lo demás no cambia.");

        sim.agregarProceso(lecturaVieja);
        PCB lecturaNueva = proceso(DiskOperation.READ, "/carlos/previo", "ana", 0, null, 500);
        sim.agregarProceso(lecturaNueva);
        correr(sim, 7, 40);
        verificar(lecturaVieja.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES
                && lecturaNueva.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO,
                "La ruta vieja ya no existe y la nueva se puede leer.");
    }

    private static void rechazosDeActualizacion() {
        Simulador sim = simulador();
        PCB[] rechazados = {
            renombrar("/otro", "ana", null), renombrar("/ana", "admin", null), renombrar("/otro", "bob", "x"), renombrar("/otro", null, "x"),
            renombrar("/ana/previo", "admin", "sub"), renombrar("/otro", "admin", "otro"),
            renombrar("/nada", "admin", "x"), renombrar("/ana/sub/a.txt", "ana", "b.txt")
        };
        for (PCB pcb : rechazados) sim.agregarProceso(pcb);
        correr(sim, 1, 40);
        for (PCB pcb : rechazados) {
            verificar(pcb.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES,
                    "UPDATE inválido debe fallar: " + pcb.getOperacionArchivo());
        }
        verificar(logContiene(sim, "pertenece a bob") && logContiene(sim, "No existe un archivo en /ana.")
                && logContiene(sim, "requiere administrador")
                && logContiene(sim, "Ya existe /ana/sub") && logContiene(sim, "igual al actual")
                && logContiene(sim, "No existe /nada"), "Cada rechazo explica el motivo en el log.");
        verificar(sim.getSistemaArchivos().cantidadArchivos() == 3 && sim.getDisco().getTotalRequestsServed() == 0
                && sim.getSistemaArchivos().buscar("/ana/previo") != null, "Un rechazo no toca el disco ni el árbol.");
        for (String nombre : new String[] {"a/b", " ", "", "..", "."}) {
            try {
                renombrar("/otro", "admin", nombre);
                throw new AssertionError("Nombre destino inválido aceptado: '" + nombre + "'");
            } catch (IllegalArgumentException esperado) {
                // correcto
            }
        }
    }

    private static void eliminarArchivo() {
        Simulador sim = simulador();
        SistemaArchivos sistema = sim.getSistemaArchivos();
        PCB pcb = eliminar("/ana/previo", "ana");
        sim.agregarProceso(pcb);
        correr(sim, 1, 3);
        verificar(pcb.getEstado() == EstadoProceso.BLOQUEADO && sistema.buscar("/ana/previo") != null
                && sim.getDisco().getBlocks()[3].contentType() == DiskBlockType.FILE,
                "Durante la E/S el archivo y sus bloques siguen ahí.");
        correr(sim, 4, 30);
        DiskBlock[] bloques = sim.getDisco().getBlocks();
        verificar(pcb.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO && sistema.buscar("/ana/previo") == null
                && bloques[3].contentType() == DiskBlockType.FREE && bloques[5].contentType() == DiskBlockType.FREE
                && bloques[7].contentType() == DiskBlockType.FILE && sistema.cantidadArchivos() == 2
                && sim.getDisco().getTotalRequestsServed() == 1 && logContiene(sim, "elimina /ana/previo"),
                "El dueño elimina su archivo: se liberan sus bloques y nada más.");

        PCB nuevo = proceso(DiskOperation.CREATE, "/ana/nuevo", "ana", 2, null, 500);
        sim.agregarProceso(nuevo);
        correr(sim, 31, 80);
        verificar(nuevo.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO
                && sistema.buscar("/ana/nuevo").entrada().bloques() == 2, "Los bloques liberados se pueden reutilizar.");
    }

    private static void eliminarDirectorio() {
        Simulador sim = simulador();
        SistemaArchivos sistema = sim.getSistemaArchivos();
        PCB pcb = eliminar("/ana", "admin");
        sim.agregarProceso(pcb);
        correr(sim, 1, 40);
        DiskBlock[] bloques = sim.getDisco().getBlocks();
        verificar(pcb.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO && sistema.buscar("/ana") == null
                && sistema.buscar("/ana/previo") == null && sistema.buscar("/ana/sub") == null
                && sistema.buscar("/ana/sub/a.txt") == null && sistema.buscar("/otro") != null
                && sistema.cantidadArchivos() == 1, "Eliminar un directorio elimina todo su contenido.");
        verificar(bloques[3].contentType() == DiskBlockType.FREE && bloques[5].contentType() == DiskBlockType.FREE
                && bloques[7].contentType() == DiskBlockType.FREE && bloques[1].contentType() == DiskBlockType.FILE
                && bloques[2].contentType() == DiskBlockType.FILE && sim.getDisco().getTotalRequestsServed() == 1,
                "Una sola solicitud libera los bloques de todos los archivos del directorio.");

        Simulador vacio = simulador();
        PCB crear = proceso(DiskOperation.CREATE, "/vacio", "admin", 0, null, 500);
        PCB borrar = eliminar("/vacio", "admin");
        vacio.agregarProceso(crear);
        correr(vacio, 1, 10);
        vacio.agregarProceso(borrar);
        correr(vacio, 11, 30);
        verificar(borrar.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO
                && vacio.getSistemaArchivos().buscar("/vacio") == null, "Un directorio vacío usa un ciclo de metadatos.");
    }

    private static void rechazosDeEliminacion() {
        Simulador sim = simulador();
        PCB[] rechazados = {
            eliminar("/ana/previo", "bob"), eliminar("/ana/previo", null), eliminar("/ana", "ana"),
            eliminar("/nada", "admin"), eliminar("/ana/sub/a.txt", "bob")
        };
        for (PCB pcb : rechazados) sim.agregarProceso(pcb);
        correr(sim, 1, 40);
        for (PCB pcb : rechazados) {
            verificar(pcb.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES,
                    "DELETE inválido debe fallar: " + pcb.getOperacionArchivo());
        }
        verificar(sim.getSistemaArchivos().cantidadArchivos() == 3 && sim.getDisco().getTotalRequestsServed() == 0
                && logContiene(sim, "DELETE denegado") && logContiene(sim, "No existe /nada"),
                "Los rechazos de DELETE no tocan nada y se explican.");
    }

    private static void conflictosEntreOperaciones() {
        Simulador sim = simulador();
        PCB borrar = eliminar("/ana", "admin");
        PCB renombrar = renombrar("/ana/previo", "admin", "otro-nombre");
        PCB crear = proceso(DiskOperation.CREATE, "/ana/nuevo", "ana", 1, null, 500);
        PCB borrarDos = eliminar("/ana/sub", "admin");
        sim.agregarProceso(borrar);
        sim.agregarProceso(renombrar);
        sim.agregarProceso(crear);
        sim.agregarProceso(borrarDos);
        correr(sim, 1, 60);
        verificar(borrar.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO
                && renombrar.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES
                && crear.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES
                && borrarDos.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES
                && logContiene(sim, "rechaza UPDATE /ana/previo", "DELETE pendiente sobre /ana")
                && logContiene(sim, "rechaza CREATE /ana/nuevo", "DELETE pendiente sobre /ana")
                && logContiene(sim, "rechaza DELETE /ana/sub", "DELETE pendiente sobre /ana"),
                "Una entrada con un cambio pendiente rechaza renombrar, crear o eliminar dentro de ella.");
        verificar(sim.getSistemaArchivos().buscar("/ana") == null && sim.getSistemaArchivos().cantidadArchivos() == 1,
                "Solo se aplica la primera operación.");
    }

    private static void vencimientoDeUnaEliminacion() {
        Simulador sim = simulador();
        SistemaArchivos sistema = sim.getSistemaArchivos();
        DiskBlock[] inicial = sim.getDisco().getBlocks();
        PCB vencido = proceso(DiskOperation.DELETE, "/ana", "admin", 0, null, 3);
        sim.agregarProceso(vencido);
        correr(sim, 1, 40);
        verificar(vencido.getMotivoTerminacion() == MotivoTerminacion.DEADLINE_VENCIDO
                && sistema.buscar("/ana") != null && Arrays.equals(inicial, sim.getDisco().getBlocks()),
                "Una eliminación vencida nunca se aplica.");
        PCB siguiente = eliminar("/ana", "admin");
        sim.agregarProceso(siguiente);
        correr(sim, 41, 100);
        verificar(siguiente.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO && sistema.buscar("/ana") == null,
                "Al drenar la solicitud vencida se libera la reserva y se puede volver a eliminar.");
    }

    private static String rutaDelBloque(Simulador sim, int bloque) {
        return sim.getDisco().getBlocks()[bloque].filePath();
    }

    private static boolean logContiene(Simulador sim, String texto) {
        return logContiene(sim, texto, "");
    }

    /** true si algún evento del log contiene ambos textos. */
    private static boolean logContiene(Simulador sim, String texto, String otro) {
        for (String evento : sim.getLog().obtenerDesde(0)) {
            if (evento.contains(texto) && evento.contains(otro)) return true;
        }
        return false;
    }

    private static void correr(Simulador sim, long desde, long hasta) {
        for (long ciclo = desde; ciclo <= hasta; ciclo++) avanzar(sim, ciclo);
    }

    private static void avanzar(Simulador sim, long ciclo) {
        var computador = sim.getComputador(1);
        DiskScheduler disco = sim.getDisco();
        disco.alCiclo(ciclo);
        computador.getNucleo().alCiclo(ciclo);
        computador.getCpu().alCiclo(ciclo);
    }

    private static void verificar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
