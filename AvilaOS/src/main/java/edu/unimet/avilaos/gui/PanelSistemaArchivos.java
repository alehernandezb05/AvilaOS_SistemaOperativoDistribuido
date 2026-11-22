package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Entrada;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Nodo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/** Exploración de solo lectura del sistema de archivos. */
final class PanelSistemaArchivos extends JPanel {

    private final JTree arbol;
    private final JLabel detalle = new JLabel("Selecciona una entrada del árbol.");

    PanelSistemaArchivos(SistemaArchivos sistema, String seleccion, Runnable cargar) {
        super(new BorderLayout(6, 6));
        JButton boton = new JButton("Cargar sistema de archivos…");
        boton.addActionListener(e -> cargar.run());
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controles.add(boton);
        if (sistema == null) controles.add(new JLabel("Sin sistema de archivos cargado"));
        add(controles, BorderLayout.NORTH);

        arbol = new JTree(new ModeloArbol(sistema == null ? null : sistema.raiz()));
        arbol.setEditable(false);
        arbol.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        arbol.addTreeSelectionListener(e -> mostrarDetalle());
        JTable tabla = new JTable(new ModeloAsignacion(sistema));
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
        add(explorador, BorderLayout.CENTER);
        detalle.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        add(detalle, BorderLayout.SOUTH);

        Nodo nodo = sistema == null ? null : sistema.buscar(seleccion);
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
            detalle.setText(entrada.nombre() + "  |  Bloques: " + entrada.bloques()
                    + "  |  Dueño: " + entrada.duenio() + "  |  Permiso: "
                    + (entrada.permiso() == SistemaArchivos.Permiso.PUBLIC ? "Público" : "Privado")
                    + "  |  Primer bloque: " + (entrada.bloques() == 0 ? "—" : entrada.primerBloque()));
        } else {
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

        // El árbol representa una carga completa y se reemplaza junto con la simulación.
        @Override public void valueForPathChanged(TreePath camino, Object valor) { }
        @Override public void addTreeModelListener(TreeModelListener listener) { }
        @Override public void removeTreeModelListener(TreeModelListener listener) { }
    }

    private static final class ModeloAsignacion extends AbstractTableModel {

        private static final String[] COLUMNAS = {"Nombre", "Bloques", "Primer bloque", "Dueño", "Creador", "Color"};
        private static final Class<?>[] TIPOS = {String.class, Integer.class, Integer.class,
            String.class, String.class, Color.class};
        private final SistemaArchivos sistema;

        ModeloAsignacion(SistemaArchivos sistema) { this.sistema = sistema; }

        @Override public int getRowCount() { return sistema == null ? 0 : sistema.cantidadArchivos(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }
        @Override public Class<?> getColumnClass(int columna) { return TIPOS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            Entrada archivo = sistema.archivo(fila);
            return switch (columna) {
                case 0 -> archivo.nombre();
                case 1 -> archivo.bloques();
                case 2 -> archivo.primerBloque();
                case 3 -> archivo.duenio();
                case 4 -> "Carga JSON";
                case 5 -> DiskMapPanel.COLOR_ARCHIVO;
                default -> throw new IndexOutOfBoundsException(columna);
            };
        }
    }
}
