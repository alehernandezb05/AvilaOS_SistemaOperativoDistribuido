package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;

/**
 * Panel visual que dibuja el mapa de bloques del disco y la posición del cabezal.
 */
public class DiskMapPanel extends JPanel {

    static final Color COLOR_ARCHIVO = new Color(239, 154, 154);
    private DiskScheduler diskScheduler;
    private static final int BLOCK_SIZE = 14;
    private static final int BLOCK_MARGIN = 2;
    private static final int BLOCK_STEP = BLOCK_SIZE + BLOCK_MARGIN;
    private static final int START_X = 10;
    private static final int START_Y = 30;

    public DiskMapPanel(DiskScheduler diskScheduler) {
        this.diskScheduler = diskScheduler;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(600, 250));
        setToolTipText("");
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

        int cols = columns();
        
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(Color.BLACK);
        g.drawString("Estado del Disco (Bloques libres / ocupados) - Cabezal en bloque: " + currentHead 
                + " [" + diskScheduler.getCurrentDirection() + "]", 10, 15);

        for (int i = 0; i < totalBlocks; i++) {
            int row = i / cols;
            int col = i % cols;

            int x = col * BLOCK_STEP + START_X;
            int y = row * BLOCK_STEP + START_Y;

            DiskBlock block = blocks[i];

            g.setColor(i == currentHead ? Color.ORANGE : switch (block.contentType()) {
                case FREE -> new Color(200, 230, 201);
                case FILE -> COLOR_ARCHIVO;
                case SWAP_RESERVED -> new Color(225, 190, 231);
                case SWAP_OCCUPIED -> new Color(171, 117, 188);
            });

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

    @Override
    public String getToolTipText(MouseEvent event) {
        int x = event.getX() - START_X;
        int y = event.getY() - START_Y;
        int cols = columns();
        if (diskScheduler == null || x < 0 || y < 0 || x / BLOCK_STEP >= cols
                || x % BLOCK_STEP >= BLOCK_SIZE || y % BLOCK_STEP >= BLOCK_SIZE) return null;
        int position = y / BLOCK_STEP * cols + x / BLOCK_STEP;
        DiskBlock[] blocks = diskScheduler.getBlocks();
        if (position >= blocks.length) return null;
        DiskBlock block = blocks[position];
        String content = switch (block.contentType()) {
            case FREE -> "Libre";
            case FILE -> "Archivo " + block.filePath();
            case SWAP_RESERVED -> "Swap disponible";
            case SWAP_OCCUPIED -> "Swap ocupado por PCB " + block.processId();
        };
        return "Bloque " + position + " — " + content;
    }

    private int columns() {
        return Math.max(1, (getWidth() - START_X) / BLOCK_STEP);
    }
}
