package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ArchivoSistemaArchivos;
import edu.unimet.avilaos.hardware.ModoEjecucion;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.so.ModoUsuario;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SesionUsuario;
import java.nio.file.Path;

/** Modos administrador y usuario: permisos validados por el simulador, no solo por la interfaz. */
public final class PruebaPermisos {

    private PruebaPermisos() { }

    public static void main(String[] args) throws Exception {
        ejecutar();
    }

    static void ejecutar() throws Exception {
        modos();
        administrador();
        usuario();
        politicasSoloAdministrador();
    }

    private static Simulador simulador() throws Exception {
        return new Simulador(ArchivoConfiguracion.cargar(Path.of("../escenarios/09_procesos_archivos.json")),
                ArchivoSistemaArchivos.cargar(Path.of("../escenarios/08_sistema_archivos.json")));
    }

    private static PCB proceso(DiskOperation tipo, String ruta, String solicitante, int bloques, String nuevoNombre) {
        return new PCB(tipo + " " + ruta, 1, TipoProceso.IO_BOUND, 1, 1, 1, 500, 0,
                new OperacionArchivo(tipo, ruta, solicitante, bloques, nuevoNombre));
    }

    private static int nuevos(Simulador sim) {
        return sim.getComputador(1).getNucleo().getColas().cantidadNuevos();
    }

    /** Devuelve el motivo del rechazo o null si el simulador acepto el proceso. */
    private static String pedir(Simulador sim, PCB proceso) {
        int antes = nuevos(sim);
        try {
            sim.solicitarProceso(proceso);
            comprobar(nuevos(sim) == antes + 1, "Un proceso aceptado entra a Nuevos.");
            return null;
        } catch (IllegalArgumentException e) {
            comprobar(nuevos(sim) == antes, "Un rechazo no crea el proceso.");
            return e.getMessage();
        }
    }

    private static void modos() {
        comprobar(ModoUsuario.values().length == 2 && ModoEjecucion.values().length == 2
                && !ModoUsuario.class.equals(ModoEjecucion.class),
                "El modo de uso es un enum distinto del modo de la CPU.");
        comprobar(SesionUsuario.administrador().solicitante().equals("admin"), "El administrador firma como admin.");
        invalida(ModoUsuario.USUARIO, "admin");
        invalida(ModoUsuario.USUARIO, " ");
        invalida(ModoUsuario.USUARIO, "user 1");
        System.out.println("OK: modo de uso separado del modo de CPU y usuarios validados.");
    }

    private static void administrador() throws Exception {
        Simulador sim = simulador();
        comprobar(sim.getSesion().esAdministrador(), "La simulacion empieza en modo administrador.");
        comprobar(pedir(sim, proceso(DiskOperation.READ, "/usuarios/notas.txt", "admin", 0, null)) == null,
                "El administrador lee un archivo privado ajeno.");
        comprobar(pedir(sim, proceso(DiskOperation.CREATE, "/usuarios/nuevo", "admin", 0, null)) == null
                && pedir(sim, proceso(DiskOperation.UPDATE, "/sistema/boot.bin", "admin", 0, "arranque.bin")) == null
                && pedir(sim, proceso(DiskOperation.DELETE, "/sistema/core.dll", "admin", 0, null)) == null,
                "El administrador crea directorios, renombra y elimina.");
        comprobar(pedir(sim, new PCB("Calculo", 1, TipoProceso.CPU_BOUND, 1, 16, 10, 500, 0)) == null,
                "El administrador crea procesos de CPU.");
        System.out.println("OK: el administrador gestiona archivos, directorios y procesos.");
    }

    private static void usuario() throws Exception {
        Simulador sim = simulador();
        sim.setSesion(new SesionUsuario(ModoUsuario.USUARIO, "user1"));
        comprobar(sim.getLog().obtenerDesde(0)[sim.getLog().obtenerDesde(0).length - 1]
                .contains("Cambia el modo de uso a usuario user1"), "El cambio de modo se registra.");

        comprobar(pedir(sim, proceso(DiskOperation.READ, "/usuarios/notas.txt", "user1", 0, null)) == null
                && pedir(sim, proceso(DiskOperation.READ, "/sistema/boot.bin", "user1", 0, null)) == null,
                "El usuario lee sus archivos y los publicos.");
        comprobar(pedir(sim, proceso(DiskOperation.DELETE, "/usuarios/notas.txt", "user1", 0, null)) == null,
                "El usuario elimina un archivo propio.");

        String crearDirectorio = pedir(sim, proceso(DiskOperation.CREATE, "/usuarios/mio", "user1", 0, null));
        String crearAjeno = pedir(sim, proceso(DiskOperation.CREATE, "/sistema/x.txt", "user1", 1, null));
        String renombrar = pedir(sim, proceso(DiskOperation.UPDATE, "/sistema/boot.bin", "user1", 0, "x.bin"));
        String borrarSistema = pedir(sim, proceso(DiskOperation.DELETE, "/sistema/boot.bin", "user1", 0, null));
        String procesoCpu = pedir(sim, new PCB("Calculo", 1, TipoProceso.CPU_BOUND, 1, 16, 10, 500, 0));
        comprobar(crearDirectorio != null && crearDirectorio.contains("directorios requiere administrador")
                && crearAjeno != null && crearAjeno.contains("dueño del directorio")
                && renombrar != null && renombrar.contains("requiere administrador")
                && borrarSistema != null && borrarSistema.contains("dueño del archivo")
                && procesoCpu != null, "Lo que no le corresponde se rechaza con su motivo.");

        Simulador otro = simulador();
        otro.setSesion(new SesionUsuario(ModoUsuario.USUARIO, "user2"));
        String leerAjeno = pedir(otro, proceso(DiskOperation.READ, "/usuarios/notas.txt", "user2", 0, null));
        String suplantar = pedir(otro, proceso(DiskOperation.READ, "/usuarios/notas.txt", "admin", 0, null));
        comprobar(leerAjeno != null && leerAjeno.contains("es privado")
                && suplantar != null && suplantar.contains("debe pedirla user2"),
                "Otro usuario no lee el archivo privado ni puede firmar como admin.");
        String registro = String.join("\n", otro.getLog().obtenerDesde(0));
        comprobar(registro.contains("Se rechaza READ /usuarios/notas.txt en modo usuario user2"),
                "El rechazo queda registrado.");

        // Un proceso conserva el solicitante con el que se creo aunque el modo cambie despues
        PCB propio = proceso(DiskOperation.READ, "/usuarios/notas.txt", "user1", 0, null);
        Simulador cambia = simulador();
        cambia.setSesion(new SesionUsuario(ModoUsuario.USUARIO, "user1"));
        comprobar(pedir(cambia, propio) == null, "user1 pide su lectura.");
        cambia.setSesion(SesionUsuario.administrador());
        comprobar(propio.getOperacionArchivo().solicitante().equals("user1"),
                "Cambiar de modo no cambia la identidad de procesos ya creados.");
        System.out.println("OK: el usuario solo lee lo suyo o lo publico, y los rechazos se explican.");
    }

    private static void politicasSoloAdministrador() throws Exception {
        Simulador sim = simulador();
        sim.setSesion(new SesionUsuario(ModoUsuario.USUARIO, "user1"));
        rechaza(() -> sim.cambiarPolitica(1, PoliticaPlanificacion.ROUND_ROBIN));
        rechaza(() -> sim.setQuantum(4));
        rechaza(() -> sim.cambiarPoliticaDisco(DiskPolicy.SSTF));
        rechaza(() -> sim.setVelocidadDisco(2));
        comprobar(sim.getDisco().getSnapshot().policy() == DiskPolicy.FIFO,
                "Un rechazo no cambia la politica.");
        sim.setSesion(SesionUsuario.administrador());
        sim.cambiarPoliticaDisco(DiskPolicy.SSTF);
        comprobar(sim.getDisco().getSnapshot().policy() == DiskPolicy.SSTF, "El administrador si la cambia.");
        System.out.println("OK: politicas, quantum y velocidad del disco solo en modo administrador.");
    }

    private static void invalida(ModoUsuario modo, String usuario) {
        try {
            new SesionUsuario(modo, usuario);
        } catch (IllegalArgumentException e) {
            return;
        }
        throw new AssertionError("Debe rechazarse el usuario '" + usuario + "'.");
    }

    private static void rechaza(Runnable accion) {
        try {
            accion.run();
        } catch (IllegalArgumentException e) {
            comprobar(e.getMessage().startsWith("En modo usuario"), "El rechazo explica el motivo.");
            return;
        }
        throw new AssertionError("En modo usuario debe rechazarse.");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
