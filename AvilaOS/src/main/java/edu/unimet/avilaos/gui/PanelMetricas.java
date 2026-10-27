package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.metricas.CalculadorMetricas;
import edu.unimet.avilaos.metricas.ResumenBuffer;
import edu.unimet.avilaos.metricas.ResumenMetricas;
import edu.unimet.avilaos.so.Buffer;
import edu.unimet.avilaos.so.PCB;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

public class PanelMetricas extends JPanel {

    private static final String[] COLUMNAS_RENDIMIENTO = {
        "", "Term.", "Compl.", "Thr/100", "CPU %", "Resp.", "Cumpl. %", "Jain"
    };
    private static final String[] COLUMNAS_BUFFERS = {
        "Buffer", "Producidos", "Consumidos", "Bloqueo prom. semaforos"
    };

    private final Simulador simulador;
    private final ModeloTexto modeloRendimiento = new ModeloTexto(COLUMNAS_RENDIMIENTO);
    private final ModeloTexto modeloBuffers = new ModeloTexto(COLUMNAS_BUFFERS);

    public PanelMetricas(Simulador simulador) {
        super(new GridLayout(2, 1, 0, 5));
        this.simulador = simulador;
        add(crearTabla("Rendimiento", modeloRendimiento));
        add(crearTabla("Productor-consumidor", modeloBuffers));
    }

    private static JScrollPane crearTabla(String titulo, ModeloTexto modelo) {
        JScrollPane scroll = new JScrollPane(new JTable(modelo));
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
    }

    public void actualizar() {
        long ciclo = simulador.getCicloActual();
        int cantidad = simulador.getCantidadComputadores();
        PCB[] todos = CalculadorMetricas.procesosDelSistema(simulador);

        String[][] filas = new String[cantidad + 1][];
        for (int i = 1; i <= cantidad; i++) {
            filas[i - 1] = fila(CalculadorMetricas.deComputador(simulador.getComputador(i), ciclo));
        }
        filas[cantidad] = fila(CalculadorMetricas.delSistema(simulador, todos, ciclo));
        modeloRendimiento.setFilas(filas);

        Buffer[] buffers = simulador.getBuffers();
        String[][] filasBuffers = new String[buffers.length + 1][];
        long producidos = 0;
        long consumidos = 0;
        for (int i = 0; i < buffers.length; i++) {
            ResumenBuffer r = CalculadorMetricas.deBuffer(buffers[i], todos);
            producidos += r.getProducidos();
            consumidos += r.getConsumidos();
            filasBuffers[i] = new String[] {
                r.getId(), String.valueOf(r.getProducidos()), String.valueOf(r.getConsumidos()),
                texto(r.getBloqueoPromedio(), 1)
            };
        }
        filasBuffers[buffers.length] = new String[] {
            "Total", String.valueOf(producidos), String.valueOf(consumidos),
            texto(CalculadorMetricas.bloqueoPromedioSemaforos(todos), 1)
        };
        modeloBuffers.setFilas(filasBuffers);
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
            texto(r.getEquidad(), 2)
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