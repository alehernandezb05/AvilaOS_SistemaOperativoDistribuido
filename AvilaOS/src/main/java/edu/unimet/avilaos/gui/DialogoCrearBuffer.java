package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.so.Buffer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.text.ParseException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Formulario para crear un buffer: capacidad y computador anfitrion. Muestra la
 * RAM libre del anfitrion elegido. Si el Simulador no puede crearlo (por ejemplo,
 * no hay RAM suficiente) explica el motivo y no se cierra.
 */
public class DialogoCrearBuffer extends JDialog {

    private final Simulador simulador;
    private final JSpinner spnCapacidad = new JSpinner(
            new SpinnerNumberModel(5, Buffer.MIN_CAPACIDAD, Buffer.MAX_CAPACIDAD, 1));
    private final JSpinner spnAnfitrion;
    private final JLabel lblRamLibre = new JLabel();

    private Buffer bufferCreado = null;

    public DialogoCrearBuffer(Frame duenio, Simulador simulador) {
        super(duenio, "Crear buffer", true);
        this.simulador = simulador;
        int cantidad = simulador.getCantidadComputadores();
        this.spnAnfitrion = new JSpinner(new SpinnerNumberModel(1, 1, cantidad, 1));

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        campos.add(new JLabel("Capacidad (" + Buffer.MIN_CAPACIDAD + "-" + Buffer.MAX_CAPACIDAD + "):"));
        campos.add(spnCapacidad);
        campos.add(new JLabel("Computador anfitrion (1-" + cantidad + "):"));
        campos.add(spnAnfitrion);
        campos.add(new JLabel("Ocupa en RAM:"));
        campos.add(new JLabel(Buffer.MEMORIA_POR_ELEMENTO + " por elemento de capacidad"));
        campos.add(new JLabel("RAM libre en el anfitrion:"));
        campos.add(lblRamLibre);
        spnAnfitrion.addChangeListener(e -> mostrarRamLibre());
        mostrarRamLibre();

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

    private void mostrarRamLibre() {
        int anfitrion = (Integer) spnAnfitrion.getValue();
        lblRamLibre.setText(String.valueOf(simulador.getComputador(anfitrion).getRam().getLibre()));
    }

    /** Valida los campos y pide al Simulador que cree el buffer; si falla, lo explica. */
    private void crear() {
        String error = validar();
        if (error == null) {
            try {
                bufferCreado = simulador.crearBuffer((Integer) spnCapacidad.getValue(),
                        (Integer) spnAnfitrion.getValue());
                dispose();
                return;
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
        JOptionPane.showMessageDialog(this, error, "No se pudo crear el buffer", JOptionPane.WARNING_MESSAGE);
        mostrarRamLibre();
    }

    /** Devuelve el mensaje del primer campo invalido, o null si todo es correcto. */
    String validar() {
        String[] nombres = {"capacidad", "computador anfitrion"};
        JSpinner[] spinners = {spnCapacidad, spnAnfitrion};
        for (int i = 0; i < spinners.length; i++) {
            try {
                spinners[i].commitEdit();
            } catch (ParseException e) {
                SpinnerNumberModel m = (SpinnerNumberModel) spinners[i].getModel();
                return "El valor de " + nombres[i] + " debe ser un numero entero entre "
                        + m.getMinimum() + " y " + m.getMaximum() + ".";
            }
        }
        return null;
    }

    /** El buffer creado, o null si el usuario cancelo. */
    public Buffer getBufferCreado() {
        return bufferCreado;
    }
}
