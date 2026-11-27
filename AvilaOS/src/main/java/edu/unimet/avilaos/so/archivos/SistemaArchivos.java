package edu.unimet.avilaos.so.archivos;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.DiskStorage;
import edu.unimet.avilaos.hardware.disco.DiskStorage.FileReservation;
import edu.unimet.avilaos.hardware.disco.DiskStorage.InitialFile;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import java.util.concurrent.Semaphore;

/** Jerarquía del sistema de archivos, independiente de Swing. */
public final class SistemaArchivos {

    public static final String ADMINISTRADOR = "admin";

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
        if (archivo.permiso() == Permiso.PRIVATE && !archivo.duenio().equals(solicitante)
                && !ADMINISTRADOR.equals(solicitante)) {
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
    /** UPDATE o DELETE confirmado solo cuando el disco termina su solicitud. */
    private record Cambio(long solicitud, DiskOperation tipo, String ruta, String destino) { }
    public record VistaDisco(Vista archivos, DiskBlock[] bloques) {
        public VistaDisco { bloques = bloques.clone(); }
        @Override public DiskBlock[] bloques() { return bloques.clone(); }
    }

    private final int bloquesDisco;
    private final int cabezalInicial;
    private final Semaphore mutex = new Semaphore(1);
    private final Lista<Creacion> pendientes = new Lista<>();
    private final Lista<Cambio> cambios = new Lista<>();
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

    /** Copia que arranca con el cabezal donde quedó, para guardar o seguir con el disco tal como está. */
    public SistemaArchivos copia(int cabezal) {
        if (cabezal < 0 || cabezal >= bloquesDisco) throw new IllegalArgumentException("El cabezal está fuera del disco.");
        return new SistemaArchivos(bloquesDisco, cabezal, vista);
    }

    public DiskRequest prepararCreacion(OperacionArchivo operacion, int procesoId, String proceso,
                                                      DiskStorage disco) {
        mutex.acquireUninterruptibly();
        try {
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
            for (int i = 0; i < cambios.getTamano(); i++) {
                Cambio cambio = cambios.obtener(i);
                if (contiene(cambio.ruta, ruta) || (cambio.destino != null && contiene(cambio.destino, ruta))) {
                    throw new IllegalArgumentException("No se puede crear " + ruta + ": hay un " + cambio.tipo
                            + " pendiente sobre " + cambio.ruta + ".");
                }
            }
            boolean directorio = operacion.bloques() == 0;
            validarPermisoCreacion(operacion, padre.entrada);
            FileReservation reserva = directorio ? null : disco.reserveFile(ruta, operacion.bloques());
            try {
                int[] cadena = directorio ? new int[0] : reserva.blockNumbers();
                DiskRequest solicitud = directorio ? DiskRequest.metadata(procesoId, proceso, DiskOperation.CREATE)
                        : new DiskRequest(procesoId, proceso, DiskOperation.CREATE, cadena);
                Entrada entrada = new Entrada(ruta, directorio ? Tipo.DIRECTORY : Tipo.FILE,
                        operacion.duenioEfectivo(), operacion.permisoEfectivo(), cadena, procesoId);
                pendientes.agregar(new Creacion(solicitud.getId(), entrada, reserva));
                return solicitud;
            } catch (IllegalArgumentException e) {
                if (reserva != null) disco.finishFileReservation(reserva, false);
                throw e;
            }
        } finally {
            mutex.release();
        }
    }

    /**
     * Revisa solo los permisos de una operacion, sin reservar nada. La interfaz la usa antes de crear
     * el proceso; al ejecutarse, las mismas reglas se vuelven a aplicar con el estado de ese momento.
     */
    public void comprobarPermiso(OperacionArchivo operacion) {
        mutex.acquireUninterruptibly();
        try {
            String solicitante = operacion.solicitante();
            switch (operacion.tipo()) {
                case READ -> archivoParaLectura(operacion.ruta(), solicitante);
                case CREATE -> {
                    Nodo padre = buscar(rutaPadre(operacion.ruta()));
                    if (padre == null || padre.entrada.tipo() != Tipo.DIRECTORY) {
                        throw new IllegalArgumentException("El directorio destino no existe: " + rutaPadre(operacion.ruta()));
                    }
                    validarPermisoCreacion(operacion, padre.entrada);
                }
                case UPDATE -> {
                    if (operacion.nuevoNombre() == null) {
                        archivoParaEscritura(operacion.ruta(), solicitante);
                    } else if (!ADMINISTRADOR.equals(solicitante)) {
                        throw new IllegalArgumentException("UPDATE denegado: renombrar requiere administrador.");
                    }
                }
                case DELETE -> {
                    Nodo nodo = buscar(operacion.ruta());
                    if (nodo == null) throw new IllegalArgumentException("No existe " + operacion.ruta() + ".");
                    validarPermisoEliminacion(solicitante, nodo.entrada);
                }
                default -> throw new IllegalArgumentException(operacion.tipo() + " no es una operación de archivo.");
            }
        } finally {
            mutex.release();
        }
    }

    /** El administrador crea en cualquier parte y puede asignar otro dueño; un usuario, solo archivos suyos en su directorio. */
    private static void validarPermisoCreacion(OperacionArchivo operacion, Entrada padre) {
        String solicitante = operacion.solicitante();
        if (ADMINISTRADOR.equals(solicitante)) return;
        boolean directorio = operacion.bloques() == 0;
        if (solicitante == null || directorio || !padre.duenio().equals(solicitante)) {
            throw new IllegalArgumentException(directorio ? "Crear directorios requiere administrador."
                    : "CREATE requiere administrador o ser dueño del directorio destino.");
        }
        if (!solicitante.equals(operacion.duenioEfectivo())) {
            throw new IllegalArgumentException("Solo el administrador puede asignar otro dueño.");
        }
    }

    /** Escribir el contenido exige un archivo propio o ser administrador; no se pueden modificar archivos ajenos. */
    private Entrada archivoParaEscritura(String ruta, String solicitante) {
        Nodo nodo = buscar(ruta);
        if (nodo == null || nodo.entrada.tipo() != Tipo.FILE) {
            throw new IllegalArgumentException("No existe un archivo en " + ruta + ".");
        }
        Entrada archivo = nodo.entrada;
        if (!ADMINISTRADOR.equals(solicitante) && !archivo.duenio().equals(solicitante)) {
            throw new IllegalArgumentException("UPDATE denegado: " + ruta + " pertenece a " + archivo.duenio() + ".");
        }
        return archivo;
    }

    /**
     * Sin newName, UPDATE escribe sobre los bloques del archivo: el disco recorre su cadena como en READ.
     * Con newName renombra, que es trabajo de metadatos: un ciclo de disco, sin mover los bloques del archivo.
     */
    public DiskRequest prepararActualizacion(OperacionArchivo operacion, int procesoId, String proceso) {
        mutex.acquireUninterruptibly();
        try {
            if (operacion.tipo() != DiskOperation.UPDATE) throw new IllegalArgumentException("Se esperaba UPDATE.");
            String nuevoNombre = operacion.nuevoNombre();
            String ruta = operacion.ruta();
            if (nuevoNombre == null) {
                Entrada archivo = archivoParaEscritura(ruta, operacion.solicitante());
                validarSinConflicto(ruta, null);
                DiskRequest solicitud = new DiskRequest(procesoId, proceso, DiskOperation.UPDATE, archivo.cadena());
                cambios.agregar(new Cambio(solicitud.getId(), DiskOperation.UPDATE, ruta, null));
                return solicitud;
            }
            if (ruta.equals("/")) throw new IllegalArgumentException("No se puede renombrar el directorio raíz.");
            Nodo nodo = buscar(ruta);
            if (nodo == null) throw new IllegalArgumentException("No existe " + ruta + ": fue eliminado o nunca existió.");
            if (!ADMINISTRADOR.equals(operacion.solicitante())) {
                throw new IllegalArgumentException("UPDATE denegado: renombrar requiere administrador.");
            }
            if (nuevoNombre.equals(nodo.entrada.nombre())) {
                throw new IllegalArgumentException("El nombre destino es igual al actual: " + nuevoNombre + ".");
            }
            String destino = rutaHija(rutaPadre(ruta), nuevoNombre);
            if (buscar(destino) != null) throw new IllegalArgumentException("Ya existe " + destino + ".");
            validarSinConflicto(ruta, destino);
            DiskRequest solicitud = DiskRequest.metadata(procesoId, proceso, DiskOperation.UPDATE);
            cambios.agregar(new Cambio(solicitud.getId(), DiskOperation.UPDATE, ruta, destino));
            return solicitud;
        } finally {
            mutex.release();
        }
    }

    /** El disco recorre los bloques de cada archivo eliminado; sin bloques es trabajo de metadatos. */
    public DiskRequest prepararEliminacion(OperacionArchivo operacion, int procesoId, String proceso) {
        mutex.acquireUninterruptibly();
        try {
            if (operacion.tipo() != DiskOperation.DELETE) throw new IllegalArgumentException("Se esperaba DELETE.");
            String ruta = operacion.ruta();
            if (ruta.equals("/")) throw new IllegalArgumentException("No se puede eliminar el directorio raíz.");
            Nodo nodo = buscar(ruta);
            if (nodo == null) throw new IllegalArgumentException("No existe " + ruta + ": fue eliminado o nunca existió.");
            validarPermisoEliminacion(operacion.solicitante(), nodo.entrada);
            validarSinConflicto(ruta, null);
            int[] bloques = new int[contarBloques(nodo)];
            copiarBloques(nodo, bloques, 0);
            DiskRequest solicitud = bloques.length == 0 ? DiskRequest.metadata(procesoId, proceso, DiskOperation.DELETE)
                    : new DiskRequest(procesoId, proceso, DiskOperation.DELETE, bloques);
            cambios.agregar(new Cambio(solicitud.getId(), DiskOperation.DELETE, ruta, null));
            return solicitud;
        } finally {
            mutex.release();
        }
    }

    private static void validarPermisoEliminacion(String solicitante, Entrada entrada) {
        if (ADMINISTRADOR.equals(solicitante)) return;
        if (solicitante != null && entrada.tipo() == Tipo.FILE && entrada.duenio().equals(solicitante)) return;
        throw new IllegalArgumentException("DELETE denegado: " + entrada.ruta()
                + " requiere administrador o ser dueño del archivo.");
    }

    private static int contarBloques(Nodo nodo) {
        int total = nodo.entrada.bloques();
        for (int i = 0; i < nodo.cantidadHijos(); i++) total += contarBloques(nodo.hijo(i));
        return total;
    }

    private static int copiarBloques(Nodo nodo, int[] destino, int desde) {
        int siguiente = desde;
        for (int bloque : nodo.entrada.cadena()) destino[siguiente++] = bloque;
        for (int i = 0; i < nodo.cantidadHijos(); i++) siguiente = copiarBloques(nodo.hijo(i), destino, siguiente);
        return siguiente;
    }

    /** true si la ruta es el ancestro o es la misma entrada. */
    private static boolean contiene(String ancestro, String ruta) {
        return ruta.equals(ancestro) || ruta.startsWith(ancestro.equals("/") ? "/" : ancestro + "/");
    }

    /** Una entrada solo admite una operación de cambio a la vez, junto con sus ancestros y descendientes. */
    private void validarSinConflicto(String ruta, String destino) {
        for (int i = 0; i < cambios.getTamano(); i++) {
            Cambio cambio = cambios.obtener(i);
            if (contiene(cambio.ruta, ruta) || contiene(ruta, cambio.ruta)
                    || (cambio.destino != null && (contiene(cambio.destino, ruta) || contiene(ruta, cambio.destino)
                        || cambio.destino.equals(destino)))) {
                throw new IllegalArgumentException("Hay un " + cambio.tipo + " pendiente sobre " + cambio.ruta + ".");
            }
        }
        for (int i = 0; i < pendientes.getTamano(); i++) {
            String creada = pendientes.obtener(i).entrada.ruta();
            if (contiene(ruta, creada) || creada.equals(destino)) {
                throw new IllegalArgumentException("Hay una creación pendiente para " + creada + ".");
            }
        }
    }

    /**
     * Confirma o cancela una operación pendiente (CREATE, UPDATE o DELETE) cuando el disco termina su solicitud
     * o cuando el proceso muere antes. Cancelar siempre libera la reserva sin publicar nada.
     */
    public void finalizarOperacion(long solicitudId, boolean confirmar, DiskStorage disco) {
        mutex.acquireUninterruptibly();
        try {
            for (int i = 0; i < cambios.getTamano(); i++) {
                Cambio cambio = cambios.obtener(i);
                if (cambio.solicitud != solicitudId) continue;
                cambios.remover(cambio);
                if (confirmar) aplicar(cambio, disco);
                return;
            }
            finalizarCreacionInterna(solicitudId, confirmar, disco);
        } finally {
            mutex.release();
        }
    }

    private void aplicar(Cambio cambio, DiskStorage disco) {
        if (buscar(cambio.ruta) == null) {
            throw new IllegalArgumentException("No existe " + cambio.ruta + ": fue eliminado antes de terminar.");
        }
        if (cambio.tipo == DiskOperation.UPDATE) {
            // Una escritura no cambia la estructura: basta con que el archivo siga existiendo.
            if (cambio.destino == null) return;
            if (buscar(cambio.destino) != null) throw new IllegalArgumentException("Ya existe " + cambio.destino + ".");
            Entrada[] entradas = new Entrada[vista.entradas.length];
            for (int i = 0; i < entradas.length; i++) {
                Entrada e = vista.entradas[i];
                if (contiene(cambio.ruta, e.ruta())) {
                    e = new Entrada(cambio.destino + e.ruta().substring(cambio.ruta.length()), e.tipo(), e.duenio(),
                            e.permiso(), e.cadena(), e.procesoCreador());
                }
                entradas[i] = e;
            }
            Vista siguiente = new Vista(bloquesDisco, entradas);
            disco.renamePath(cambio.ruta, cambio.destino);
            vista = siguiente;
            return;
        }
        int restantes = 0;
        for (Entrada e : vista.entradas) if (!contiene(cambio.ruta, e.ruta())) restantes++;
        Entrada[] entradas = new Entrada[restantes];
        int siguienteEntrada = 0;
        for (Entrada e : vista.entradas) {
            if (!contiene(cambio.ruta, e.ruta())) entradas[siguienteEntrada++] = e;
        }
        Vista siguiente = new Vista(bloquesDisco, entradas);
        for (Entrada e : vista.entradas) {
            if (e.tipo() == Tipo.FILE && contiene(cambio.ruta, e.ruta())) disco.releaseFile(e.ruta(), e.primerBloque());
        }
        vista = siguiente;
    }

    public void finalizarCreacion(long solicitudId, boolean confirmar, DiskStorage disco) {
        mutex.acquireUninterruptibly();
        try { finalizarCreacionInterna(solicitudId, confirmar, disco); }
        finally { mutex.release(); }
    }

    private void finalizarCreacionInterna(long solicitudId, boolean confirmar, DiskStorage disco) {
        for (int i = 0; i < pendientes.getTamano(); i++) {
            Creacion creacion = pendientes.obtener(i);
            if (creacion.solicitud != solicitudId) continue;
            Vista siguiente = vista;
            if (confirmar) {
                Entrada[] entradas = new Entrada[vista.entradas.length + 1];
                System.arraycopy(vista.entradas, 0, entradas, 0, vista.entradas.length);
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
    public VistaDisco observar(DiskStorage disco) {
        mutex.acquireUninterruptibly();
        try {
            return new VistaDisco(vista, disco.getBlocks());
        } finally {
            mutex.release();
        }
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
