package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.metricas.CalculadorMetricas;
import edu.unimet.avilaos.metricas.ResumenMetricas;
import edu.unimet.avilaos.so.PCB;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

public class PanelMetricas extends JPanel {

    private static final String[] COLUMNAS_RENDIMIENTO = {
        "", "Term.", "Compl.", "Thr/100", "CPU %", "Resp.", "Cumpl. %", "Jain", "Susp.", "React."
    };
    private final Simulador simulador;
    private final ModeloTexto modeloRendimiento = new ModeloTexto(COLUMNAS_RENDIMIENTO);

    public PanelMetricas(Simulador simulador) {
        super(new GridLayout(1, 1));
        this.simulador = simulador;
        add(crearTabla("Rendimiento", modeloRendimiento));
    }

    private static JScrollPane crearTabla(String titulo, ModeloTexto modelo) {
        JTable tabla = new JTable(modelo);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(105);
        tabla.getTableHeader().setToolTipText("Thr/100: completados por 100 ciclos; CPU y cumplimiento: %; "
                + "Resp.: ciclos; Jain: indice de equidad; Susp./React.: transferencias completadas");
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
    }

    Runnable capturar() {
        long ciclo = simulador.getCicloActual();
        int cantidad = simulador.getCantidadComputadores();
        PCB[] todos = CalculadorMetricas.procesosDelSistema(simulador);

        String[][] filas = new String[cantidad + 1][];
        for (int i = 1; i <= cantidad; i++) {
            filas[i - 1] = fila(CalculadorMetricas.deComputador(simulador.getComputador(i), ciclo));
        }
        filas[cantidad] = fila(CalculadorMetricas.delSistema(simulador, todos, ciclo));
        return () -> modeloRendimiento.setFilas(filas);


    }

    private static String[] fila(ResumenMetricas r) {
        return new String[] {
            r.getNombre(),
            String.valueOf(r.getTerminados()),
            String.valueOf(r.getCompletados()),
            texto(r.getThroughput() * 100, 2),
            texto(r.getUtilizacion(), 1),
            texto(r.getRespuestaPromedio(), 1),
            texto(r.getCumplimiento(), 1),
            texto(r.getEquidad(), 2),
            String.valueOf(r.getSuspensiones()),
            String.valueOf(r.getReactivaciones())
        };
    }

    private static String texto(double valor, int decimales) {
        if (Double.isNaN(valor)) {
            return "-";
        }
        return String.format("%." + decimales + "f", valor);
    }

    private static class ModeloTexto extends AbstractTableModel {

        private final String[] columnas;
        private String[][] filas = new String[0][];

        ModeloTexto(String[] columnas) {
            this.columnas = columnas;
        }

        void setFilas(String[][] nuevas) {
            this.filas = nuevas;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return filas.length; }

        @Override
        public int getColumnCount() { return columnas.length; }

        @Override
        public String getColumnName(int columna) { return columnas[columna]; }

        @Override
        public Object getValueAt(int fila, int columna) { return filas[fila][columna]; }
    }
}