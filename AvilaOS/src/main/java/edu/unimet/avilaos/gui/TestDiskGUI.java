package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.DiskSnapshot;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Prueba del disco del computador; el Timer de Swing solo observa el reloj global. */
public class TestDiskGUI extends JFrame {

    private final Simulador simulador = new Simulador(Configuracion.porDefecto());
    private final DiskScheduler scheduler = simulador.getDisco();
    private final DiskMapPanel mapPanel = new DiskMapPanel(scheduler);
    private final DiskChartPanel chartPanel = new DiskChartPanel();
    private final JLabel lblHead = new JLabel();
    private final JLabel lblMovement = new JLabel();
    private final JLabel lblRequestsServed = new JLabel();
    private final JButton btnStartPause = new JButton("Iniciar simulacion");
    private final Timer refresh;
    private long lastCycle = -1;
    private int nextProcessId = 1;

    public TestDiskGUI() {
        super("AvilaOS - Prueba de disco con reloj global (FIFO/SSTF)");
        setSize(1100, 750);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        simulador.setDuracionCicloMs(50);
        int[] officialBlocks = {95, 180, 34, 119, 11, 123, 62, 64};
        for (int block : officialBlocks) enqueue(block);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        controls.add(new JLabel("Politica:"));
        JComboBox<DiskPolicy> policies = new JComboBox<>(new DiskPolicy[] {DiskPolicy.FIFO, DiskPolicy.SSTF});
        policies.addActionListener(e -> simulador.cambiarPoliticaDisco((DiskPolicy) policies.getSelectedItem()));
        controls.add(policies);
        btnStartPause.addActionListener(e -> {
            if (simulador.estaCorriendo()) simulador.pausar();
            else simulador.iniciar();
            btnStartPause.setText(simulador.estaCorriendo() ? "Pausar simulacion" : "Reanudar simulacion");
        });
        controls.add(btnStartPause);
        JButton add = new JButton("Agregar solicitud de prueba");
        add.addActionListener(e -> agregarNuevaSolicitud());
        controls.add(add);
        controls.add(new JLabel("Posiciones/ciclo:"));
        JSpinner speed = new JSpinner(new SpinnerNumberModel(1, 1, scheduler.getTotalBlocks(), 1));
        speed.addChangeListener(e -> simulador.setVelocidadDisco((Integer) speed.getValue()));
        controls.add(speed);
        add(controls, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(2, 1, 5, 5));
        center.add(mapPanel);
        center.add(chartPanel);
        add(center, BorderLayout.CENTER);
        JPanel status = new JPanel(new FlowLayout());
        status.add(lblHead);
        status.add(lblMovement);
        status.add(lblRequestsServed);
        add(status, BorderLayout.SOUTH);

        refresh = new Timer(100, e -> actualizar());
        refresh.start();
        actualizar();
    }

    private void enqueue(int block) {
        int process = nextProcessId++;
        scheduler.addRequest(new DiskRequest(process, "Prueba_" + process, DiskOperation.READ, block));
    }

    private void actualizar() {
        DiskSnapshot state = scheduler.getSnapshot();
        mapPanel.repaint();
        if (state.cycle() > lastCycle) {
            chartPanel.addDataPoint(state.cycle(), state.totalHeadMovement(), state.policy().name());
            lastCycle = state.cycle();
        }
        lblHead.setText("Ciclo: " + state.cycle() + " | Cabezal: " + state.currentHead() + " [" + state.direction() + "]");
        lblMovement.setText("Desplazamiento: " + state.totalHeadMovement());
        lblRequestsServed.setText("Atendidas: " + state.totalRequestsServed() + " | En cola: " + state.pendingCount());
        // Esta prueba no tiene PCBs reales; el nucleo consumira los resultados en la integracion de E/S.
        while (scheduler.pollCompleted() != null) { }
    }

    private void agregarNuevaSolicitud() {
        String input = JOptionPane.showInputDialog(this, "Bloque (0-" + (scheduler.getTotalBlocks() - 1) + "):");
        if (input == null) return;
        try {
            enqueue(Integer.parseInt(input.trim()));
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Bloque invalido: " + e.getMessage(), "Solicitud invalida", JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    public void dispose() {
        refresh.stop();
        simulador.detener();
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TestDiskGUI().setVisible(true));
    }
}
