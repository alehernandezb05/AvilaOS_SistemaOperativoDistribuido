package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Entrada;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Nodo;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Vista;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/** Exploración de solo lectura y solicitudes de procesos para el sistema de archivos. */
final class PanelSistemaArchivos extends JPanel {

    private final SistemaArchivos sistema;
    private final DiskScheduler disco;
    private Vista vista;
    private final JTree arbol;
    private final ModeloAsignacion asignacion;
    private final DiskMapPanel mapa;
    private final JLabel detalle = new JLabel("Selecciona una entrada del árbol.");
    private final JButton leer = new JButton("Crear proceso READ…");
    private final JButton crear = new JButton("Crear archivo o directorio…");

    PanelSistemaArchivos(SistemaArchivos sistema, DiskScheduler disco, String seleccion, Runnable cargar,
                         Consumer<String> alLeer, Consumer<String> alCrear) {
        super(new BorderLayout(6, 6));
        this.sistema = sistema;
        this.disco = disco;
        vista = sistema == null ? null : sistema.vista();
        JButton boton = new JButton("Cargar sistema de archivos…");
        boton.addActionListener(e -> cargar.run());
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controles.add(boton);
        leer.setEnabled(false);
        leer.addActionListener(e -> alLeer.accept(rutaSeleccionada()));
        controles.add(leer);
        crear.setEnabled(false);
        crear.addActionListener(e -> alCrear.accept(rutaSeleccionada()));
        controles.add(crear);
        if (sistema == null) controles.add(new JLabel("Sin sistema de archivos cargado"));
        add(controles, BorderLayout.NORTH);

        arbol = new JTree(new ModeloArbol(vista == null ? null : vista.raiz()));
        arbol.setEditable(false);
        arbol.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        arbol.addTreeSelectionListener(e -> mostrarDetalle());
        asignacion = new ModeloAsignacion(vista);
        JTable tabla = new JTable(asignacion);
        tabla.setDefaultRenderer(Color.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object valor) {
                setText("■");
                setForeground((Color) valor);
            }
        });

        JSplitPane explorador = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(arbol), new JScrollPane(tabla));
        explorador.setResizeWeight(0.30);
        mapa = new DiskMapPanel(sistema == null ? null : disco);
        JScrollPane mapaScroll = new JScrollPane(mapa);
        mapaScroll.setMinimumSize(new Dimension(0, 95));
        JSplitPane contenido = new JSplitPane(JSplitPane.VERTICAL_SPLIT, explorador, mapaScroll);
        contenido.setResizeWeight(0.68);
        add(sistema == null ? explorador : contenido, BorderLayout.CENTER);
        if (sistema != null) SwingUtilities.invokeLater(() -> contenido.setDividerLocation(0.65));
        detalle.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        add(detalle, BorderLayout.SOUTH);

        seleccionar(seleccion);
    }

    void actualizar() {
        if (sistema == null) return;
        var observacion = sistema.observar(disco.getStorage());
        Vista actual = observacion.archivos();
        if (vista != actual) {
            String seleccion = rutaSeleccionada();
            Lista<String> expandidas = new Lista<>();
            var caminos = arbol.getExpandedDescendants(new TreePath(vista.raiz()));
            if (caminos != null) {
                while (caminos.hasMoreElements()) {
                    Nodo nodo = (Nodo) caminos.nextElement().getLastPathComponent();
                    expandidas.agregar(nodo.entrada().ruta());
                }
            }
            vista = actual;
            arbol.setModel(new ModeloArbol(actual.raiz()));
            for (int i = 0; i < expandidas.getTamano(); i++) {
                Nodo nodo = actual.buscar(expandidas.obtener(i));
                if (nodo != null) arbol.expandPath(camino(nodo));
            }
            asignacion.actualizar(actual);
            seleccionar(seleccion);
        }
        mapa.mostrarBloques(observacion.bloques());
    }

    private void seleccionar(String seleccion) {
        Nodo nodo = vista == null ? null : vista.buscar(seleccion);
        if (nodo != null) {
            TreePath camino = camino(nodo);
            arbol.setSelectionPath(camino);
            arbol.scrollPathToVisible(camino);
        }
    }

    String rutaSeleccionada() {
        return arbol.getLastSelectedPathComponent() instanceof Nodo nodo ? nodo.entrada().ruta() : null;
    }

    private void mostrarDetalle() {
        if (arbol.getLastSelectedPathComponent() instanceof Nodo nodo) {
            Entrada entrada = nodo.entrada();
            leer.setEnabled(entrada.tipo() == SistemaArchivos.Tipo.FILE);
            crear.setEnabled(entrada.tipo() == SistemaArchivos.Tipo.DIRECTORY);
            detalle.setText(entrada.nombre() + "  |  Bloques: " + entrada.bloques()
                    + "  |  Dueño: " + entrada.duenio() + "  |  Permiso: "
                    + (entrada.permiso() == SistemaArchivos.Permiso.PUBLIC ? "Público" : "Privado")
                    + "  |  Primer bloque: " + (entrada.bloques() == 0 ? "—" : entrada.primerBloque())
                    + (entrada.procesoCreador() > 0 ? "  |  Creador: P" + entrada.procesoCreador() : ""));
        } else {
            leer.setEnabled(false);
            crear.setEnabled(false);
            detalle.setText("Selecciona una entrada del árbol.");
        }
    }

    private static TreePath camino(Nodo nodo) {
        int profundidad = 0;
        for (Nodo actual = nodo; actual != null; actual = actual.padre()) profundidad++;
        Nodo[] segmentos = new Nodo[profundidad];
        for (Nodo actual = nodo; actual != null; actual = actual.padre()) segmentos[--profundidad] = actual;
        return new TreePath(segmentos);
    }

    private record ModeloArbol(Nodo raiz) implements TreeModel {
        @Override public Object getRoot() { return raiz; }
        @Override public Object getChild(Object padre, int indice) { return ((Nodo) padre).hijo(indice); }
        @Override public int getChildCount(Object padre) { return ((Nodo) padre).cantidadHijos(); }
        @Override public boolean isLeaf(Object nodo) {
            return ((Nodo) nodo).entrada().tipo() == SistemaArchivos.Tipo.FILE;
        }

        @Override
        public int getIndexOfChild(Object padre, Object hijo) {
            if (!(padre instanceof Nodo nodo) || hijo == null) return -1;
            for (int i = 0; i < nodo.cantidadHijos(); i++) {
                if (nodo.hijo(i) == hijo) return i;
            }
            return -1;
        }

        // Cada modelo representa una vista inmutable, que se sustituye al confirmar cambios.
        @Override public void valueForPathChanged(TreePath camino, Object valor) { }
        @Override public void addTreeModelListener(TreeModelListener listener) { }
        @Override public void removeTreeModelListener(TreeModelListener listener) { }
    }

    private static final class ModeloAsignacion extends AbstractTableModel {

        private static final String[] COLUMNAS = {"Nombre", "Bloques", "Primer bloque", "Dueño", "Creador", "Color"};
        private static final Class<?>[] TIPOS = {String.class, Integer.class, Integer.class,
            String.class, String.class, Color.class};
        private Vista vista;

        ModeloAsignacion(Vista vista) { this.vista = vista; }

        void actualizar(Vista actual) {
            vista = actual;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return vista == null ? 0 : vista.cantidadArchivos(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }
        @Override public Class<?> getColumnClass(int columna) { return TIPOS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            Entrada archivo = vista.archivo(fila);
            return switch (columna) {
                case 0 -> archivo.nombre();
                case 1 -> archivo.bloques();
                case 2 -> archivo.primerBloque();
                case 3 -> archivo.duenio();
                case 4 -> archivo.procesoCreador() == 0 ? "Carga JSON" : "P" + archivo.procesoCreador();
                case 5 -> DiskMapPanel.COLOR_ARCHIVO;
                default -> throw new IndexOutOfBoundsException(columna);
            };
        }
    }
}
