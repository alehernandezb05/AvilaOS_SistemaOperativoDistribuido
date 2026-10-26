package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.so.PCB;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Timer;

/**
 * Ventana principal del simulador. Arriba los controles, al centro un panel por
 * computador y abajo los buffers, el log y el grafico de utilizacion.
 */
public class VentanaPrincipal extends JFrame {

    public static final int REFRESCO_MS = 150;

    private final Simulador simulador;
    private final PanelControles panelControles = new PanelControles();
    private final PanelLog panelLog = new PanelLog();
    private final PanelGraficoUtilizacion panelGrafico;
    private final PanelComputador[] panelesComputador;
    private final Timer refresco;

    public VentanaPrincipal(Simulador simulador) {
        super("AvilaOS - Simulador de sistema operativo distribuido");
        this.simulador = simulador;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(panelControles, BorderLayout.NORTH);
        conectarControles();

        int cantidad = simulador.getCantidadComputadores();
        panelesComputador = new PanelComputador[cantidad];
        JPanel computadores = new JPanel(new GridLayout(1, cantidad, 5, 0));
        for (int i = 0; i < cantidad; i++) {
            int id = i + 1;
            panelesComputador[i] = new PanelComputador(simulador.getComputador(id));
            // Cada computador cambia su politica de forma independiente
            panelesComputador[i].alCambiarPolitica(p -> simulador.cambiarPolitica(id, p));
            computadores.add(panelesComputador[i]);
        }

        panelGrafico = new PanelGraficoUtilizacion(simulador);

        JSplitPane inferiorDerecha = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelLog, panelGrafico);
        inferiorDerecha.setResizeWeight(0.5);

        JSplitPane inferior = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new PanelBuffers(), inferiorDerecha);
        inferior.setResizeWeight(0.4);

        JSplitPane principal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(computadores), inferior);
        principal.setResizeWeight(0.6);
        add(principal, BorderLayout.CENTER);

        setSize(1400, 850);
        setLocationRelativeTo(null);

        // javax.swing.Timer corre en el hilo de Swing, asi que puede tocar los componentes
        refresco = new Timer(REFRESCO_MS, e -> actualizar());
        refresco.start();
    }

    /** Los controles solo llaman al Simulador; no tienen logica propia. */
    private void conectarControles() {
        panelControles.setValores(simulador.getDuracionCicloMs(), simulador.getQuantum());
        panelControles.alIniciar(e -> simulador.iniciar());
        panelControles.alPausar(e -> simulador.pausar());
        panelControles.alCambiarDuracionCiclo(simulador::setDuracionCicloMs);
        panelControles.alCambiarQuantum(simulador::setQuantum);
        panelControles.alCrearProceso(e -> abrirDialogoCrearProceso());
    }

    /** Lee el estado del simulador y repinta los paneles. */
    private void actualizar() {
        panelControles.setCiclo(simulador.getCicloActual());
        panelControles.setCorriendo(simulador.estaCorriendo());
        for (PanelComputador panel : panelesComputador) {
            panel.actualizar();
        }
        panelGrafico.actualizar();
        panelLog.actualizar(simulador.getLog());
    }

    /** Abre el dialogo de crear proceso y lo envia al simulador. */
    private void abrirDialogoCrearProceso() {
        DialogoCrearProceso dialogo = new DialogoCrearProceso(this,
                simulador.getCantidadComputadores(), simulador.getCicloActual());
        dialogo.setVisible(true);
        PCB proceso = dialogo.getProcesoCreado();
        if (proceso != null) {
            simulador.agregarProceso(proceso);
        }
    }
}