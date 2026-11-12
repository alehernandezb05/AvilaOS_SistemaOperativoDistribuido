package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskRequest;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Ventana de prueba visual para validar la simulación del disco,
 * el movimiento del cabezal y la gráfica de desplazamiento vs. tiempo.
 */
public class TestDiskGUI extends JFrame {

    private final DiskScheduler scheduler;
    private final DiskMapPanel mapPanel;
    private final DiskChartPanel chartPanel;

    private Timer simulationTimer;
    private int currentCycle = 0;
    private boolean isRunning = false;

    // Etiquetas de métricas y controles
    private final JLabel lblHead;
    private final JLabel lblMovement;
    private final JLabel lblRequestsServed;
    private final JComboBox<DiskPolicy> comboPolicies;
    private final JButton btnStartPause;

    public TestDiskGUI() {
        setTitle("ÁvilaOS - Prueba Visual de Planificación de Disco");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Inicializar el disco con 200 bloques y el cabezal inicial en 50 (según el JSON del enunciado)
        scheduler = new DiskScheduler(200, 50, 100, DiskPolicy.FIFO);
        cargarSolicitudesDePruebaOficiales();

        mapPanel = new DiskMapPanel(scheduler);
        chartPanel = new DiskChartPanel();

        // Panel Superior de Controles
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));

        controlPanel.add(new JLabel("Política de Disco:"));
        comboPolicies = new JComboBox<>(DiskPolicy.values());
        comboPolicies.setSelectedItem(DiskPolicy.FIFO);
        comboPolicies.addActionListener(e -> {
            DiskPolicy selected = (DiskPolicy) comboPolicies.getSelectedItem();
            if (selected != null) {
                scheduler.setPolicy(selected);
            }
        });
        controlPanel.add(comboPolicies);

        btnStartPause = new JButton("Iniciar Simulación");
        btnStartPause.addActionListener(e -> toggleSimulation());
        controlPanel.add(btnStartPause);

        JButton btnAddRequest = new JButton("+ Agregar Solicitud");
        btnAddRequest.addActionListener(e -> agregarNuevaSolicitud());
        controlPanel.add(btnAddRequest);

        // Panel Central con Mapa de Bloques y Gráfica JFreeChart
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        centerPanel.add(mapPanel);
        centerPanel.add(chartPanel);

        // Panel Inferior con Métricas del Disco
        JPanel metricsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 10));
        lblHead = new JLabel("Cabezal: 50 [UP]");
        lblMovement = new JLabel("Desplazamiento Total: 0 bloques");
        lblRequestsServed = new JLabel("Solicitudes Atendidas: 0");
        metricsPanel.add(lblHead);
        metricsPanel.add(lblMovement);
        metricsPanel.add(lblRequestsServed);

        add(controlPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(metricsPanel, BorderLayout.SOUTH);

        configurarTimer();
    }

    /**
     * Carga el caso de prueba oficial del enunciado:
     * Cabezal inicial en 50 y solicitudes: 95, 180, 34, 119, 11, 123, 62, 64
     */
    private void cargarSolicitudesDePruebaOficiales() {
        int[] bloquesPrueba = {95, 180, 34, 119, 11, 123, 62, 64};
        for (int b : bloquesPrueba) {
            scheduler.addRequest(new DiskRequest(b, "Proc_" + b));
        }

        // Marcar visualmente algunos bloques de la prueba como archivos o swap
        DiskBlock[] blocks = scheduler.getBlocks();
        blocks[95].setOccupied(true);
        blocks[95].setOwnerName("config.sys");
        blocks[95].setContentType("FILE");

        blocks[180].setOccupied(true);
        blocks[180].setOwnerName("video.mp4");
        blocks[180].setContentType("FILE");

        blocks[34].setOccupied(true);
        blocks[34].setOwnerName("notas.txt");
        blocks[34].setContentType("FILE");

        blocks[11].setOccupied(true);
        blocks[11].setOwnerName("boot.bin");
        blocks[11].setContentType("FILE");
    }

    private void configurarTimer() {
        // Ejecuta un ciclo de reloj cada 300 ms (según el JSON del proyecto)
        simulationTimer = new Timer(300, e -> {
            currentCycle++;
            scheduler.tick();

            // Repintar interfaz y actualizar la gráfica
            mapPanel.repaint();
            chartPanel.addDataPoint(currentCycle, scheduler.getTotalHeadMovement(), scheduler.getActivePolicyEnum().name());

            lblHead.setText("Cabezal: " + scheduler.getCurrentHead() + " [" + scheduler.getCurrentDirection() + "]");
            lblMovement.setText("Desplazamiento Total: " + scheduler.getTotalHeadMovement() + " bloques");
            lblRequestsServed.setText("Solicitudes Atendidas: " + scheduler.getTotalRequestsServed());
        });
    }

    private void toggleSimulation() {
        if (isRunning) {
            simulationTimer.stop();
            btnStartPause.setText("Reanudar Simulación");
            isRunning = false;
        } else {
            simulationTimer.start();
            btnStartPause.setText("Pausar Simulación");
            isRunning = true;
        }
    }

    private void agregarNuevaSolicitud() {
        String input = JOptionPane.showInputDialog(this, "Ingrese el número de bloque (0 - 199):");
        if (input != null && !input.trim().isEmpty()) {
            try {
                int block = Integer.parseInt(input.trim());
                if (block >= 0 && block < 200) {
                    scheduler.addRequest(new DiskRequest(block, "Proc_User_" + block));
                    mapPanel.repaint();
                } else {
                    JOptionPane.showMessageDialog(this, "El bloque debe estar entre 0 y 199", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Por favor ingrese un número entero válido", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TestDiskGUI().setVisible(true);
        });
    }
}