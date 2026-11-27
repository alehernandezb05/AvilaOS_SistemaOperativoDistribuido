package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Formularios de UPDATE (renombrar) y DELETE: solo arman la operación; el proceso de E/S la ejecuta. */
final class DialogoOperacionArchivo {

    private DialogoOperacionArchivo() { }

    static OperacionArchivo renombrar(Component padre, String ruta, String solicitante) {
        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        JTextField nuevoNombre = new JTextField(20);
        campos.add(new JLabel("Entrada"));
        campos.add(new JLabel(ruta));
        campos.add(new JLabel("Nombre nuevo"));
        campos.add(nuevoNombre);
        campos.add(new JLabel("Solicitante"));
        campos.add(new JLabel(solicitante));
        while (JOptionPane.showConfirmDialog(padre, campos, "Renombrar",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                return new OperacionArchivo(DiskOperation.UPDATE, ruta, solicitante, 0,
                        nuevoNombre.getText().strip());
            } catch (IllegalArgumentException e) {
                mostrarError(padre, e.getMessage());
            }
        }
        return null;
    }

    static OperacionArchivo eliminar(Component padre, String ruta, String solicitante) {
        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        campos.add(new JLabel("Entrada"));
        campos.add(new JLabel(ruta));
        campos.add(new JLabel("Solicitante"));
        campos.add(new JLabel(solicitante));
        campos.add(new JLabel("Si es un directorio, se elimina todo su contenido."));
        campos.add(new JLabel(""));
        while (JOptionPane.showConfirmDialog(padre, campos, "Eliminar",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                return new OperacionArchivo(DiskOperation.DELETE, ruta, solicitante, 0, null);
            } catch (IllegalArgumentException e) {
                mostrarError(padre, e.getMessage());
            }
        }
        return null;
    }

    private static void mostrarError(Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, "Datos inválidos", JOptionPane.WARNING_MESSAGE);
    }
}
