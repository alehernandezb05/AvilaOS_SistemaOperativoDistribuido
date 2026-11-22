package edu.unimet.avilaos.gui;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

/**
 * Panel con gráfica de JFreeChart para el movimiento/desplazamiento acumulado del cabezal.
 */
public class DiskChartPanel extends JPanel {

    private final DefaultCategoryDataset dataset;
    private final JFreeChart chart;

    public DiskChartPanel() {
        setLayout(new BorderLayout());

        dataset = new DefaultCategoryDataset();
        chart = ChartFactory.createLineChart(
                "Desplazamiento Acumulado del Cabezal vs Tiempo",
                "Ciclos de Reloj",
                "Desplazamiento (Bloques)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        ChartPanel chartPanel = new ChartPanel(chart);
        add(chartPanel, BorderLayout.CENTER);
    }

    public void addDataPoint(long timeCycle, long totalMovement, String policyName) {
        dataset.addValue(totalMovement, policyName, String.valueOf(timeCycle));
    }

    public void clearChart() {
        dataset.clear();
    }
}
