package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Timer;

/**
 * Ventana principal del simulador. Arriba los controles, al centro un panel por
 * computador y abajo los buffers y el log. La GUI solo observa al modelo: un
 * Timer de Swing lee el Simulador cada cierto tiempo y actualiza los paneles.
 */
public class VentanaPrincipal extends JFrame {

    public static final int REFRESCO_MS = 150;

    private final Simulador simulador;
    private final PanelControles panelControles = new PanelControles();
    private final PanelComputador[] panelesComputador;
    private final Timer refresco;

    public VentanaPrincipal(Simulador simulador) {
        super("AvilaOS - Simulador de sistema operativo distribuido");
        this.simulador = simulador;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(panelControles, BorderLayout.NORTH);

        int cantidad = simulador.getCantidadComputadores();
        panelesComputador = new PanelComputador[cantidad];
        JPanel computadores = new JPanel(new GridLayout(1, cantidad, 5, 0));
        for (int i = 0; i < cantidad; i++) {
            panelesComputador[i] = new PanelComputador(simulador.getComputador(i + 1));
            computadores.add(panelesComputador[i]);
        }

        JSplitPane inferior = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new PanelBuffers(), new PanelLog());
        inferior.setResizeWeight(0.5);

        JSplitPane principal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(computadores), inferior);
        principal.setResizeWeight(0.7);
        add(principal, BorderLayout.CENTER);

        setSize(1400, 850);
        setLocationRelativeTo(null);

        // javax.swing.Timer corre en el hilo de Swing, asi que puede tocar los componentes
        refresco = new Timer(REFRESCO_MS, e -> actualizar());
        refresco.start();
    }

    /** Lee el estado del simulador y repinta los paneles. */
    private void actualizar() {
        panelControles.setCiclo(simulador.getCicloActual());
        for (PanelComputador panel : panelesComputador) {
            panel.actualizar();
        }
    }
}
