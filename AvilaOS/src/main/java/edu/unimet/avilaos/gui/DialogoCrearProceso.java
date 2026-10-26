package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.text.ParseException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Vista 1: formulario para crear un proceso. Valida tipo y rango de cada campo
 * y, si todo es correcto, crea el PCB (la ventana lo envia al Simulador).
 * Si hay un error lo explica y no se cierra.
 * Pendiente: los datos propios de cada tipo de proceso (#29).
 */
public class DialogoCrearProceso extends JDialog {

    public static final int MAX_NOMBRE = 30;
    public static final int MIN_INSTRUCCIONES = 1;
    public static final int MAX_INSTRUCCIONES = 10000;
    public static final int MIN_MEMORIA = 1;
    public static final int MAX_MEMORIA = 100000;
    public static final int MIN_PRIORIDAD = 1;    // 1 es la prioridad mas alta
    public static final int MAX_PRIORIDAD = 10;
    public static final int MIN_DEADLINE = 1;
    public static final int MAX_DEADLINE = 100000;

    private final JTextField txtNombre = new JTextField(15);
    private final JSpinner spnInstrucciones = new JSpinner(new SpinnerNumberModel(20, MIN_INSTRUCCIONES, MAX_INSTRUCCIONES, 1));
    private final JSpinner spnMemoria = new JSpinner(new SpinnerNumberModel(32, MIN_MEMORIA, MAX_MEMORIA, 1));
    private final JSpinner spnPrioridad = new JSpinner(new SpinnerNumberModel(5, MIN_PRIORIDAD, MAX_PRIORIDAD, 1));
    private final JSpinner spnDeadline = new JSpinner(new SpinnerNumberModel(100, MIN_DEADLINE, MAX_DEADLINE, 1));
    private final JComboBox<TipoProceso> comboTipo = new JComboBox<>(TipoProceso.values());
    private final JSpinner spnComputador;

    private final long cicloActual;
    private PCB procesoCreado = null;

    public DialogoCrearProceso(Frame duenio, int cantidadComputadores, long cicloActual) {
        super(duenio, "Crear proceso", true);
        this.cicloActual = cicloActual;
        this.spnComputador = new JSpinner(new SpinnerNumberModel(1, 1, cantidadComputadores, 1));

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        agregarCampo(campos, "Nombre:", txtNombre);
        agregarCampo(campos, "Instrucciones (" + MIN_INSTRUCCIONES + "-" + MAX_INSTRUCCIONES + "):", spnInstrucciones);
        agregarCampo(campos, "Memoria (" + MIN_MEMORIA + "-" + MAX_MEMORIA + "):", spnMemoria);
        agregarCampo(campos, "Prioridad (" + MIN_PRIORIDAD + " = mas alta):", spnPrioridad);
        agregarCampo(campos, "Deadline en ciclos (" + MIN_DEADLINE + "-" + MAX_DEADLINE + "):", spnDeadline);
        agregarCampo(campos, "Tipo:", comboTipo);
        agregarCampo(campos, "Computador (1-" + cantidadComputadores + "):", spnComputador);

        JButton btnCrear = new JButton("Crear");
        JButton btnCancelar = new JButton("Cancelar");
        btnCrear.addActionListener(e -> crear());
        btnCancelar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnCancelar);
        botones.add(btnCrear);
        getRootPane().setDefaultButton(btnCrear);

        setLayout(new BorderLayout());
        add(campos, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(duenio);
    }

    private static void agregarCampo(JPanel panel, String etiqueta, java.awt.Component campo) {
        panel.add(new JLabel(etiqueta));
        panel.add(campo);
    }

    /** Valida los campos; si son correctos crea el PCB y cierra, si no muestra el error. */
    private void crear() {
        String error = validar();
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Datos invalidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        procesoCreado = new PCB(txtNombre.getText().trim(), valor(spnComputador),
                (TipoProceso) comboTipo.getSelectedItem(), valor(spnPrioridad), valor(spnMemoria),
                valor(spnInstrucciones), valor(spnDeadline), cicloActual);
        dispose();
    }

    /** Devuelve el mensaje del primer campo invalido, o null si todo es correcto. */
    String validar() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            return "El nombre no puede estar vacio.";
        }
        if (nombre.length() > MAX_NOMBRE) {
            return "El nombre debe tener como maximo " + MAX_NOMBRE + " caracteres.";
        }
        // commitEdit lanza ParseException si lo escrito no es un numero dentro del rango
        String[] nombresCampos = {"instrucciones", "memoria", "prioridad", "deadline", "computador"};
        JSpinner[] spinners = {spnInstrucciones, spnMemoria, spnPrioridad, spnDeadline, spnComputador};
        for (int i = 0; i < spinners.length; i++) {
            try {
                spinners[i].commitEdit();
            } catch (ParseException e) {
                SpinnerNumberModel m = (SpinnerNumberModel) spinners[i].getModel();
                return "El valor de " + nombresCampos[i] + " debe ser un numero entero entre "
                        + m.getMinimum() + " y " + m.getMaximum() + ".";
            }
        }
        if (comboTipo.getSelectedItem() == null) {
            return "Debe elegir un tipo de proceso.";
        }
        return null;
    }

    private static int valor(JSpinner spinner) {
        return (Integer) spinner.getValue();
    }

    /** El proceso creado, o null si el usuario cancelo. */
    public PCB getProcesoCreado() {
        return procesoCreado;
    }
}
