package edu.unimet.avilaos.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/**
 * Barra superior: ciclo global, iniciar/pausar, duracion del ciclo, quantum
 * y acciones para crear procesos y buffers. La ventana conecta cada control al Simulador.
 */
public class PanelControles extends JPanel {

    private final JLabel lblCiclo = new JLabel("Ciclo: 0");
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPausar = new JButton("Pausar");
    // Los spinners ya limitan tipo y rango de lo que se puede escribir
    private final JSpinner spnDuracionCiclo = new JSpinner(new SpinnerNumberModel(500, 50, 5000, 50));
    private final JSpinner spnQuantum = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
    private final JButton btnCrearProceso = new JButton("Crear proceso");
    private final JButton btnCrearBuffer = new JButton("Crear buffer");
    private final JButton btnGuardarConfig = new JButton("Guardar configuracion");
    private final JButton btnCargarConfig = new JButton("Cargar configuracion");

    public PanelControles() {
        super(new FlowLayout(FlowLayout.LEFT, 8, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        lblCiclo.setFont(lblCiclo.getFont().deriveFont(16f));
        // Ancho fijo para que la barra no salte cuando el ciclo crezca
        lblCiclo.setPreferredSize(new Dimension(130, 24));
        add(lblCiclo);
        add(separador());
        add(btnIniciar);
        add(btnPausar);
        add(separador());
        add(new JLabel("Ciclo (ms):"));
        add(spnDuracionCiclo);
        add(new JLabel("Quantum:"));
        add(spnQuantum);
        add(separador());
        add(btnCrearProceso);
        add(btnCrearBuffer);
        add(separador());
        add(btnGuardarConfig);
        add(btnCargarConfig);

        btnPausar.setEnabled(false);
    }

    private JSeparator separador() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(2, 24));
        return s;
    }

    public void setCiclo(long ciclo) {
        lblCiclo.setText("Ciclo: " + ciclo);
    }

    /** Habilita Iniciar o Pausar segun si la simulacion esta corriendo. */
    public void setCorriendo(boolean corriendo) {
        btnIniciar.setEnabled(!corriendo);
        btnPausar.setEnabled(corriendo);
    }

    /** Muestra los valores actuales sin avisar a los listeners (llamar antes de conectarlos). */
    public void setValores(int duracionCicloMs, int quantum) {
        spnDuracionCiclo.setValue(duracionCicloMs);
        spnQuantum.setValue(quantum);
    }

    public void alIniciar(ActionListener accion) {
        btnIniciar.addActionListener(accion);
    }

    public void alPausar(ActionListener accion) {
        btnPausar.addActionListener(accion);
    }

    /** La ventana decide que hacer al pulsar "Crear proceso". */
    public void alCrearProceso(ActionListener accion) {
        btnCrearProceso.addActionListener(accion);
    }

    public void alGuardarConfiguracion(ActionListener accion) {
        btnGuardarConfig.addActionListener(accion);
    }

    public void alCargarConfiguracion(ActionListener accion) {
        btnCargarConfig.addActionListener(accion);
    }

    // El JSpinner solo acepta enteros dentro de su rango: si se escribe algo invalido
    // vuelve al ultimo valor valido, asi que aqui solo llegan valores correctos
    public void alCambiarDuracionCiclo(IntConsumer accion) {
        spnDuracionCiclo.addChangeListener(e -> accion.accept((Integer) spnDuracionCiclo.getValue()));
    }

    public void alCambiarQuantum(IntConsumer accion) {
        spnQuantum.addChangeListener(e -> accion.accept((Integer) spnQuantum.getValue()));
    }
}
