package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.so.ModoUsuario;
import edu.unimet.avilaos.so.archivos.SesionUsuario;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/**
 * Barra superior: ciclo global, iniciar/pausar, duracion del ciclo, quantum
 * y acciones para crear procesos. La ventana conecta cada control al Simulador.
 */
public class PanelControles extends JPanel {

    private final JLabel lblCiclo = new JLabel("Ciclo: 0");
    private final JLabel lblCarga = new JLabel();
    private final JButton btnIniciar = new JButton("Iniciar");
    private final JButton btnPausar = new JButton("Pausar");
    // Los spinners ya limitan tipo y rango de lo que se puede escribir
    private final JSpinner spnDuracionCiclo = new JSpinner(new SpinnerNumberModel(500, 50, 5000, 50));
    private final JSpinner spnQuantum = new JSpinner(new SpinnerNumberModel(3, Configuracion.MIN_QUANTUM, Configuracion.MAX_QUANTUM, 1));
    private final JButton btnCrearProceso = new JButton("Crear proceso");
    private final JButton btnGuardarConfig = new JButton("Guardar configuracion");
    private final JButton btnCargarConfig = new JButton("Cargar procesos…");
    private final JComboBox<ModoUsuario> comboModo = new JComboBox<>(ModoUsuario.values());
    private final JTextField txtUsuario = new JTextField("user1", 6);
    private boolean sincronizando;

    public PanelControles() {
        super(new GridLayout(2, 1));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        // El modo de uso va en su propia fila: en la primera ya no cabe sin salirse de la ventana
        JPanel modo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        add(barra);
        add(modo);

        lblCiclo.setFont(lblCiclo.getFont().deriveFont(16f));
        // Ancho fijo para que la barra no salte cuando el ciclo crezca
        lblCiclo.setPreferredSize(new Dimension(130, 24));
        barra.add(lblCiclo);
        barra.add(separador());
        barra.add(btnIniciar);
        barra.add(btnPausar);
        barra.add(separador());
        barra.add(new JLabel("Ciclo (ms):"));
        barra.add(spnDuracionCiclo);
        barra.add(new JLabel("Quantum:"));
        barra.add(spnQuantum);
        barra.add(separador());
        barra.add(btnCrearProceso);
        barra.add(separador());
        barra.add(btnGuardarConfig);
        barra.add(btnCargarConfig);
        modo.add(new JLabel("Modo de uso:"));
        modo.add(comboModo);
        modo.add(new JLabel("Usuario:"));
        modo.add(txtUsuario);
        modo.add(lblCarga);

        btnPausar.setEnabled(false);
        comboModo.setToolTipText("Administrador o usuario; no es el modo de la CPU");
        txtUsuario.setToolTipText("Nombre del usuario en modo usuario (Enter para aplicar)");
        txtUsuario.setEnabled(false);
    }

    /** Avisa el modo elegido y el usuario escrito; la ventana decide si es valido. */
    public void alCambiarModo(BiConsumer<ModoUsuario, String> accion) {
        Runnable avisar = () -> {
            if (!sincronizando) accion.accept((ModoUsuario) comboModo.getSelectedItem(), txtUsuario.getText());
        };
        comboModo.addActionListener(e -> avisar.run());
        txtUsuario.addActionListener(e -> avisar.run());
    }

    /** Muestra la sesion vigente y habilita solo lo que ese modo puede hacer. */
    public void setSesion(SesionUsuario sesion) {
        sincronizando = true;
        try {
            comboModo.setSelectedItem(sesion.modo());
            if (!sesion.esAdministrador()) txtUsuario.setText(sesion.usuario());
        } finally {
            sincronizando = false;
        }
        boolean admin = sesion.esAdministrador();
        txtUsuario.setEnabled(!admin);
        spnQuantum.setEnabled(admin);
        btnCrearProceso.setEnabled(admin);
        btnGuardarConfig.setEnabled(admin);
        btnCargarConfig.setEnabled(admin);
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
    public void setCorriendo(boolean corriendo, String impedimento) {
        btnIniciar.setEnabled(!corriendo && impedimento == null);
        btnPausar.setEnabled(corriendo);
        lblCarga.setText(impedimento);
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
