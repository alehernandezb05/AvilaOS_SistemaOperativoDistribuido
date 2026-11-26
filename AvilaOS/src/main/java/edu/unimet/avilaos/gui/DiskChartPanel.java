package edu.unimet.avilaos.gui;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class DiskChartPanel extends JPanel {

    private final XYSeriesCollection dataset;
    private final JFreeChart chart;

    public DiskChartPanel() {
        setLayout(new BorderLayout());

        dataset = new XYSeriesCollection();

        chart = ChartFactory.createXYLineChart(
                "Desplazamiento acumulado del cabezal",
                "Ciclos de reloj",
                "Desplazamiento (bloques)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        NumberAxis ejeX =
                (NumberAxis) chart.getXYPlot().getDomainAxis();

        ejeX.setStandardTickUnits(
                NumberAxis.createIntegerTickUnits());

        NumberAxis ejeY =
                (NumberAxis) chart.getXYPlot().getRangeAxis();

        ejeY.setStandardTickUnits(
                NumberAxis.createIntegerTickUnits());

        ChartPanel chartPanel = new ChartPanel(chart);
        add(chartPanel, BorderLayout.CENTER);
    }

    public void addDataPoint(long timeCycle,
            long totalMovement, String policy) {

        int index = dataset.getSeriesIndex(policy);

        if (index == -1) {
            dataset.addSeries(
                    new XYSeries(policy, true, false));

            index = dataset.getSeriesIndex(policy);
        }

        dataset.getSeries(index).addOrUpdate(
                timeCycle, totalMovement);
    }

    public void clearChart() {
        dataset.removeAllSeries();
    }
}