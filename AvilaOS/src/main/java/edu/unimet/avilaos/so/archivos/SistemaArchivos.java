package edu.unimet.avilaos.so.archivos;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskStorage.InitialFile;

/** Jerarquía del sistema de archivos, independiente de Swing. */
public final class SistemaArchivos {

    public enum Tipo { DIRECTORY, FILE }
    public enum Permiso { PUBLIC, PRIVATE }

    public record Entrada(String ruta, Tipo tipo, String duenio, Permiso permiso, int[] cadena) {

        public Entrada {
            validarRuta(ruta);
            if (tipo == null || duenio == null || duenio.isBlank() || permiso == null || cadena == null) {
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
        for (String nombre : ruta.substring(1).split("/", -1)) {
            if (nombre.isBlank() || nombre.equals(".") || nombre.equals("..")) {
                throw new IllegalArgumentException("Componente inválido en la ruta " + ruta + ".");
            }
        }
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

    private final int bloquesDisco;
    private final int cabezalInicial;
    private final Nodo[] nodos;
    private final Entrada[] archivos;

    public SistemaArchivos(int bloquesDisco, int cabezalInicial, Entrada[] entradas) {
        this.bloquesDisco = bloquesDisco;
        this.cabezalInicial = cabezalInicial;
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
            nodos[0] = new Nodo(new Entrada("/", Tipo.DIRECTORY, "—", Permiso.PUBLIC, new int[0]));
        }
        archivos = new Entrada[cantidadArchivos];
        int siguienteArchivo = 0;
        for (int i = 1; i < totalNodos; i++) {
            Nodo nodo = nodos[i];
            String ruta = nodo.entrada.ruta();
            int separador = ruta.lastIndexOf('/');
            String rutaPadre = separador == 0 ? "/" : ruta.substring(0, separador);
            Nodo padre = buscar(rutaPadre);
            if (padre == null || padre.entrada.tipo() != Tipo.DIRECTORY) {
                throw new IllegalArgumentException("El padre de " + ruta + " debe ser un directorio existente: " + rutaPadre);
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
    public int bloquesDisco() { return bloquesDisco; }
    public int cabezalInicial() { return cabezalInicial; }
    public int cantidadArchivos() { return archivos.length; }
    public Entrada archivo(int indice) { return archivos[indice]; }

    public InitialFile[] archivosIniciales() {
        InitialFile[] iniciales = new InitialFile[archivos.length];
        for (int i = 0; i < archivos.length; i++) {
            Entrada archivo = archivos[i];
            iniciales[i] = new InitialFile(archivo.ruta(), archivo.cadena());
        }
        return iniciales;
    }
}
