package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import edu.unimet.avilaos.hardware.ModoEjecucion;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.so.EstadoProceso;
import edu.unimet.avilaos.so.MotivoBloqueo;
import edu.unimet.avilaos.so.MotivoTerminacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Entrada;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Permiso;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Tipo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class PruebaCreate {

    private static final String CONFIG = """
            {"globalConfig":{"cycleDurationMs":50,"swapSizeBlocks":2,"initialDiskPolicy":"FIFO"},
             "computer":{"ramCapacity":32,"policy":"FCFS","quantum":0},"processes":[%s]}
            """;

    static void ejecutar() throws Exception {
        Path archivo = Files.createTempFile("avilaos-create", ".json");
        try {
            String crear = """
                    {"name":"Crear archivo","memory":1,"deadline":50,"instructions":1,
                     "targetPath":"/ana/datos","operation":"CREATE","requester":"ana","fileSizeBlocks":3},
                    {"name":"Crear directorio","memory":1,"deadline":50,"instructions":1,
                     "targetPath":"/nuevo","operation":"CREATE","requester":"admin","fileSizeBlocks":0}
                    """;
            Files.writeString(archivo, CONFIG.formatted(crear));
            creacionReal(simulador(archivo));

            Files.writeString(archivo, CONFIG.formatted(""));
            rechazosYReserva(simulador(archivo));
            vencimiento(simulador(archivo));

            Files.writeString(archivo, CONFIG.formatted(""));
            directorioDeUsuario(simulador(archivo));

            Files.writeString(archivo, CONFIG.formatted(crear.replace(",\"fileSizeBlocks\":3", "")));
            try {
                ArchivoConfiguracion.cargar(archivo);
                throw new AssertionError("CREATE debe declarar explícitamente fileSizeBlocks.");
            } catch (ConfiguracionInvalidaException esperado) {
                verificar(esperado.getMessage().contains("fileSizeBlocks"), "Debe identificar el tamaño omitido.");
            }
        } finally {
            Files.deleteIfExists(archivo);
        }
        System.out.println("OK: CREATE confirma árbol y mapa desde E/S, permisos, reservas y rechazos.");
    }

    private static Simulador simulador(Path archivo) throws Exception {
        SistemaArchivos sistema = new SistemaArchivos(10, 0, new Entrada[] {
            new Entrada("/ana", Tipo.DIRECTORY, "ana", Permiso.PUBLIC, new int[0]),
            new Entrada("/ana/previo", Tipo.FILE, "ana", Permiso.PRIVATE, new int[] {3, 5})
        });
        return new Simulador(ArchivoConfiguracion.cargar(archivo), sistema);
    }

    private static void creacionReal(Simulador sim) {
        SistemaArchivos sistema = sim.getSistemaArchivos();
        DiskBlock[] inicial = sim.getDisco().getBlocks();
        var computador = sim.getComputador(1);
        var colas = computador.getNucleo().getColas();
        PCB archivo = colas.obtenerNuevo(0);
        PCB directorio = colas.obtenerNuevo(1);

        avanzar(sim, 1);
        verificar(archivo.getProgramCounter() == 1 && computador.getCpu().getModo() == ModoEjecucion.SISTEMA_OPERATIVO,
                "CREATE consume la instrucción en modo SO.");
        avanzar(sim, 2);
        verificar(archivo.getMotivoBloqueo() == MotivoBloqueo.ESPERA_DISCO
                && sim.getDisco().getPendingRequests()[0].getProcessId() == archivo.getId(),
                "CREATE usa la misma cola de disco y bloquea a su PCB.");
        for (int ciclo = 3; ciclo <= 10; ciclo++) avanzar(sim, ciclo);
        verificar(sistema.vista().cantidadArchivos() == 1 && sistema.buscar("/ana/datos") == null
                && sistema.buscar("/nuevo") == null && Arrays.equals(inicial, sim.getDisco().getBlocks()),
                "El árbol y el mapa publicado siguen intactos durante la E/S.");
        avanzar(sim, 11);
        Entrada datos = sistema.buscar("/ana/datos").entrada();
        verificar(archivo.getEstado() == EstadoProceso.LISTO && datos.procesoCreador() == archivo.getId()
                && datos.duenio().equals("ana") && datos.permiso() == Permiso.PRIVATE
                && Arrays.equals(sim.getDisco().getStorage().getFileChain(datos.ruta(), datos.primerBloque()),
                        new int[] {2, 4, 6}), "Al finalizar se publica la cadena y el creador antes de devolver a Listo.");

        DiskBlock[] conArchivo = sim.getDisco().getBlocks();
        verificar(directorio.getEstado() == EstadoProceso.BLOQUEADO, "El directorio espera su turno de disco.");
        sim.cambiarPoliticaDisco(DiskPolicy.C_SCAN);
        avanzar(sim, 12);
        Entrada nueva = sistema.buscar("/nuevo").entrada();
        verificar(nueva.tipo() == Tipo.DIRECTORY && nueva.procesoCreador() == directorio.getId()
                && nueva.bloques() == 0 && Arrays.equals(conArchivo, sim.getDisco().getBlocks())
                && sim.getDisco().getCurrentHead() == 6 && sim.getDisco().getServedCount(DiskPolicy.C_SCAN) == 1
                && directorio.getEstado() == EstadoProceso.LISTO, "El directorio usa un ciclo de metadatos sin mover el disco.");
    }

    private static void rechazosYReserva(Simulador sim) {
        PCB primero = crear("/ana/ganador", "ana", 4, 100);
        PCB[] rechazados = {crear("/ana/ganador", "admin", 1, 100), crear("/ana/lleno", "admin", 3, 100),
            crear("/no-existe/copia", "admin", 1, 100), crear("/ana/previo", "ana", 1, 100),
            crear("/ana/sin-permiso", "bob", 1, 100), crear("/ana/subdir", "ana", 0, 100),
            crear("/ana/anonimo", null, 1, 100)};
        sim.agregarProceso(primero);
        for (PCB pc : rechazados) sim.agregarProceso(pc);
        for (int ciclo = 1; ciclo <= 30; ciclo++) avanzar(sim, ciclo);
        for (PCB pc : rechazados) {
            verificar(pc.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES,
                    "La creación inválida debe fallar: " + pc.getOperacionArchivo().ruta());
        }
        verificar(primero.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO
                && sim.getDisco().getTotalRequestsServed() == 1
                && sim.getSistemaArchivos().cantidadArchivos() == 2, "Solo la primera reserva debe publicarse.");
    }

    private static void vencimiento(Simulador sim) {
        PCB vencido = crear("/ana/vencido", "ana", 6, 3);
        DiskBlock[] inicial = sim.getDisco().getBlocks();
        sim.agregarProceso(vencido);
        for (int ciclo = 1; ciclo <= 24; ciclo++) avanzar(sim, ciclo);
        verificar(vencido.getMotivoTerminacion() == MotivoTerminacion.DEADLINE_VENCIDO
                && sim.getSistemaArchivos().buscar("/ana/vencido") == null
                && Arrays.equals(inicial, sim.getDisco().getBlocks()), "Una escritura vencida nunca se publica.");
        PCB siguiente = crear("/ana/reutilizado", "ana", 6, 100);
        sim.agregarProceso(siguiente);
        for (int ciclo = 25; ciclo <= 60; ciclo++) avanzar(sim, ciclo);
        verificar(siguiente.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO,
                "Se deben recuperar los bloques reservados tras drenar la solicitud vencida.");
    }

    /** El admin da a un usuario su directorio; el usuario crea ahí con el permiso que elija, siempre a su nombre. */
    private static void directorioDeUsuario(Simulador sim) {
        SistemaArchivos sistema = sim.getSistemaArchivos();
        PCB carpeta = crearCon("/bob", "admin", 0, "bob", Permiso.PUBLIC);
        sim.agregarProceso(carpeta);
        for (int ciclo = 1; ciclo <= 10; ciclo++) avanzar(sim, ciclo);
        Entrada creada = sistema.buscar("/bob").entrada();
        verificar(carpeta.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO
                && creada.duenio().equals("bob") && creada.permiso() == Permiso.PUBLIC,
                "El administrador crea el directorio de bob con dueño y permiso elegidos.");

        PCB publico = crearCon("/bob/notas", "bob", 1, null, Permiso.PUBLIC);
        PCB ajeno = crearCon("/bob/regalo", "bob", 1, "ana", null);
        sim.agregarProceso(publico);
        sim.agregarProceso(ajeno);
        for (int ciclo = 11; ciclo <= 40; ciclo++) avanzar(sim, ciclo);
        Entrada notas = sistema.buscar("/bob/notas").entrada();
        verificar(publico.getMotivoTerminacion() == MotivoTerminacion.COMPLETADO
                && notas.duenio().equals("bob") && notas.permiso() == Permiso.PUBLIC,
                "bob crea un archivo público en su propio directorio.");
        verificar(ajeno.getMotivoTerminacion() == MotivoTerminacion.ERROR_ES && sistema.buscar("/bob/regalo") == null
                && String.join("\n", sim.getLog().obtenerDesde(0)).contains("Solo el administrador puede asignar otro dueño"),
                "Un usuario no puede crear archivos a nombre de otro.");
        sistema.archivoParaLectura("/bob/notas", "ana");
        try {
            new OperacionArchivo(DiskOperation.READ, "/bob/notas", "ana", 0, null, null, Permiso.PUBLIC);
            throw new AssertionError("Dueño y permiso solo aplican a CREATE.");
        } catch (IllegalArgumentException esperado) {
            verificar(esperado.getMessage().contains("solo se indican al crear"), "Debe explicar el campo sobrante.");
        }
    }

    private static PCB crearCon(String ruta, String solicitante, int bloques, String duenio, Permiso permiso) {
        return new PCB("Crear", 1, TipoProceso.IO_BOUND, 1, 1, 1, 100, 0,
                new OperacionArchivo(DiskOperation.CREATE, ruta, solicitante, bloques, null, duenio, permiso));
    }

    private static PCB crear(String ruta, String solicitante, int bloques, int plazo) {
        return new PCB("Crear", 1, TipoProceso.IO_BOUND, 1, 1, 1, plazo, 0,
                new OperacionArchivo(DiskOperation.CREATE, ruta, solicitante, bloques, null));
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
