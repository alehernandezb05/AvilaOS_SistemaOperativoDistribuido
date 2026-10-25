package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.PoliticaPlanificacion;
import java.awt.BorderLayout;
import java.awt.GridLayout;
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
 * Se crea una instancia por computador, igual que el modelo.
 * Por ahora muestra datos de ejemplo; se conecta al modelo en otro PR.
 */
public class PanelComputador extends JPanel {

    static final String[] COLUMNAS_PCB = {
        "ID", "Nombre", "Tipo", "Estado", "Prioridad", "Memoria", "Deadline", "Restante", "PC", "Motivo"
    };

    private final JLabel lblProceso = new JLabel("-");
    private final JLabel lblPc = new JLabel("-");
    private final JLabel lblPrioridad = new JLabel("-");
    private final JLabel lblDeadline = new JLabel("-");
    private final JLabel lblModo = new JLabel("-");
    private final JProgressBar barraRam = new JProgressBar();
    private final JComboBox<PoliticaPlanificacion> comboPolitica = new JComboBox<>(PoliticaPlanificacion.values());

    public PanelComputador(int idComputador) {
        super(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Computador " + idComputador));

        add(crearPanelEstado(), BorderLayout.NORTH);
        add(crearPestanasColas(), BorderLayout.CENTER);

        cargarDatosEjemplo();
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
        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Nuevos", crearTabla(new Object[][] {
            {7, "Compilador", "CPU_BOUND", "NUEVO", 3, 60, 80, 40, 0, ""}
        }));
        pestanas.addTab("Listos", crearTabla(new Object[][] {
            {4, "Editor", "IO_BOUND", "LISTO", 1, 20, 35, 12, 3, ""},
            {5, "Productor A", "PRODUCTOR", "LISTO", 2, 10, 50, 30, 0, ""}
        }));
        pestanas.addTab("Bloqueados", crearTabla(new Object[][] {
            {6, "Consumidor B", "CONSUMIDOR", "BLOQUEADO", 2, 10, 44, 18, 6, "ESPERA_SEMAFORO"}
        }));
        pestanas.addTab("Terminados", crearTabla(new Object[][] {
            {1, "Init", "CPU_BOUND", "TERMINADO", 1, 5, 0, 0, 10, ""}
        }));
        return pestanas;
    }

    private JScrollPane crearTabla(Object[][] filas) {
        JTable tabla = new JTable(filas, COLUMNAS_PCB);
        tabla.setEnabled(false);
        tabla.setFillsViewportHeight(true);
        return new JScrollPane(tabla);
    }

    // Datos fijos solo para ver el layout; se reemplazan por lecturas del modelo
    private void cargarDatosEjemplo() {
        lblProceso.setText("P3 (Navegador)");
        lblPc.setText("17");
        lblPrioridad.setText("2");
        lblDeadline.setText("41");
        lblModo.setText("USUARIO");
        barraRam.setMaximum(256);
        barraRam.setValue(96);
        barraRam.setString("96 / 256 usados");
    }
}
