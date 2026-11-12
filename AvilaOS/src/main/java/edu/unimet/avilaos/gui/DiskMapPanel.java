package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.JPanel;

/**
 * Panel visual que dibuja el mapa de bloques del disco y la posición del cabezal.
 */
public class DiskMapPanel extends JPanel {

    private DiskScheduler diskScheduler;
    private static final int BLOCK_SIZE = 14;
    private static final int BLOCK_MARGIN = 2;

    public DiskMapPanel(DiskScheduler diskScheduler) {
        this.diskScheduler = diskScheduler;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(600, 250));
    }

    public void setDiskScheduler(DiskScheduler diskScheduler) {
        this.diskScheduler = diskScheduler;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (diskScheduler == null) {
            g.drawString("No hay disco cargado", 20, 20);
            return;
        }

        DiskBlock[] blocks = diskScheduler.getBlocks();
        int currentHead = diskScheduler.getCurrentHead();
        int totalBlocks = blocks.length;

        int cols = Math.max(1, getWidth() / (BLOCK_SIZE + BLOCK_MARGIN));
        
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(Color.BLACK);
        g.drawString("Estado del Disco (Bloques libres / ocupados) - Cabezal en bloque: " + currentHead 
                + " [" + diskScheduler.getCurrentDirection() + "]", 10, 15);

        int startY = 30;
        for (int i = 0; i < totalBlocks; i++) {
            int row = i / cols;
            int col = i % cols;

            int x = col * (BLOCK_SIZE + BLOCK_MARGIN) + 10;
            int y = row * (BLOCK_SIZE + BLOCK_MARGIN) + startY;

            DiskBlock block = blocks[i];

            // Determinar color de relleno
            if (i == currentHead) {
                g.setColor(Color.ORANGE); // Bloque donde está el cabezal
            } else if (!block.isOccupied()) {
                g.setColor(new Color(200, 230, 201)); // Verde claro: Libre
            } else if ("SWAP".equalsIgnoreCase(block.getContentType())) {
                g.setColor(new Color(225, 190, 231)); // Morado: Área Swap
            } else {
                g.setColor(new Color(239, 154, 154)); // Rojo claro: Archivo ocupado
            }

            g.fillRect(x, y, BLOCK_SIZE, BLOCK_SIZE);

            // Borde
            if (i == currentHead) {
                g.setColor(Color.RED);
                g.drawRect(x - 1, y - 1, BLOCK_SIZE + 1, BLOCK_SIZE + 1);
            } else {
                g.setColor(Color.GRAY);
                g.drawRect(x, y, BLOCK_SIZE, BLOCK_SIZE);
            }
        }
    }
}
