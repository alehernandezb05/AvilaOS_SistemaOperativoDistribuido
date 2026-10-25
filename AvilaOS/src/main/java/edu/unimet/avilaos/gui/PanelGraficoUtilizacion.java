package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.hardware.CPU;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Grafico de la utilizacion de la CPU de cada computador con respecto al tiempo,
 * con una linea por computador (JFreeChart). Solo lee del Simulador; la ventana
 * llama a actualizar() en cada refresco.
 */
public class PanelGraficoUtilizacion extends JPanel {

    /** Puntos que conserva cada serie, para que el grafico no crezca sin limite. */
    public static final int MAX_PUNTOS = 300;

    private final Simulador simulador;
    private final XYSeries[] series;
    // Contadores de la CPU en la muestra anterior, para calcular la utilizacion del intervalo
    private final long[] ocupadaAnterior;
    private final long[] totalesAnterior;
    private long ultimoCiclo = -1;

    public PanelGraficoUtilizacion(Simulador simulador) {
        super(new BorderLayout());
        this.simulador = simulador;

        int cantidad = simulador.getCantidadComputadores();
        series = new XYSeries[cantidad];
        ocupadaAnterior = new long[cantidad];
        totalesAnterior = new long[cantidad];
        XYSeriesCollection datos = new XYSeriesCollection();
        for (int i = 0; i < cantidad; i++) {
            series[i] = new XYSeries("Computador " + (i + 1));
            series[i].setMaximumItemCount(MAX_PUNTOS);
            datos.addSeries(series[i]);
        }

        JFreeChart grafico = ChartFactory.createXYLineChart(
                "Utilizacion de la CPU", "Ciclo", "Utilizacion (%)", datos,
                PlotOrientation.VERTICAL, true, true, false);
        XYPlot plot = grafico.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        // Un poco por encima de 100 para que una CPU siempre ocupada no quede tapada por el borde
        plot.getRangeAxis().setRange(0, 105);
        ((NumberAxis) plot.getDomainAxis()).setAutoRangeIncludesZero(false);
        XYItemRenderer lineas = plot.getRenderer();
        for (int i = 0; i < cantidad; i++) {
            lineas.setSeriesStroke(i, new BasicStroke(2f));
        }

        add(new ChartPanel(grafico), BorderLayout.CENTER);
    }

    /**
     * Agrega un punto por computador si el ciclo avanzo desde la ultima muestra.
     * La utilizacion es la del intervalo: ciclos ocupados / ciclos transcurridos
     * desde la muestra anterior, para que se vean los cambios en el tiempo.
     */
    public void actualizar() {
        long ciclo = simulador.getCicloActual();
        if (ciclo <= ultimoCiclo) {
            return;
        }
        ultimoCiclo = ciclo;
        for (int i = 0; i < series.length; i++) {
            CPU cpu = simulador.getComputador(i + 1).getCpu();
            long ocupada = cpu.getCiclosOcupada();
            long totales = cpu.getCiclosTotales();
            long transcurridos = totales - totalesAnterior[i];
            if (transcurridos > 0) {
                double porcentaje = 100.0 * (ocupada - ocupadaAnterior[i]) / transcurridos;
                series[i].add(ciclo, porcentaje);
            }
            ocupadaAnterior[i] = ocupada;
            totalesAnterior[i] = totales;
        }
    }
}
