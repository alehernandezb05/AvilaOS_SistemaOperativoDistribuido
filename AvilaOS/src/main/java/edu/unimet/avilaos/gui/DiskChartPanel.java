package edu.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;

/** Gráfica del desplazamiento acumulado: una curva por ejecución y política, para compararlas. */
public class DiskChartPanel extends JPanel {

    public DiskChartPanel(ComparacionDisco comparacion) {
        setLayout(new BorderLayout());

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Desplazamiento acumulado del cabezal",
                "Ciclos de reloj",
                "Desplazamiento (bloques)",
                comparacion.datos(),
                PlotOrientation.VERTICAL,
                true, true, false
        );
        ((NumberAxis) chart.getXYPlot().getDomainAxis()).setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        ((NumberAxis) chart.getXYPlot().getRangeAxis()).setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        add(new ChartPanel(chart), BorderLayout.CENTER);

        JButton limpiar = new JButton("Limpiar gráfica");
        limpiar.addActionListener(e -> comparacion.limpiar());
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        pie.add(new JLabel("Las curvas de ejecuciones anteriores se conservan al recargar para comparar políticas."));
        pie.add(limpiar);
        add(pie, BorderLayout.SOUTH);
    }
}
