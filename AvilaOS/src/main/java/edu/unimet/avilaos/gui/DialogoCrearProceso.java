package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
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
    public static final int MAX_CICLOS_TIPO = 1000;     // E/S

    // Nombres de las tarjetas del CardLayout
    private static final String SIN_DATOS = "sin datos";
    private static final String DATOS_ES = "es";
    private static final String DATOS_READ = "read";

    private final JTextField txtNombre = new JTextField(15);
    private final JTextField txtSolicitante = new JTextField(15);
    private final JSpinner spnInstrucciones = new JSpinner(new SpinnerNumberModel(20, MIN_INSTRUCCIONES, MAX_INSTRUCCIONES, 1));
    private final JSpinner spnMemoria = new JSpinner(new SpinnerNumberModel(32, MIN_MEMORIA, MAX_MEMORIA, 1));
    private final JSpinner spnPrioridad = new JSpinner(new SpinnerNumberModel(5, MIN_PRIORIDAD, MAX_PRIORIDAD, 1));
    private final JSpinner spnDeadline = new JSpinner(new SpinnerNumberModel(100, MIN_DEADLINE, MAX_DEADLINE, 1));
    private final JComboBox<TipoProceso> comboTipo = new JComboBox<>(new TipoProceso[] {TipoProceso.CPU_BOUND, TipoProceso.IO_BOUND});

    // I/O bound
    private final JSpinner spnCiclosParaES = new JSpinner(new SpinnerNumberModel(5, 1, MAX_CICLOS_TIPO, 1));
    private final JSpinner spnDuracionES = new JSpinner(new SpinnerNumberModel(3, 1, MAX_CICLOS_TIPO, 1));

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel datosTipo = new JPanel(tarjetas);

    private final long cicloActual;
    private final String rutaLectura;
    private PCB procesoCreado = null;

    public DialogoCrearProceso(Frame duenio, long cicloActual) {
        this(duenio, cicloActual, null);
    }

    public DialogoCrearProceso(Frame duenio, long cicloActual, String rutaLectura) {
        super(duenio, "Crear proceso", true);
        this.cicloActual = cicloActual;
        this.rutaLectura = rutaLectura;

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        agregarCampo(campos, "Nombre:", txtNombre);
        agregarCampo(campos, "Instrucciones (" + MIN_INSTRUCCIONES + "-" + MAX_INSTRUCCIONES + "):", spnInstrucciones);
        agregarCampo(campos, "Memoria (" + MIN_MEMORIA + "-" + MAX_MEMORIA + "):", spnMemoria);
        agregarCampo(campos, "Prioridad (" + MIN_PRIORIDAD + " = mas alta):", spnPrioridad);
        agregarCampo(campos, "Deadline en ciclos (" + MIN_DEADLINE + "-" + MAX_DEADLINE + "):", spnDeadline);
        agregarCampo(campos, "Tipo:", comboTipo);
        agregarCampo(campos, "Computador:", new JLabel("1 (unico)"));

        crearTarjetas();
        if (rutaLectura != null) {
            comboTipo.setSelectedItem(TipoProceso.IO_BOUND);
            comboTipo.setEnabled(false);
            txtNombre.setText("Lectura");
            spnInstrucciones.setValue(2);
            spnDeadline.setValue(500);
        }
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

        datosTipo.add(sinDatos, SIN_DATOS);
        datosTipo.add(es, DATOS_ES);
        if (rutaLectura != null) {
            JPanel read = seccion();
            agregarCampo(read, "Operación:", new JLabel("READ"));
            agregarCampo(read, "Ruta:", new JLabel(rutaLectura));
            agregarCampo(read, "Solicitante (vacío para acceso público):", txtSolicitante);
            datosTipo.add(read, DATOS_READ);
        }
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
        if (rutaLectura != null) {
            tarjetas.show(datosTipo, DATOS_READ);
            pack();
            return;
        }
        TipoProceso tipo = (TipoProceso) comboTipo.getSelectedItem();
        String tarjeta = switch (tipo) {
            case CPU_BOUND -> SIN_DATOS;
            case IO_BOUND -> DATOS_ES;
            default -> throw new IllegalArgumentException("Tipo de proceso no disponible en el Proyecto 2.");
        };
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
        String solicitante = txtSolicitante.getText().strip();
        OperacionArchivo operacion = rutaLectura == null ? null
                : new OperacionArchivo(DiskOperation.READ, rutaLectura,
                        solicitante.isEmpty() ? null : solicitante, 0, null);
        PCB pcb = new PCB(txtNombre.getText().trim(), 1, tipo, valor(spnPrioridad),
                valor(spnMemoria), valor(spnInstrucciones), valor(spnDeadline), cicloActual, operacion);
        if (operacion == null && tipo == TipoProceso.IO_BOUND) {
            pcb.setCiclosParaES(valor(spnCiclosParaES));
            pcb.setDuracionES(valor(spnDuracionES));
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
                new String[] {"instrucciones", "memoria", "prioridad", "deadline"},
                new JSpinner[] {spnInstrucciones, spnMemoria, spnPrioridad, spnDeadline});
        if (error != null) {
            return error;
        }
        if (rutaLectura != null) return null;
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
            default -> "Tipo de proceso no disponible en el Proyecto 2.";
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
