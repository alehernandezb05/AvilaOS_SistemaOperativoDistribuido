package edu.unimet.avilaos.gui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/**
 * Estado global de los buffers: ocupacion, semaforos y procesos bloqueados.
 * Por ahora muestra datos de ejemplo.
 */
public class PanelBuffers extends JPanel {

    private static final String[] COLUMNAS = {
        "Buffer", "Anfitrion", "Elementos", "Capacidad", "vacios", "llenos", "mutex", "Bloqueados"
    };

    public PanelBuffers() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Buffers"));

        JTable tabla = new JTable(new Object[][] {
            {"B1", "Computador 1", 3, 5, 2, 3, 1, "-"},
            {"B2", "Computador 2", 0, 4, 4, 0, 1, "P6 (llenos)"}
        }, COLUMNAS);
        tabla.setEnabled(false);
        tabla.setFillsViewportHeight(true);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
    }
}
