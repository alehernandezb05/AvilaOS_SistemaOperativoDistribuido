package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.PCB;
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

    private final int cantidadComputadores;
    private final PanelControles panelControles = new PanelControles();
    private final PanelLog panelLog = new PanelLog();

    public VentanaPrincipal(int cantidadComputadores) {
        super("AvilaOS - Simulador de sistema operativo distribuido");
        this.cantidadComputadores = cantidadComputadores;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(panelControles, BorderLayout.NORTH);
        panelControles.alCrearProceso(e -> abrirDialogoCrearProceso());

        JPanel computadores = new JPanel(new GridLayout(1, cantidadComputadores, 5, 0));
        for (int i = 1; i <= cantidadComputadores; i++) {
            computadores.add(new PanelComputador(i));
        }

        JSplitPane inferior = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new PanelBuffers(), panelLog);
        inferior.setResizeWeight(0.5);

        JSplitPane principal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(computadores), inferior);
        principal.setResizeWeight(0.7);
        add(principal, BorderLayout.CENTER);

        setSize(1400, 850);
        setLocationRelativeTo(null);
    }

    // Todavia no hay Simulador (#27): el proceso se crea pero solo se anuncia en el log
    private void abrirDialogoCrearProceso() {
        DialogoCrearProceso dialogo = new DialogoCrearProceso(this, cantidadComputadores, 0);
        dialogo.setVisible(true);
        PCB proceso = dialogo.getProcesoCreado();
        if (proceso != null) {
            panelLog.agregarLinea("Proceso " + proceso + " creado para el computador "
                    + proceso.getIdComputador() + " (" + proceso.getTipo() + ")");
        }
    }
}
