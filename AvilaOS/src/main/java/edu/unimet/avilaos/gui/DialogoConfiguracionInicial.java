package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ConfigProceso;
import edu.unimet.avilaos.config.ConfigDisco;
import edu.unimet.avilaos.config.Configuracion;
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

/** Configura el unico computador del Proyecto 2, antes de iniciar el reloj. */
public class DialogoConfiguracionInicial extends JDialog {

    private final JSpinner spnCiclo = spinner(500, Configuracion.MIN_CICLO_MS, Configuracion.MAX_CICLO_MS, 50);
    private final JSpinner spnQuantum = spinner(3, Configuracion.MIN_QUANTUM, Configuracion.MAX_QUANTUM, 1);
    private final JSpinner spnRam = spinner(256, Configuracion.MIN_RAM, Configuracion.MAX_RAM, 16);
    private final JComboBox<PoliticaPlanificacion> comboPolitica = new JComboBox<>(PoliticaPlanificacion.values());
    private final JLabel lblProcesos = new JLabel();
    private ConfigProceso[] procesosCargados = new ConfigProceso[0];
    private Configuracion resultado;
    private ConfigDisco discoCargado;

    public DialogoConfiguracionInicial(Configuracion inicial) {
        super((java.awt.Frame) null, "AvilaOS - Configuracion del computador", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        campos.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        campos.add(new JLabel("Computador:"));
        campos.add(new JLabel("1 (unico)"));
        campos.add(new JLabel("RAM (" + Configuracion.MIN_RAM + "-" + Configuracion.MAX_RAM + "):"));
        campos.add(spnRam);
        campos.add(new JLabel("Politica de CPU:"));
        campos.add(comboPolitica);
        campos.add(new JLabel("Duracion del ciclo en ms (" + Configuracion.MIN_CICLO_MS + "-" + Configuracion.MAX_CICLO_MS + "):"));
        campos.add(spnCiclo);
        campos.add(new JLabel("Quantum (0 sin Round Robin, max. " + Configuracion.MAX_QUANTUM + "):"));
        campos.add(spnQuantum);

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
        add(campos, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        mostrarEnCampos(inicial);
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
    }

    private static JSpinner spinner(int valor, int min, int max, int paso) {
        return new JSpinner(new SpinnerNumberModel(valor, min, max, paso));
    }

    private void mostrarEnCampos(Configuracion config) {
        spnCiclo.setValue(config.getDuracionCicloMs());
        spnQuantum.setValue(config.getQuantum());
        spnRam.setValue(config.getComputador(0).getRam());
        comboPolitica.setSelectedItem(config.getComputador(0).getPolitica());
        procesosCargados = config.getProcesos();
        discoCargado = config.getDisco();
        lblProcesos.setText("Carga inicial: " + procesosCargados.length + " procesos");
    }

    private void cargarArchivo() {
        Path archivo = SelectorArchivoJson.elegirParaAbrir(this);
        if (archivo == null) {
            return;
        }
        try {
            mostrarEnCampos(ArchivoConfiguracion.cargar(archivo));
        } catch (IOException e) {
            error("No se pudo leer el archivo: " + e.getMessage());
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
        }
    }

    private void iniciar() {
        JSpinner[] controles = {spnRam, spnCiclo, spnQuantum};
        String[] nombres = {"RAM", "duracion del ciclo", "quantum"};
        for (int i = 0; i < controles.length; i++) {
            try {
                controles[i].commitEdit();
            } catch (ParseException e) {
                error("El valor de " + nombres[i] + " debe ser un numero entero dentro del rango indicado.");
                return;
            }
        }
        Configuracion config = new Configuracion((Integer) spnCiclo.getValue(), (Integer) spnQuantum.getValue(),
                0, new ConfigComputador[] {
                    new ConfigComputador((Integer) spnRam.getValue(),
                            (PoliticaPlanificacion) comboPolitica.getSelectedItem())
                }, new Configuracion.ConfigBuffer[0], procesosCargados);
        config.setDisco(discoCargado);
        try {
            config.validar();
            resultado = config;
            dispose();
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Configuracion invalida", JOptionPane.WARNING_MESSAGE);
    }

    public Configuracion getConfiguracion() {
        return resultado;
    }
}
