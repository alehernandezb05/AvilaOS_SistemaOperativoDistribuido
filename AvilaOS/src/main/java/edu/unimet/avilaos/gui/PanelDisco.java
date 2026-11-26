package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.hardware.disco.DiskQueueEntry;
import edu.unimet.avilaos.hardware.disco.DiskQueueView;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.DiskSnapshot;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Vista del disco del computador: mapa de bloques, cola de E/S y controles.
 * Solo observa copias del disco; los cambios de politica y velocidad pasan por el Simulador.
 */
public class PanelDisco extends JPanel {

    private final DiskScheduler disco;
    private final DiskMapPanel mapa;
    private final DiskChartPanel grafica = new DiskChartPanel();
private int muestrasGraficadas = 0;
    private final ModeloCola cola = new ModeloCola();
    private final JComboBox<DiskPolicy> comboPolitica = new JComboBox<>(DiskPolicy.values());
    private final JSpinner spnVelocidad;
    private final JLabel lblEstado = new JLabel();
    private final JLabel lblMetricas = new JLabel();
    // Evita que actualizar el combo desde el disco se tome como una eleccion del usuario
    private boolean sincronizando;

    PanelDisco(DiskScheduler disco) {
        super(new BorderLayout(6, 6));
        this.disco = disco;
        DiskSnapshot inicial = disco.getSnapshot();
        spnVelocidad = new JSpinner(new SpinnerNumberModel(inicial.speed(), 1, disco.getTotalBlocks(), 1));
        comboPolitica.setSelectedItem(inicial.policy());

        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controles.add(new JLabel("Politica de disco:"));
        controles.add(comboPolitica);
        controles.add(new JLabel("Velocidad (bloques por ciclo):"));
        controles.add(spnVelocidad);
        lblEstado.setFont(lblEstado.getFont().deriveFont(Font.PLAIN));
        JPanel cabecera = new JPanel(new BorderLayout(4, 4));

cabecera.add(controles, BorderLayout.NORTH);
cabecera.add(lblEstado, BorderLayout.CENTER);
cabecera.add(lblMetricas, BorderLayout.SOUTH);

add(cabecera, BorderLayout.NORTH);

        mapa = new DiskMapPanel(disco);
        JScrollPane mapaScroll = new JScrollPane(mapa);
        mapaScroll.setBorder(BorderFactory.createTitledBorder("Mapa de bloques"));
        JPanel ladoMapa = new JPanel(new BorderLayout());
        ladoMapa.add(mapaScroll, BorderLayout.CENTER);
        ladoMapa.add(leyenda(), BorderLayout.SOUTH);

        JTable tabla = new JTable(cola);
        tabla.setDefaultRenderer(Object.class, new RenderCola());
        tabla.setFillsViewportHeight(true);
        int[] anchos = {80, 60, 140, 70, 80, 55, 65};
        for (int i = 0; i < anchos.length; i++) tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        JScrollPane tablaScroll = new JScrollPane(tabla);
        tablaScroll.setBorder(BorderFactory.createTitledBorder("Cola de E/S del disco"));
        tablaScroll.setPreferredSize(new Dimension(420, 200));

        JSplitPane contenido = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ladoMapa, tablaScroll);
        contenido.setResizeWeight(0.6);
JSplitPane vistaCompleta = new JSplitPane(
        JSplitPane.VERTICAL_SPLIT, contenido, grafica);

vistaCompleta.setResizeWeight(0.6);
add(vistaCompleta, BorderLayout.CENTER);

SwingUtilities.invokeLater(
        () -> vistaCompleta.setDividerLocation(0.6));
SwingUtilities.invokeLater(() -> contenido.setDividerLocation(0.6));
    }

    /** Avisa cuando el usuario elige otra politica de disco. */
    /** Solo el administrador cambia la politica y la velocidad del disco. */
    void setControlesEditables(boolean editables) {
        comboPolitica.setEnabled(editables);
        spnVelocidad.setEnabled(editables);
    }

    void alCambiarPolitica(Consumer<DiskPolicy> accion) {
        comboPolitica.addActionListener(e -> {
            if (!sincronizando) accion.accept((DiskPolicy) comboPolitica.getSelectedItem());
        });
    }

    // El spinner solo deja escribir enteros dentro del rango del disco
    void alCambiarVelocidad(IntConsumer accion) {
        spnVelocidad.addChangeListener(e -> {
            if (!sincronizando) accion.accept((Integer) spnVelocidad.getValue());
        });
    }

    /** Lee estado y cola en una sola copia y repinta. Se llama desde el hilo de Swing. */
    Runnable capturar() {
        DiskQueueView vista = disco.getQueueView();
        DiskSnapshot estado = vista.state();
        var bloques = disco.getBlocks();
        DiskScheduler.MovementSample[] nuevasMuestras = disco.getMovementHistory(muestrasGraficadas);
        StringBuilder metricas = new StringBuilder("<html>");
        metricas.append(String.format(
                "Espera promedio: %.2f ciclos | Retorno C-SCAN: %d ciclos",
                disco.getAverageWaitCycles(), disco.getTotalReturnCycles()));
        metricas.append("<br>");
        for (DiskPolicy politica : DiskPolicy.values()) {
            metricas.append(String.format(
                    "%s: %d atendidas (%.2f ciclos) &nbsp; | &nbsp; ",
                    politica, disco.getServedCount(politica), disco.getAverageWaitCycles(politica)));
        }
        metricas.append("</html>");
        String textoMetricas = metricas.toString();
        return () -> {
            sincronizando = true;
            try {
                if (comboPolitica.getSelectedItem() != estado.policy() && !comboPolitica.isPopupVisible()) {
                    comboPolitica.setSelectedItem(estado.policy());
                }
                if (!spnVelocidad.getValue().equals(estado.speed()) && !spnVelocidad.hasFocus()) {
                    spnVelocidad.setValue(estado.speed());
                }
            } finally {
                sincronizando = false;
            }
            lblEstado.setText("   Cabezal: " + estado.currentHead() + " [" + estado.direction() + "]"
                    + "   Desplazamiento: " + estado.totalHeadMovement()
                    + "   Atendidas: " + estado.totalRequestsServed()
                    + "   En cola: " + estado.pendingCount());
            lblMetricas.setText(textoMetricas);
            cola.mostrar(vista.entries());
            mapa.mostrar(bloques, estado);
            for (DiskScheduler.MovementSample muestra : nuevasMuestras) {
                grafica.addDataPoint(muestra.cycle(), muestra.movement(), "Desplazamiento acumulado");
            }
            muestrasGraficadas += nuevasMuestras.length;
        };
    }

    private static JPanel leyenda() {
        JPanel leyenda = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        leyenda.add(muestra(new Color(200, 230, 201), "Libre"));
        leyenda.add(muestra(DiskMapPanel.COLOR_ARCHIVO, "Archivo"));
        leyenda.add(muestra(new Color(225, 190, 231), "Swap disponible"));
        leyenda.add(muestra(new Color(171, 117, 188), "Swap ocupado"));
        leyenda.add(muestra(Color.ORANGE, "Cabezal"));
        JLabel destino = new JLabel("Borde azul: destino");
        leyenda.add(destino);
        return leyenda;
    }

    private static JLabel muestra(Color color, String texto) {
        JLabel etiqueta = new JLabel("■ " + texto);
        etiqueta.setForeground(color.darker());
        return etiqueta;
    }

    /** Filas de la cola: la activa primero y luego las pendientes en orden de llegada. */
    private static final class ModeloCola extends AbstractTableModel {

        private static final String[] COLUMNAS = {"Estado", "Solicitud", "Proceso", "Operacion",
            "Destino", "Bloques", "Llegada"};
        private DiskQueueEntry[] filas = new DiskQueueEntry[0];

        void mostrar(DiskQueueEntry[] entradas) {
            filas = entradas;
            fireTableDataChanged();
        }

        boolean esActiva(int fila) {
            return fila < filas.length && filas[fila].active();
        }

        @Override public int getRowCount() { return filas.length; }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) {
            DiskQueueEntry e = filas[fila];
            return switch (columna) {
                case 0 -> e.active() ? "Atendiendo" : "Pendiente";
                case 1 -> e.requestId();
                case 2 -> "P" + e.processId() + " (" + e.processName() + ")";
                case 3 -> e.operation();
                case 4 -> e.targetBlock() < 0 ? "Metadatos" : "Bloque " + e.targetBlock();
                case 5 -> e.blockCount();
                default -> "Ciclo " + e.arrivalCycle();
            };
        }
    }

    /** Resalta la solicitud que el disco esta atendiendo. */
    private final class RenderCola extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            Component c = super.getTableCellRendererComponent(tabla, valor, seleccionada, foco, fila, columna);
            boolean activa = cola.esActiva(fila);
            c.setFont(c.getFont().deriveFont(activa ? Font.BOLD : Font.PLAIN));
            if (!seleccionada) c.setBackground(activa ? new Color(255, 236, 179) : tabla.getBackground());
            return c;
        }
    }
}
