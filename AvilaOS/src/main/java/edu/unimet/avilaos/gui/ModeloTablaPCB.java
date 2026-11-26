package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.so.MotivoBloqueo;
import edu.unimet.avilaos.so.PCB;
import javax.swing.table.AbstractTableModel;

/**
 * Modelo de tabla que muestra los campos del PCB de los procesos de una cola.
 * Guarda una copia en un arreglo (sin colecciones de Java) que se reemplaza en cada refresco.
 */
public class ModeloTablaPCB extends AbstractTableModel {

    static final String[] COLUMNAS = {
        "ID", "Nombre", "Tipo", "Estado", "Prioridad", "Memoria", "Deadline", "Restante", "PC", "Motivo",
        "Operación", "Ruta"
    };

    private Object[][] filas = new Object[0][];

    /** Reemplaza las filas y avisa a la tabla para que se repinte. */
    public void setProcesos(PCB[] procesos) {
        setFilas(copiar(procesos));
    }

    static Object[][] copiar(PCB[] procesos) {
        Object[][] copia = new Object[procesos.length][COLUMNAS.length];
        for (int i = 0; i < procesos.length; i++) {
            for (int j = 0; j < COLUMNAS.length; j++) copia[i][j] = valor(procesos[i], j);
        }
        return copia;
    }

    void setFilas(Object[][] nuevas) {
        filas = nuevas;
        fireTableDataChanged();
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

    private static Object valor(PCB p, int columna) {
        return switch (columna) {
            case 0 -> "P" + p.getId();
            case 1 -> p.getNombre();
            case 2 -> p.getTipo();
            case 3 -> p.getEstado();
            case 4 -> p.getPrioridad();
            case 5 -> p.getMemoria();
            case 6 -> p.getDeadline();
            case 7 -> p.getTiempoRestante();
            case 8 -> p.getProgramCounter();
            case 9 -> p.getMotivoBloqueo() == MotivoBloqueo.ESPERA_DISCO ? "E/S de disco"
                    : p.getMotivoBloqueo() == null ? p.getMotivoTerminacion() : p.getMotivoBloqueo();
            case 10 -> p.getOperacionArchivo() == null ? "" : p.getOperacionArchivo().tipo();
            case 11 -> p.getOperacionArchivo() == null ? "" : p.getOperacionArchivo().ruta();
            default -> "";
        };
    }
}
