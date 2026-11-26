package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Computador;
import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.hardware.RAM;
import edu.unimet.avilaos.so.ColasSO;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

/**
 * Vista de un computador: CPU, RAM, politica y sus cuatro colas.
 * Se crea una instancia por computador, igual que el modelo. Solo lee del
 * Computador; la ventana llama a actualizar() en cada refresco.
 */
public class PanelComputador extends JPanel {

    private static final String[] NOMBRES_COLAS = {"Nuevos", "Listos", "Bloqueados",
        "Listos/Suspendidos", "Bloqueados/Suspendidos", "Terminados"};

    private final Computador computador;

    private final JLabel lblProceso = new JLabel("-");
    private final JLabel lblPc = new JLabel("-");
    private final JLabel lblPrioridad = new JLabel("-");
    private final JLabel lblDeadline = new JLabel("-");
    private final JLabel lblModo = new JLabel("-");
    private final JProgressBar barraRam = new JProgressBar();
    private final JComboBox<PoliticaPlanificacion> comboPolitica = new JComboBox<>(PoliticaPlanificacion.values());
    private final JTabbedPane pestanas = new JTabbedPane();
    private final ModeloTablaPCB[] modelos = new ModeloTablaPCB[NOMBRES_COLAS.length];
    private boolean actualizando = false;

    public PanelComputador(Computador computador) {
        super(new BorderLayout(5, 5));
        this.computador = computador;
        setBorder(BorderFactory.createTitledBorder("Computador " + computador.getId()));

        add(crearPanelEstado(), BorderLayout.NORTH);
        add(crearPestanasColas(), BorderLayout.CENTER);

    }

    /** Solo el administrador cambia la politica de CPU. */
    public void setPoliticaEditable(boolean editable) {
        comboPolitica.setEnabled(editable);
    }

    /** Avisa cuando el usuario elige otra politica para este computador. */
    public void alCambiarPolitica(Consumer<PoliticaPlanificacion> accion) {
        comboPolitica.addActionListener(e -> {
            if (!actualizando) {
                accion.accept((PoliticaPlanificacion) comboPolitica.getSelectedItem());
            }
        });
    }

    private JPanel crearPanelEstado() {
        JPanel cpu = new JPanel(new GridLayout(0, 2, 8, 2));
        cpu.setBorder(BorderFactory.createTitledBorder("CPU"));
        cpu.add(new JLabel("Proceso:"));
        cpu.add(lblProceso);
        cpu.add(new JLabel("Program counter:"));
        cpu.add(lblPc);
        cpu.add(new JLabel("Prioridad:"));
        cpu.add(lblPrioridad);
        cpu.add(new JLabel("Deadline:"));
        cpu.add(lblDeadline);
        cpu.add(new JLabel("Modo:"));
        cpu.add(lblModo);

        JPanel ram = new JPanel(new BorderLayout());
        ram.setBorder(BorderFactory.createTitledBorder("RAM"));
        barraRam.setStringPainted(true);
        ram.add(barraRam, BorderLayout.CENTER);

        JPanel politica = new JPanel(new BorderLayout());
        politica.setBorder(BorderFactory.createTitledBorder("Politica de planificacion"));
        politica.add(comboPolitica, BorderLayout.CENTER);

        JPanel derecha = new JPanel(new GridLayout(2, 1, 0, 5));
        derecha.add(ram);
        derecha.add(politica);

        JPanel estado = new JPanel(new GridLayout(1, 2, 5, 0));
        estado.add(cpu);
        estado.add(derecha);
        return estado;
    }

    private JTabbedPane crearPestanasColas() {
        for (int i = 0; i < NOMBRES_COLAS.length; i++) {
            modelos[i] = new ModeloTablaPCB();
            JTable tabla = new JTable(modelos[i]);
            tabla.setEnabled(false);
            tabla.setFillsViewportHeight(true);
            pestanas.addTab(NOMBRES_COLAS[i], new JScrollPane(tabla));
        }
        return pestanas;
    }

    /** Captura solo valores; la accion devuelta se ejecuta en EDT sin semaforos. */
    Runnable capturar() {
        CPU cpu = computador.getCpu();
        PCB actual = cpu.getProcesoActual();
        String nombre = actual == null ? "(libre)" : actual.toString();
        String pc = actual == null ? "-" : String.valueOf(actual.getProgramCounter());
        String prioridad = actual == null ? "-" : String.valueOf(actual.getPrioridad());
        String deadline = actual == null ? "-" : String.valueOf(actual.getDeadline());
        String modo = cpu.getModo().toString();
        RAM ram = computador.getRam();
        int total = ram.getTotal(), usada = ram.getUsada();
        PoliticaPlanificacion politica = computador.getNucleo().getEstrategia().getPolitica();
        ColasSO colas = computador.getNucleo().getColas();
        Object[][][] filas = {
            copiarCola(colas.cantidadNuevos(), colas::obtenerNuevo),
            copiarCola(colas.cantidadListos(), colas::obtenerListo),
            copiarCola(colas.cantidadBloqueados(), colas::obtenerBloqueado),
            copiarCola(colas.cantidadListosSuspendidos(), colas::obtenerListoSuspendido),
            copiarCola(colas.cantidadBloqueadosSuspendidos(), colas::obtenerBloqueadoSuspendido),
            copiarCola(colas.cantidadTerminados(), colas::obtenerTerminado)
        };
        return () -> {
            lblProceso.setText(nombre);
            lblPc.setText(pc);
            lblPrioridad.setText(prioridad);
            lblDeadline.setText(deadline);
            lblModo.setText(modo);
            barraRam.setMaximum(total);
            barraRam.setValue(usada);
            barraRam.setString(usada + " / " + total + " usados");
            actualizando = true;
            try {
                if (comboPolitica.getSelectedItem() != politica && !comboPolitica.isPopupVisible())
                    comboPolitica.setSelectedItem(politica);
            } finally { actualizando = false; }
            for (int i = 0; i < filas.length; i++) {
                modelos[i].setFilas(filas[i]);
                pestanas.setTitleAt(i, NOMBRES_COLAS[i] + " (" + filas[i].length + ")");
            }
        };
    }

    private static Object[][] copiarCola(int cantidad, IntFunction<PCB> obtener) {
        PCB[] temporal = new PCB[cantidad];
        for (int i = 0; i < cantidad; i++) temporal[i] = obtener.apply(i);
        return ModeloTablaPCB.copiar(temporal);
    }
}
