package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.Buffer;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.Semaforo;
import javax.swing.table.AbstractTableModel;

/**
 * Modelo de tabla con el estado de cada buffer: ocupacion, valor de sus tres
 * semaforos y los procesos bloqueados en cada uno. Guarda una copia en arreglos
 * (sin colecciones de Java) que se reemplaza en cada refresco.
 */
public class ModeloTablaBuffers extends AbstractTableModel {

    static final String[] COLUMNAS = {
        "Buffer", "Anfitrion", "Elementos", "Capacidad", "vacios", "llenos", "mutex", "Bloqueados"
    };

    private Object[][] filas = new Object[0][];

    /** Toma una foto del estado de los buffers y avisa a la tabla para que se repinte. */
    public void setBuffers(Buffer[] buffers) {
        Object[][] nuevas = new Object[buffers.length][];
        for (int i = 0; i < buffers.length; i++) {
            Buffer b = buffers[i];
            nuevas[i] = new Object[] {
                b.getId(), "Computador " + b.getIdAnfitrion(), b.getElementos(), b.getCapacidad(),
                b.getVacios().getValor(), b.getLlenos().getValor(), b.getMutex().getValor(),
                describirBloqueados(b)
            };
        }
        filas = nuevas;
        fireTableDataChanged();
    }

    // Ej.: "P6 (llenos), P8 (mutex)"; "-" si nadie espera
    private static String describirBloqueados(Buffer b) {
        StringBuilder texto = new StringBuilder();
        agregarBloqueados(texto, b.getVacios(), "vacios");
        agregarBloqueados(texto, b.getLlenos(), "llenos");
        agregarBloqueados(texto, b.getMutex(), "mutex");
        return texto.length() == 0 ? "-" : texto.toString();
    }

    private static void agregarBloqueados(StringBuilder texto, Semaforo semaforo, String nombre) {
        for (PCB p : semaforo.getBloqueados()) {
            if (texto.length() > 0) {
                texto.append(", ");
            }
            texto.append('P').append(p.getId()).append(" (").append(nombre).append(')');
        }
    }

    @Override
    public int getRowCount() {
        return filas.length;
    }

    @Override
    public int getColumnCount() {
        return COLUMNAS.length;
    }

    @Override
    public String getColumnName(int columna) {
        return COLUMNAS[columna];
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        return filas[fila][columna];
    }
}
