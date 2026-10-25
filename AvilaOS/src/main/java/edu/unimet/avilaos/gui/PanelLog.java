package edu.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Log de eventos del sistema. Por ahora muestra lineas de ejemplo.
 */
public class PanelLog extends JPanel {

    private final JTextArea areaLog = new JTextArea();

    public PanelLog() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        areaLog.setEditable(false);
        areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        areaLog.setText("[ciclo 12] Computador 1 selecciona el proceso P3\n"
                + "[ciclo 13] Proceso P6 se bloquea en el semaforo 'llenos' del buffer B2\n"
                + "[ciclo 14] Proceso P5 accede al buffer B2 de forma remota\n");
        add(new JScrollPane(areaLog), BorderLayout.CENTER);
    }

    /** Agrega una linea al final del log y la deja visible. */
    public void agregarLinea(String linea) {
        areaLog.append(linea + "\n");
        areaLog.setCaretPosition(areaLog.getDocument().getLength());
    }
}
