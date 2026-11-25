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
import java.util.function.IntSupplier;
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

        actualizar();
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

    /** Lee el estado actual del computador y lo muestra. Se llama desde el hilo de Swing. */
    public void actualizar() {
        CPU cpu = computador.getCpu();
        PCB actual = cpu.getProcesoActual();
        if (actual == null) {
            lblProceso.setText("(libre)");
            lblPc.setText("-");
            lblPrioridad.setText("-");
            lblDeadline.setText("-");
        } else {
            lblProceso.setText(actual.toString());
            lblPc.setText(String.valueOf(actual.getProgramCounter()));
            lblPrioridad.setText(String.valueOf(actual.getPrioridad()));
            lblDeadline.setText(String.valueOf(actual.getDeadline()));
        }
        lblModo.setText(cpu.getModo().toString());

        RAM ram = computador.getRam();
        barraRam.setMaximum(ram.getTotal());
        barraRam.setValue(ram.getUsada());
        barraRam.setString(ram.getUsada() + " / " + ram.getTotal() + " usados");

        // Al refrescar el combo no debe avisar, o cada refresco volveria a cambiar la politica
        PoliticaPlanificacion politica = computador.getNucleo().getEstrategia().getPolitica();
        if (comboPolitica.getSelectedItem() != politica) {
            actualizando = true;
            comboPolitica.setSelectedItem(politica);
            actualizando = false;
        }

        ColasSO colas = computador.getNucleo().getColas();
        actualizarCola(0, colas::cantidadNuevos, colas::obtenerNuevo);
        actualizarCola(1, colas::cantidadListos, colas::obtenerListo);
        actualizarCola(2, colas::cantidadBloqueados, colas::obtenerBloqueado);
        actualizarCola(3, colas::cantidadListosSuspendidos, colas::obtenerListoSuspendido);
        actualizarCola(4, colas::cantidadBloqueadosSuspendidos, colas::obtenerBloqueadoSuspendido);
        actualizarCola(5, colas::cantidadTerminados, colas::obtenerTerminado);
    }

    /** Copia la cola a un arreglo y pone la cantidad en el titulo de la pestana. */
    private void actualizarCola(int indice, IntSupplier cantidad, IntFunction<PCB> obtener) {
        PCB[] copia = copiarCola(cantidad.getAsInt(), obtener);
        modelos[indice].setProcesos(copia);
        pestanas.setTitleAt(indice, NOMBRES_COLAS[indice] + " (" + copia.length + ")");
    }

    // El reloj puede cambiar la cola mientras se copia: si un indice ya no existe, se corta ahi
    private static PCB[] copiarCola(int cantidad, IntFunction<PCB> obtener) {
        PCB[] temporal = new PCB[cantidad];
        int n = 0;
        while (n < cantidad) {
            PCB p = obtener.apply(n);
            if (p == null) {
                break;
            }
            temporal[n++] = p;
        }
        if (n == cantidad) {
            return temporal;
        }
        PCB[] recortado = new PCB[n];
        System.arraycopy(temporal, 0, recortado, 0, n);
        return recortado;
    }
}
