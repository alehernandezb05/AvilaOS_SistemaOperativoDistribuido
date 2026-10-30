package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ConfigProceso;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.Configuracion.ConfigBuffer;
import edu.unimet.avilaos.config.Configuracion.ConfigComputador;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Configuracion del cluster antes de iniciar la simulacion: cantidad de
 * computadores, RAM y politica de cada uno, duracion del ciclo, quantum y
 * latencia de red. Tambien permite partir de un archivo JSON.
 */
public class DialogoConfiguracionInicial extends JDialog {

    private final JSpinner spnCantidad = spinner(2, Configuracion.MIN_COMPUTADORES, Configuracion.MAX_COMPUTADORES, 1);
    private final JSpinner spnCiclo = spinner(500, Configuracion.MIN_CICLO_MS, Configuracion.MAX_CICLO_MS, 50);
    private final JSpinner spnQuantum = spinner(3, Configuracion.MIN_QUANTUM, Configuracion.MAX_QUANTUM, 1);
    private final JSpinner spnLatencia = spinner(2, Configuracion.MIN_LATENCIA, Configuracion.MAX_LATENCIA, 1);

    // Se crean todas las filas posibles y solo se agregan al panel las de la cantidad elegida
    private final JPanel filas = new JPanel(new GridLayout(0, 3, 8, 4));
    private final JLabel[] lblComputador = new JLabel[Configuracion.MAX_COMPUTADORES];
    private final JSpinner[] spnRam = new JSpinner[Configuracion.MAX_COMPUTADORES];
    @SuppressWarnings("unchecked")
    private final JComboBox<PoliticaPlanificacion>[] comboPolitica = new JComboBox[Configuracion.MAX_COMPUTADORES];

    private final JLabel lblProcesos = new JLabel();
    // Carga inicial del ultimo archivo cargado
    private ConfigBuffer[] buffersCargados = new ConfigBuffer[0];
    private ConfigProceso[] procesosCargados = new ConfigProceso[0];
    private Configuracion resultado = null;

    public DialogoConfiguracionInicial(Configuracion inicial) {
        super((java.awt.Frame) null, "AvilaOS - Configuracion del cluster", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel generales = new JPanel(new GridLayout(0, 2, 8, 6));
        generales.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        generales.add(new JLabel("Computadores (" + Configuracion.MIN_COMPUTADORES + "-" + Configuracion.MAX_COMPUTADORES + "):"));
        generales.add(spnCantidad);
        generales.add(new JLabel("Duracion del ciclo en ms (" + Configuracion.MIN_CICLO_MS + "-" + Configuracion.MAX_CICLO_MS + "):"));
        generales.add(spnCiclo);
        generales.add(new JLabel("Quantum de Round Robin (" + Configuracion.MIN_QUANTUM + "-" + Configuracion.MAX_QUANTUM + "):"));
        generales.add(spnQuantum);
        generales.add(new JLabel("Latencia de red en ciclos (" + Configuracion.MIN_LATENCIA + "-" + Configuracion.MAX_LATENCIA + "):"));
        generales.add(spnLatencia);

        filas.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 0, 10),
                BorderFactory.createTitledBorder("Computadores (RAM " + Configuracion.MIN_RAM + "-" + Configuracion.MAX_RAM + ")")));
        for (int i = 0; i < Configuracion.MAX_COMPUTADORES; i++) {
            lblComputador[i] = new JLabel("Computador " + (i + 1));
            spnRam[i] = spinner(256, Configuracion.MIN_RAM, Configuracion.MAX_RAM, 16);
            comboPolitica[i] = new JComboBox<>(PoliticaPlanificacion.values());
        }
        spnCantidad.addChangeListener(e -> mostrarFilas());

        JButton btnCargar = new JButton("Cargar desde archivo...");
        JButton btnSalir = new JButton("Salir");
        JButton btnIniciar = new JButton("Iniciar");
        btnCargar.addActionListener(e -> cargarArchivo());
        btnSalir.addActionListener(e -> dispose());
        btnIniciar.addActionListener(e -> iniciar());
        JPanel botones = new JPanel(new BorderLayout());
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT));
        izquierda.add(btnCargar);
        izquierda.add(lblProcesos);
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        derecha.add(btnSalir);
        derecha.add(btnIniciar);
        botones.add(izquierda, BorderLayout.WEST);
        botones.add(derecha, BorderLayout.EAST);
        getRootPane().setDefaultButton(btnIniciar);

        setLayout(new BorderLayout());
        add(generales, BorderLayout.NORTH);
        add(filas, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);

        mostrarEnCampos(inicial);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private static JSpinner spinner(int valor, int min, int max, int paso) {
        return new JSpinner(new SpinnerNumberModel(valor, min, max, paso));
    }

    /** Deja en el panel solo las filas de los computadores elegidos y ajusta el tamano. */
    private void mostrarFilas() {
        int cantidad = (Integer) spnCantidad.getValue();
        filas.removeAll();
        for (int i = 0; i < cantidad; i++) {
            filas.add(lblComputador[i]);
            filas.add(spnRam[i]);
            filas.add(comboPolitica[i]);
        }
        filas.revalidate();
        pack();
    }

    private void mostrarEnCampos(Configuracion config) {
        spnCantidad.setValue(config.getCantidadComputadores());
        spnCiclo.setValue(config.getDuracionCicloMs());
        spnQuantum.setValue(config.getQuantum());
        spnLatencia.setValue(config.getLatenciaRed());
        for (int i = 0; i < config.getCantidadComputadores(); i++) {
            spnRam[i].setValue(config.getComputador(i).getRam());
            comboPolitica[i].setSelectedItem(config.getComputador(i).getPolitica());
        }
        mostrarFilas();
    }

    /** Rellena el dialogo con un archivo; si no es valido lo explica y no cambia nada. */
    private void cargarArchivo() {
        Path archivo = SelectorArchivoJson.elegirParaAbrir(this);
        if (archivo == null) {
            return;
        }
        try {
            Configuracion cargada = ArchivoConfiguracion.cargar(archivo);
            mostrarEnCampos(cargada);
            buffersCargados = cargada.getBuffers();
            procesosCargados = cargada.getProcesos();
            lblProcesos.setText("Carga inicial: " + buffersCargados.length + " buffers y "
                    + procesosCargados.length + " procesos");
        } catch (IOException e) {
            error("No se pudo leer el archivo: " + e.getMessage());
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
        }
    }

    /** Arma la configuracion con los campos; si es valida cierra el dialogo. */
    private void iniciar() {
        JSpinner[] generales = {spnCantidad, spnCiclo, spnQuantum, spnLatencia};
        String[] nombres = {"computadores", "duracion del ciclo", "quantum", "latencia de red"};
        for (int i = 0; i < generales.length; i++) {
            if (!confirmar(generales[i])) {
                error("El valor de " + nombres[i] + " debe ser un numero entero dentro del rango indicado.");
                return;
            }
        }
        int cantidad = (Integer) spnCantidad.getValue();
        ConfigComputador[] computadores = new ConfigComputador[cantidad];
        for (int i = 0; i < cantidad; i++) {
            if (!confirmar(spnRam[i])) {
                error("La RAM del computador " + (i + 1) + " debe ser un numero entero dentro del rango indicado.");
                return;
            }
            computadores[i] = new ConfigComputador((Integer) spnRam[i].getValue(),
                    (PoliticaPlanificacion) comboPolitica[i].getSelectedItem());
        }
        Configuracion config = new Configuracion((Integer) spnCiclo.getValue(), (Integer) spnQuantum.getValue(),
                (Integer) spnLatencia.getValue(), computadores, buffersCargados, procesosCargados);
        try {
            config.validar();
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
            return;
        }
        resultado = config;
        dispose();
    }

    // commitEdit lanza ParseException si lo escrito no es un numero dentro del rango
    private static boolean confirmar(JSpinner spinner) {
        try {
            spinner.commitEdit();
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Configuracion invalida", JOptionPane.WARNING_MESSAGE);
    }

    /** La configuracion elegida, o null si el usuario salio. */
    public Configuracion getConfiguracion() {
        return resultado;
    }
}
