package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.estructuras.Lista;
import edu.unimet.avilaos.hardware.disco.DiskBlock;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.DiskSnapshot;
import edu.unimet.avilaos.hardware.disco.enums.DiskBlockType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/**
 * Mapa de bloques del disco: cada celda lleva su número y quién la ocupa (nombre del archivo o PCB en swap).
 * Cada archivo tiene su propio color, el mismo que muestra la tabla de asignación.
 */
public class DiskMapPanel extends JPanel implements Scrollable {

    static final Color COLOR_LIBRE = new Color(232, 245, 233);
    static final Color COLOR_SWAP_LIBRE = new Color(237, 231, 246);
    static final Color COLOR_SWAP_OCUPADO = new Color(149, 117, 205);
    static final Color COLOR_CABEZAL = new Color(211, 47, 47);
    static final Color COLOR_DESTINO = new Color(25, 118, 210);
    private static final double PROPORCION_AUREA = 0.6180339887498949;
    private static final int BLOCK_SIZE = 32;
    private static final int BLOCK_MARGIN = 3;
    private static final int BLOCK_STEP = BLOCK_SIZE + BLOCK_MARGIN;
    private static final int START_X = 10;
    private static final int START_Y = 30;
    private static final Font FUENTE_TITULO = new Font("SansSerif", Font.BOLD, 12);
    private static final Font FUENTE_NUMERO = new Font("SansSerif", Font.BOLD, 10);
    private static final Font FUENTE_ETIQUETA = new Font("SansSerif", Font.PLAIN, 9);
    private DiskScheduler diskScheduler;
    private DiskBlock[] bloquesObservados;
    private DiskSnapshot estadoObservado;

    public DiskMapPanel(DiskScheduler diskScheduler) {
        this.diskScheduler = diskScheduler;
        setBackground(Color.WHITE);
        setToolTipText("");
        // Al cambiar el ancho cambian las columnas, y con ellas el alto que necesita el mapa.
        addComponentListener(new ComponentAdapter() {
            private int columnasPrevias;
            @Override
            public void componentResized(ComponentEvent e) {
                int columnas = columns();
                if (columnas != columnasPrevias) {
                    columnasPrevias = columnas;
                    revalidate();
                }
            }
        });
    }

    private record ColorAsignado(String ruta, Color color) { }
    private static final Lista<ColorAsignado> COLORES = new Lista<>();

    /**
     * Color de un archivo, el mismo en el mapa y en la tabla. Cada ruta nueva recibe el siguiente tono de la
     * secuencia áurea, que reparte los tonos sin repetir los cercanos. Solo se usa desde el hilo de Swing.
     */
    static Color colorArchivo(String ruta) {
        for (int i = 0; i < COLORES.getTamano(); i++) {
            if (COLORES.obtener(i).ruta().equals(ruta)) return COLORES.obtener(i).color();
        }
        double tono = (0.08 + COLORES.getTamano() * PROPORCION_AUREA) % 1.0;
        Color color = Color.getHSBColor((float) tono, 0.45f, 0.95f);
        COLORES.agregar(new ColorAsignado(ruta, color));
        return color;
    }

    public void setDiskScheduler(DiskScheduler diskScheduler) {
        this.diskScheduler = diskScheduler;
        bloquesObservados = null;
        estadoObservado = null;
        revalidate();
        repaint();
    }

    void mostrarBloques(DiskBlock[] bloques) {
        mostrar(bloques, null);
    }

    /** Pinta bloques y cabezal de una misma observacion; null en estado lo pide al disco. */
    void mostrar(DiskBlock[] bloques, DiskSnapshot estado) {
        boolean cambiaTamano = bloquesObservados == null || bloques == null || bloquesObservados.length != bloques.length;
        bloquesObservados = bloques;
        estadoObservado = estado;
        if (cambiaTamano) revalidate();
        repaint();
    }

    private DiskBlock[] bloquesVisibles() {
        if (bloquesObservados != null) return bloquesObservados;
        return diskScheduler == null ? new DiskBlock[0] : diskScheduler.getBlocks();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (diskScheduler == null) {
            g.drawString("No hay disco cargado", 20, 20);
            return;
        }

        DiskBlock[] blocks = bloquesVisibles();
        // Cabezal, direccion y destino salen de una sola copia para no mezclar ciclos.
        DiskSnapshot estado = estadoObservado == null ? diskScheduler.getSnapshot() : estadoObservado;
        int currentHead = estado.currentHead();
        int destino = estado.targetBlock();
        int cols = columns();

        g.setFont(FUENTE_TITULO);
        g.setColor(Color.BLACK);
        g.drawString("Cabezal en el bloque " + currentHead + " [" + estado.direction() + "]"
                + (destino >= 0 ? " - destino: bloque " + destino : ""), 10, 15);

        for (int i = 0; i < blocks.length; i++) {
            int x = (i % cols) * BLOCK_STEP + START_X;
            int y = (i / cols) * BLOCK_STEP + START_Y;
            DiskBlock block = blocks[i];

            g.setColor(colorBloque(block));
            g.fillRect(x, y, BLOCK_SIZE, BLOCK_SIZE);
            g.setColor(Color.LIGHT_GRAY);
            g.drawRect(x, y, BLOCK_SIZE, BLOCK_SIZE);

            Color texto = block.contentType() == DiskBlockType.SWAP_OCCUPIED
                    ? Color.WHITE : Color.DARK_GRAY;
            g.setColor(texto);
            g.setFont(FUENTE_NUMERO);
            centrar(g, String.valueOf(i), x, y + 12);
            String etiqueta = etiqueta(block);
            if (!etiqueta.isEmpty()) {
                g.setFont(FUENTE_ETIQUETA);
                centrar(g, etiqueta, x, y + 25);
            }

            if (i == currentHead || i == destino) {
                g.setColor(i == currentHead ? COLOR_CABEZAL : COLOR_DESTINO);
                g.setStroke(new BasicStroke(i == currentHead ? 3f : 2f));
                g.drawRect(x - 1, y - 1, BLOCK_SIZE + 2, BLOCK_SIZE + 2);
                g.setStroke(new BasicStroke(1f));
            }
        }
    }

    private static Color colorBloque(DiskBlock block) {
        return switch (block.contentType()) {
            case FREE -> COLOR_LIBRE;
            case FILE -> colorArchivo(block.filePath());
            case SWAP_RESERVED -> COLOR_SWAP_LIBRE;
            case SWAP_OCCUPIED -> COLOR_SWAP_OCUPADO;
        };
    }

    /** Texto corto del ocupante: el nombre del archivo o el PCB suspendido. */
    private static String etiqueta(DiskBlock block) {
        return switch (block.contentType()) {
            case FREE -> "";
            case FILE -> {
                String ruta = block.filePath();
                String nombre = ruta.substring(ruta.lastIndexOf('/') + 1);
                yield nombre.length() <= 5 ? nombre : nombre.substring(0, 4) + "…";
            }
            case SWAP_RESERVED -> "swap";
            case SWAP_OCCUPIED -> "P" + block.processId();
        };
    }

    private static void centrar(Graphics2D g, String texto, int x, int base) {
        FontMetrics medidas = g.getFontMetrics();
        g.drawString(texto, x + (BLOCK_SIZE - medidas.stringWidth(texto)) / 2, base);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int x = event.getX() - START_X;
        int y = event.getY() - START_Y;
        int cols = columns();
        if (diskScheduler == null || x < 0 || y < 0 || x / BLOCK_STEP >= cols
                || x % BLOCK_STEP >= BLOCK_SIZE || y % BLOCK_STEP >= BLOCK_SIZE) return null;
        int position = y / BLOCK_STEP * cols + x / BLOCK_STEP;
        DiskBlock[] blocks = bloquesVisibles();
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

    /** Ancho donde se dibuja: el del visor si está en un JScrollPane, para no calcular filas con un ancho viejo. */
    private int anchoDisponible() {
        int ancho = getParent() instanceof JViewport visor ? visor.getWidth() : getWidth();
        return ancho > 0 ? ancho : 20 * BLOCK_STEP + START_X;
    }

    private int columns() {
        return Math.max(1, (anchoDisponible() - START_X) / BLOCK_STEP);
    }

    /** El ancho lo pone el panel que lo contiene; el alto crece con las filas necesarias. */
    @Override
    public Dimension getPreferredSize() {
        int columnas = columns();
        int filas = (bloquesVisibles().length + columnas - 1) / columnas;
        return new Dimension(anchoDisponible(), START_Y + filas * BLOCK_STEP + 10);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(600, 250); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientacion, int direccion) { return BLOCK_STEP; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientacion, int direccion) {
        return Math.max(BLOCK_STEP, visible.height - BLOCK_STEP);
    }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
