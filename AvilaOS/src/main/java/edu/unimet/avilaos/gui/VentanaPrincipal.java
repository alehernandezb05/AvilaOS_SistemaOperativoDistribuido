package edu.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

/**
 * Ventana principal del simulador. Arriba los controles, al centro un panel por
 * computador y abajo los buffers y el log. La GUI solo observa al modelo.
 */
public class VentanaPrincipal extends JFrame {

    public VentanaPrincipal(int cantidadComputadores) {
        super("AvilaOS - Simulador de sistema operativo distribuido");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(new PanelControles(), BorderLayout.NORTH);

        JPanel computadores = new JPanel(new GridLayout(1, cantidadComputadores, 5, 0));
        for (int i = 1; i <= cantidadComputadores; i++) {
            computadores.add(new PanelComputador(i));
        }

        JSplitPane inferior = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new PanelBuffers(), new PanelLog());
        inferior.setResizeWeight(0.5);

        JSplitPane principal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(computadores), inferior);
        principal.setResizeWeight(0.7);
        add(principal, BorderLayout.CENTER);

        setSize(1400, 850);
        setLocationRelativeTo(null);
    }
}
