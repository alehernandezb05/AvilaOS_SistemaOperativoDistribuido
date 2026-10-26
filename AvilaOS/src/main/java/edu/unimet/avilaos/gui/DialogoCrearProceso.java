package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
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
 * Si hay un error lo explica y no se cierra. La seccion "Datos segun el tipo"
 * muestra solo los campos del tipo elegido.
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
    public static final int MAX_CICLOS_TIPO = 1000;     // E/S y produccion/consumo
    public static final int MAX_ELEMENTOS = 10000;

    // Nombres de las tarjetas del CardLayout
    private static final String SIN_DATOS = "sin datos";
    private static final String DATOS_ES = "es";
    private static final String DATOS_BUFFER = "buffer";

    private final JTextField txtNombre = new JTextField(15);
    private final JSpinner spnInstrucciones = new JSpinner(new SpinnerNumberModel(20, MIN_INSTRUCCIONES, MAX_INSTRUCCIONES, 1));
    private final JSpinner spnMemoria = new JSpinner(new SpinnerNumberModel(32, MIN_MEMORIA, MAX_MEMORIA, 1));
    private final JSpinner spnPrioridad = new JSpinner(new SpinnerNumberModel(5, MIN_PRIORIDAD, MAX_PRIORIDAD, 1));
    private final JSpinner spnDeadline = new JSpinner(new SpinnerNumberModel(100, MIN_DEADLINE, MAX_DEADLINE, 1));
    private final JComboBox<TipoProceso> comboTipo = new JComboBox<>(TipoProceso.values());
    private final JSpinner spnComputador;

    // I/O bound
    private final JSpinner spnCiclosParaES = new JSpinner(new SpinnerNumberModel(5, 1, MAX_CICLOS_TIPO, 1));
    private final JSpinner spnDuracionES = new JSpinner(new SpinnerNumberModel(3, 1, MAX_CICLOS_TIPO, 1));

    // Productor y consumidor
    private final JComboBox<String> comboBuffer;
    private final JLabel lblCiclosPorElemento = new JLabel();
    private final JSpinner spnCiclosPorElemento = new JSpinner(new SpinnerNumberModel(4, 1, MAX_CICLOS_TIPO, 1));
    private final JSpinner spnElementos = new JSpinner(new SpinnerNumberModel(10, 1, MAX_ELEMENTOS, 1));

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel datosTipo = new JPanel(tarjetas);

    private final long cicloActual;
    private PCB procesoCreado = null;

    /** @param buffersDisponibles IDs de los buffers que pueden usar productores y consumidores */
    public DialogoCrearProceso(Frame duenio, int cantidadComputadores, long cicloActual, String[] buffersDisponibles) {
        super(duenio, "Crear proceso", true);
        this.cicloActual = cicloActual;
        this.spnComputador = new JSpinner(new SpinnerNumberModel(1, 1, cantidadComputadores, 1));
        this.comboBuffer = new JComboBox<>(buffersDisponibles);

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        agregarCampo(campos, "Nombre:", txtNombre);
        agregarCampo(campos, "Instrucciones (" + MIN_INSTRUCCIONES + "-" + MAX_INSTRUCCIONES + "):", spnInstrucciones);
        agregarCampo(campos, "Memoria (" + MIN_MEMORIA + "-" + MAX_MEMORIA + "):", spnMemoria);
        agregarCampo(campos, "Prioridad (" + MIN_PRIORIDAD + " = mas alta):", spnPrioridad);
        agregarCampo(campos, "Deadline en ciclos (" + MIN_DEADLINE + "-" + MAX_DEADLINE + "):", spnDeadline);
        agregarCampo(campos, "Tipo:", comboTipo);
        agregarCampo(campos, "Computador (1-" + cantidadComputadores + "):", spnComputador);

        crearTarjetas();
        comboTipo.addActionListener(e -> mostrarDatosDelTipo());

        JButton btnCrear = new JButton("Crear");
        JButton btnCancelar = new JButton("Cancelar");
        btnCrear.addActionListener(e -> crear());
        btnCancelar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnCancelar);
        botones.add(btnCrear);
        getRootPane().setDefaultButton(btnCrear);

        setLayout(new BorderLayout());
        add(campos, BorderLayout.NORTH);
        add(datosTipo, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        mostrarDatosDelTipo();
        setResizable(false);
        setLocationRelativeTo(duenio);
    }

    private void crearTarjetas() {
        JPanel sinDatos = seccion();
        sinDatos.setLayout(new BorderLayout());
        sinDatos.add(new JLabel("Sin datos adicionales: sus ciclos de CPU son las instrucciones."), BorderLayout.CENTER);

        JPanel es = seccion();
        agregarCampo(es, "Cada cuantos ciclos hace E/S (1-" + MAX_CICLOS_TIPO + "):", spnCiclosParaES);
        agregarCampo(es, "Duracion de cada E/S en ciclos (1-" + MAX_CICLOS_TIPO + "):", spnDuracionES);

        JPanel buffer = seccion();
        agregarCampo(buffer, "Buffer:", comboBuffer);
        agregarCampo(buffer, lblCiclosPorElemento, spnCiclosPorElemento);
        agregarCampo(buffer, "Elementos para terminar (1-" + MAX_ELEMENTOS + "):", spnElementos);

        datosTipo.add(sinDatos, SIN_DATOS);
        datosTipo.add(es, DATOS_ES);
        datosTipo.add(buffer, DATOS_BUFFER);
    }

    private static JPanel seccion() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 0, 10),
                BorderFactory.createTitledBorder("Datos segun el tipo")));
        return panel;
    }

    /** Muestra la tarjeta del tipo elegido y ajusta el tamano de la ventana. */
    private void mostrarDatosDelTipo() {
        TipoProceso tipo = (TipoProceso) comboTipo.getSelectedItem();
        String tarjeta = switch (tipo) {
            case CPU_BOUND -> SIN_DATOS;
            case IO_BOUND -> DATOS_ES;
            case PRODUCTOR, CONSUMIDOR -> DATOS_BUFFER;
        };
        String verbo = tipo == TipoProceso.PRODUCTOR ? "produce" : "consume";
        lblCiclosPorElemento.setText("Cada cuantos ciclos " + verbo + " un elemento (1-" + MAX_CICLOS_TIPO + "):");
        tarjetas.show(datosTipo, tarjeta);
        pack();
    }

    private static void agregarCampo(JPanel panel, String etiqueta, Component campo) {
        agregarCampo(panel, new JLabel(etiqueta), campo);
    }

    private static void agregarCampo(JPanel panel, JLabel etiqueta, Component campo) {
        panel.add(etiqueta);
        panel.add(campo);
    }

    /** Valida los campos; si son correctos crea el PCB y cierra, si no muestra el error. */
    private void crear() {
        String error = validar();
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Datos invalidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        TipoProceso tipo = (TipoProceso) comboTipo.getSelectedItem();
        PCB pcb = new PCB(txtNombre.getText().trim(), valor(spnComputador), tipo, valor(spnPrioridad),
                valor(spnMemoria), valor(spnInstrucciones), valor(spnDeadline), cicloActual);
        switch (tipo) {
            case IO_BOUND -> {
                pcb.setCiclosParaES(valor(spnCiclosParaES));
                pcb.setDuracionES(valor(spnDuracionES));
            }
            case PRODUCTOR, CONSUMIDOR -> {
                pcb.setIdBuffer((String) comboBuffer.getSelectedItem());
                pcb.setCiclosPorElemento(valor(spnCiclosPorElemento));
                pcb.setElementosTotales(valor(spnElementos));
            }
            case CPU_BOUND -> { }
        }
        procesoCreado = pcb;
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
        String error = validarSpinners(
                new String[] {"instrucciones", "memoria", "prioridad", "deadline", "computador"},
                new JSpinner[] {spnInstrucciones, spnMemoria, spnPrioridad, spnDeadline, spnComputador});
        if (error != null) {
            return error;
        }
        TipoProceso tipo = (TipoProceso) comboTipo.getSelectedItem();
        if (tipo == null) {
            return "Debe elegir un tipo de proceso.";
        }
        // Solo se validan los campos del tipo elegido
        return switch (tipo) {
            case CPU_BOUND -> null;
            case IO_BOUND -> validarSpinners(
                    new String[] {"cada cuantos ciclos hace E/S", "duracion de la E/S"},
                    new JSpinner[] {spnCiclosParaES, spnDuracionES});
            case PRODUCTOR, CONSUMIDOR -> {
                if (comboBuffer.getItemCount() == 0) {
                    yield "No hay buffers creados. Crea uno con \"Crear buffer\" antes de crear "
                            + "productores o consumidores.";
                }
                yield validarSpinners(
                        new String[] {"cada cuantos ciclos por elemento", "elementos para terminar"},
                        new JSpinner[] {spnCiclosPorElemento, spnElementos});
            }
        };
    }

    // commitEdit lanza ParseException si lo escrito no es un numero dentro del rango
    private static String validarSpinners(String[] nombres, JSpinner[] spinners) {
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

    private static int valor(JSpinner spinner) {
        return (Integer) spinner.getValue();
    }

    /** El proceso creado, o null si el usuario cancelo. */
    public PCB getProcesoCreado() {
        return procesoCreado;
    }
}
