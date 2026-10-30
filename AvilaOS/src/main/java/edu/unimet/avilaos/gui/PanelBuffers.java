package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.Buffer;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/**
 * Estado global de los buffers: ocupacion, valor de los semaforos y procesos
 * bloqueados en cada uno. Solo lee del modelo; la ventana llama a actualizar()
 * en cada refresco.
 */
public class PanelBuffers extends JPanel {

    private final ModeloTablaBuffers modelo = new ModeloTablaBuffers();

    public PanelBuffers() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Buffers"));

        JTable tabla = new JTable(modelo);
        tabla.setEnabled(false);
        tabla.setFillsViewportHeight(true);
        // La columna de bloqueados necesita mas espacio que las de numeros
        tabla.getColumnModel().getColumn(ModeloTablaBuffers.COLUMNAS.length - 1).setPreferredWidth(220);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
    }

    /** Muestra el estado actual de los buffers. Se llama desde el hilo de Swing. */
    public void actualizar(Buffer[] buffers) {
        modelo.setBuffers(buffers);
    }
}
