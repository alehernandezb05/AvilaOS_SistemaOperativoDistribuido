package edu.unimet.avilaos.so.archivos;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.DiskStorage;
import edu.unimet.avilaos.hardware.disco.DiskStorage.FileReservation;
import edu.unimet.avilaos.hardware.disco.DiskStorage.InitialFile;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import java.util.Arrays;

/** Jerarquía del sistema de archivos, independiente de Swing. */
public final class SistemaArchivos {

    private static final String ADMINISTRADOR = "admin";

    public enum Tipo { DIRECTORY, FILE }
    public enum Permiso { PUBLIC, PRIVATE }

    public record Entrada(String ruta, Tipo tipo, String duenio, Permiso permiso, int[] cadena,
                          int procesoCreador) {

        public Entrada(String ruta, Tipo tipo, String duenio, Permiso permiso, int[] cadena) {
            this(ruta, tipo, duenio, permiso, cadena, 0);
        }

        public Entrada {
            validarRuta(ruta);
            if (tipo == null || duenio == null || duenio.isBlank() || permiso == null || cadena == null
                    || procesoCreador < 0) {
                throw new IllegalArgumentException("Metadatos incompletos de " + ruta + ".");
            }
            if (tipo == Tipo.FILE ? ruta.equals("/") || cadena.length == 0 : cadena.length != 0) {
                throw new IllegalArgumentException("Tipo o bloques incompatibles con la ruta " + ruta + ".");
            }
            cadena = cadena.clone();
        }

        @Override
        public int[] cadena() { return cadena.clone(); }

        public String nombre() {
            return ruta.equals("/") ? ruta : ruta.substring(ruta.lastIndexOf('/') + 1);
        }

        public int bloques() { return cadena.length; }
        public int primerBloque() { return cadena.length == 0 ? DiskBlock.END_OF_CHAIN : cadena[0]; }
    }

    static void validarRuta(String ruta) {
        if (ruta == null || !ruta.startsWith("/")) {
            throw new IllegalArgumentException("La ruta debe ser absoluta: " + ruta);
        }
        if (ruta.equals("/")) return;
        for (String nombre : ruta.substring(1).split("/", -1)) validarNombre(nombre);
    }

    public static String rutaHija(String padre, String nombre) {
        validarRuta(padre);
        validarNombre(nombre);
        return (padre.equals("/") ? "" : padre) + "/" + nombre;
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.contains("/") || nombre.equals(".") || nombre.equals("..")) {
            throw new IllegalArgumentException("Nombre de archivo o directorio inválido: " + nombre);
        }
    }

    private static String rutaPadre(String ruta) {
        int separador = ruta.lastIndexOf('/');
        return separador == 0 ? "/" : ruta.substring(0, separador);
    }

    public Entrada archivoParaLectura(String ruta, String solicitante) {
        Nodo nodo = buscar(ruta);
        if (nodo == null || nodo.entrada.tipo() != Tipo.FILE) {
            throw new IllegalArgumentException("No existe un archivo en " + ruta + ".");
        }
        Entrada archivo = nodo.entrada;
        if (archivo.permiso() == Permiso.PRIVATE && !archivo.duenio().equals(solicitante)) {
            throw new IllegalArgumentException("READ denegado: " + ruta + " es privado y pertenece a " + archivo.duenio() + ".");
        }
        return archivo;
    }

    public static final class Nodo {

        private final Entrada entrada;
        private final Lista<Nodo> hijos = new Lista<>();
        private Nodo padre;

        private Nodo(Entrada entrada) { this.entrada = entrada; }

        public Entrada entrada() { return entrada; }
        public Nodo padre() { return padre; }
        public int cantidadHijos() { return hijos.getTamano(); }
        public Nodo hijo(int indice) { return hijos.obtener(indice); }

        @Override
        public String toString() { return entrada.nombre(); }
    }

    /** Árbol y archivos publicados juntos. Los nodos permanecen estables dentro de cada vista. */
    public static final class Vista {
        private final Entrada[] entradas;
        private final Nodo[] nodos;
        private final Entrada[] archivos;

        private Vista(int bloquesDisco, Entrada[] entradas) {
            this.entradas = entradas.clone();
            nodos = new Nodo[entradas.length + 1];
            boolean[] ocupados = new boolean[bloquesDisco];
            int totalNodos = 1;
            int cantidadArchivos = 0;
            for (Entrada entrada : entradas) {
                if (buscar(entrada.ruta()) != null) {
                    throw new IllegalArgumentException("Ruta duplicada: " + entrada.ruta());
                }
                Nodo nodo = new Nodo(entrada);
                if (entrada.ruta().equals("/")) nodos[0] = nodo;
                else nodos[totalNodos++] = nodo;
                if (entrada.tipo() == Tipo.FILE) {
                    cantidadArchivos++;
                    for (int bloque : entrada.cadena()) {
                        if (bloque < 0 || bloque >= ocupados.length || ocupados[bloque]) {
                            throw new IllegalArgumentException("Bloque fuera de rango o repetido en "
                                    + entrada.ruta() + ": " + bloque);
                        }
                        ocupados[bloque] = true;
                    }
                }
            }
            if (nodos[0] == null) {
                nodos[0] = new Nodo(new Entrada("/", Tipo.DIRECTORY, ADMINISTRADOR, Permiso.PUBLIC, new int[0]));
            }
            archivos = new Entrada[cantidadArchivos];
            int siguienteArchivo = 0;
            for (int i = 1; i < totalNodos; i++) {
                Nodo nodo = nodos[i];
                String ruta = nodo.entrada.ruta();
                Nodo padre = buscar(rutaPadre(ruta));
                if (padre == null || padre.entrada.tipo() != Tipo.DIRECTORY) {
                    throw new IllegalArgumentException("El padre de " + ruta
                            + " debe ser un directorio existente: " + rutaPadre(ruta));
                }
                nodo.padre = padre;
                padre.hijos.agregar(nodo);
                if (nodo.entrada.tipo() == Tipo.FILE) archivos[siguienteArchivo++] = nodo.entrada;
            }
        }

        public Nodo buscar(String ruta) {
            if (ruta == null) return null;
            for (Nodo nodo : nodos) {
                if (nodo != null && ruta.equals(nodo.entrada.ruta())) return nodo;
            }
            return null;
        }

        public Nodo raiz() { return nodos[0]; }
        public int cantidadArchivos() { return archivos.length; }
        public Entrada archivo(int indice) { return archivos[indice]; }
    }

    private record Creacion(long solicitud, Entrada entrada, FileReservation reserva) { }
    public record VistaDisco(Vista archivos, DiskBlock[] bloques) { }

    private final int bloquesDisco;
    private final int cabezalInicial;
    private final Lista<Creacion> pendientes = new Lista<>();
    private volatile Vista vista;

    public SistemaArchivos(int bloquesDisco, int cabezalInicial, Entrada[] entradas) {
        this(bloquesDisco, cabezalInicial, new Vista(bloquesDisco, entradas));
    }

    private SistemaArchivos(int bloquesDisco, int cabezalInicial, Vista vista) {
        this.bloquesDisco = bloquesDisco;
        this.cabezalInicial = cabezalInicial;
        this.vista = vista;
    }

    /** Incorpora las entradas confirmadas al iniciar otra simulación. */
    public SistemaArchivos copia() { return new SistemaArchivos(bloquesDisco, cabezalInicial, vista); }

    public synchronized DiskRequest prepararCreacion(OperacionArchivo operacion, int procesoId, String proceso,
                                                      DiskStorage disco) {
        if (operacion.tipo() != DiskOperation.CREATE) throw new IllegalArgumentException("Se esperaba CREATE.");
        String ruta = operacion.ruta();
        if (buscar(ruta) != null) throw new IllegalArgumentException("La ruta ya existe: " + ruta);
        Nodo padre = buscar(rutaPadre(ruta));
        if (padre == null || padre.entrada.tipo() != Tipo.DIRECTORY) {
            throw new IllegalArgumentException("El directorio destino no existe: " + rutaPadre(ruta));
        }
        for (int i = 0; i < pendientes.getTamano(); i++) {
            if (pendientes.obtener(i).entrada.ruta().equals(ruta)) {
                throw new IllegalArgumentException("Ya hay una creación pendiente para " + ruta + ".");
            }
        }
        String solicitante = operacion.solicitante();
        boolean directorio = operacion.bloques() == 0;
        validarPermisoCreacion(solicitante, directorio, padre.entrada);
        FileReservation reserva = directorio ? null : disco.reserveFile(ruta, operacion.bloques());
        try {
            int[] cadena = directorio ? new int[0] : reserva.blockNumbers();
            DiskRequest solicitud = directorio ? DiskRequest.metadata(procesoId, proceso, DiskOperation.CREATE)
                    : new DiskRequest(procesoId, proceso, DiskOperation.CREATE, cadena);
            Entrada entrada = new Entrada(ruta, directorio ? Tipo.DIRECTORY : Tipo.FILE,
                    solicitante, Permiso.PRIVATE, cadena, procesoId);
            pendientes.agregar(new Creacion(solicitud.getId(), entrada, reserva));
            return solicitud;
        } catch (IllegalArgumentException e) {
            if (reserva != null) disco.finishFileReservation(reserva, false);
            throw e;
        }
    }

    private static void validarPermisoCreacion(String solicitante, boolean directorio, Entrada padre) {
        if (ADMINISTRADOR.equals(solicitante)) return;
        if (solicitante == null || directorio || !padre.duenio().equals(solicitante)) {
            throw new IllegalArgumentException(directorio ? "Crear directorios requiere administrador."
                    : "CREATE requiere administrador o ser dueño del directorio destino.");
        }
    }

    public synchronized void finalizarCreacion(long solicitudId, boolean confirmar, DiskStorage disco) {
        for (int i = 0; i < pendientes.getTamano(); i++) {
            Creacion creacion = pendientes.obtener(i);
            if (creacion.solicitud != solicitudId) continue;
            Vista siguiente = vista;
            if (confirmar) {
                Entrada[] entradas = Arrays.copyOf(vista.entradas, vista.entradas.length + 1);
                entradas[entradas.length - 1] = creacion.entrada;
                siguiente = new Vista(bloquesDisco, entradas);
            }
            if (creacion.reserva != null) disco.finishFileReservation(creacion.reserva, confirmar);
            pendientes.remover(creacion);
            vista = siguiente;
            return;
        }
        if (confirmar) throw new IllegalArgumentException("La creación ya no está pendiente.");
    }

    public Vista vista() { return vista; }
    public synchronized VistaDisco observar(DiskStorage disco) {
        return new VistaDisco(vista, disco.getBlocks());
    }
    public Nodo buscar(String ruta) { return vista.buscar(ruta); }
    public Nodo raiz() { return vista.raiz(); }
    public int bloquesDisco() { return bloquesDisco; }
    public int cabezalInicial() { return cabezalInicial; }
    public int cantidadArchivos() { return vista.cantidadArchivos(); }
    public Entrada archivo(int indice) { return vista.archivo(indice); }

    public InitialFile[] archivosIniciales() {
        Vista actual = vista;
        InitialFile[] iniciales = new InitialFile[actual.cantidadArchivos()];
        for (int i = 0; i < iniciales.length; i++) {
            Entrada archivo = actual.archivo(i);
            iniciales[i] = new InitialFile(archivo.ruta(), archivo.cadena());
        }
        return iniciales;
    }
}
