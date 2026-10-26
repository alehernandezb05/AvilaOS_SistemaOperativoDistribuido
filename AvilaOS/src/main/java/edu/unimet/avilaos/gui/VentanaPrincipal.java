package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import edu.unimet.avilaos.so.PCB;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
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
    public static final int COLUMNAS_COMPUTADORES = 2;
    public static final int ALTO_PANEL_COMPUTADOR = 380;

    private final Simulador simulador;
    private final PanelControles panelControles = new PanelControles();
    private final PanelLog panelLog = new PanelLog();
    private final PanelBuffers panelBuffers = new PanelBuffers();
    private final PanelGraficoUtilizacion panelGrafico;
    private final PanelComputador[] panelesComputador;
    private final Timer refresco;
    private final Consumer<Configuracion> alReiniciar;

    /**
     * @param alReiniciar que hacer al cargar una configuracion: quien abrio la ventana
     *                    crea un Simulador nuevo con ella y cierra esta ventana
     */
    public VentanaPrincipal(Simulador simulador, Consumer<Configuracion> alReiniciar) {
        super("AvilaOS - Simulador de sistema operativo distribuido");
        this.simulador = simulador;
        this.alReiniciar = alReiniciar;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(panelControles, BorderLayout.NORTH);
        conectarControles();

        int cantidad = simulador.getCantidadComputadores();
        panelesComputador = new PanelComputador[cantidad];
        // Dos computadores por fila; si no caben en la ventana aparece el scroll vertical
        JPanel computadores = new JPanel(new GridLayout(0, COLUMNAS_COMPUTADORES, 5, 5));
        for (int i = 0; i < cantidad; i++) {
            int id = i + 1;
            panelesComputador[i] = new PanelComputador(simulador.getComputador(id));
            panelesComputador[i].setPreferredSize(new Dimension(300, ALTO_PANEL_COMPUTADOR));
            // Cada computador cambia su politica de forma independiente
            panelesComputador[i].alCambiarPolitica(p -> simulador.cambiarPolitica(id, p));
            computadores.add(panelesComputador[i]);
        }

        panelGrafico = new PanelGraficoUtilizacion(simulador);

        JSplitPane inferiorDerecha = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelLog, panelGrafico);
        inferiorDerecha.setResizeWeight(0.5);

        JSplitPane inferior = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelBuffers, inferiorDerecha);
        inferior.setResizeWeight(0.4);

        JScrollPane scrollComputadores = new JScrollPane(computadores);
        scrollComputadores.getVerticalScrollBar().setUnitIncrement(16);
        JSplitPane principal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollComputadores, inferior);
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
        panelControles.alCrearBuffer(e -> new DialogoCrearBuffer(this, simulador).setVisible(true));
        panelControles.alGuardarConfiguracion(e -> guardarConfiguracion());
        panelControles.alCargarConfiguracion(e -> cargarConfiguracion());
    }

    /** Guarda en un JSON los parametros actuales del cluster. */
    private void guardarConfiguracion() {
        Path archivo = SelectorArchivoJson.elegirParaGuardar(this);
        if (archivo == null) {
            return;
        }
        if (Files.exists(archivo) && JOptionPane.showConfirmDialog(this,
                "El archivo " + archivo.getFileName() + " ya existe. Reemplazarlo?", "Guardar configuracion",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ArchivoConfiguracion.guardar(simulador.getConfiguracion(), archivo);
            JOptionPane.showMessageDialog(this, "Configuracion guardada en " + archivo,
                    "Guardar configuracion", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            error("No se pudo guardar el archivo: " + e.getMessage());
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
        }
    }

    /** Lee un JSON y, si es valido y el usuario confirma, reinicia la simulacion con el. */
    private void cargarConfiguracion() {
        Path archivo = SelectorArchivoJson.elegirParaAbrir(this);
        if (archivo == null) {
            return;
        }
        Configuracion config;
        try {
            config = ArchivoConfiguracion.cargar(archivo);
        } catch (IOException e) {
            error("No se pudo leer el archivo: " + e.getMessage());
            return;
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Cargar la configuracion reinicia la simulacion y se pierde el estado actual. Continuar?",
                "Cargar configuracion", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            alReiniciar.accept(config);
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Configuracion", JOptionPane.WARNING_MESSAGE);
    }

    /** Al cerrar la ventana tambien se detiene su refresco. */
    @Override
    public void dispose() {
        refresco.stop();
        super.dispose();
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
        panelBuffers.actualizar(simulador.getBuffers());
    }

    /** Abre el dialogo de crear proceso y lo envia al simulador. */
    private void abrirDialogoCrearProceso() {
        // Sin buffers creados, productores y consumidores avisan que hay que crear uno
        DialogoCrearProceso dialogo = new DialogoCrearProceso(this,
                simulador.getCantidadComputadores(), simulador.getCicloActual(), simulador.getIdsBuffers());
        dialogo.setVisible(true);
        PCB proceso = dialogo.getProcesoCreado();
        if (proceso != null) {
            simulador.agregarProceso(proceso);
        }
    }
}