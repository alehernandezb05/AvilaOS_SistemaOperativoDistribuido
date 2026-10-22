package edu.unimet.avilaos.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
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
 * y acciones para crear procesos y buffers. Todavia no esta conectada al modelo.
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
}
