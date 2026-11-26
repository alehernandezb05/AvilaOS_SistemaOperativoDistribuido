package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.Log;
import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

/**
 * Muestra el log de eventos del sistema. Solo lee el Log del modelo: en cada
 * refresco agrega los eventos nuevos desde la ultima lectura.
 */
public class PanelLog extends JPanel {

    /** Lineas que se conservan en pantalla; las mas viejas se recortan. */
    public static final int MAX_LINEAS = 500;

    private final JTextArea areaLog = new JTextArea();
    private long ultimoLeido = 0;

    public PanelLog() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        areaLog.setEditable(false);
        areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(areaLog), BorderLayout.CENTER);
    }

    /** Agrega los eventos registrados desde la ultima llamada. Se llama desde el hilo de Swing. */
    Runnable capturar(Log log) {
        String[] nuevos = log.obtenerDesde(ultimoLeido);
        long total = log.getTotalRegistrados();
        return () -> mostrar(nuevos, total);
    }

    private void mostrar(String[] nuevos, long total) {
        ultimoLeido = total;
        if (nuevos.length == 0) {
            return;
        }
        StringBuilder texto = new StringBuilder();
        for (String evento : nuevos) {
            texto.append(evento).append('\n');
        }
        areaLog.append(texto.toString());
        recortar();
        areaLog.setCaretPosition(areaLog.getDocument().getLength());
    }

    // Quita las lineas mas viejas para que el area de texto no crezca sin limite
    private void recortar() {
        int sobrantes = areaLog.getLineCount() - 1 - MAX_LINEAS;  // la ultima linea queda vacia
        if (sobrantes <= 0) {
            return;
        }
        try {
            areaLog.replaceRange("", 0, areaLog.getLineEndOffset(sobrantes - 1));
        } catch (BadLocationException e) {
            areaLog.setText("");
        }
    }
}
