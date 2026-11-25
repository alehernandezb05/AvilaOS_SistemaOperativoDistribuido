package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import edu.unimet.avilaos.so.archivos.SistemaArchivos.Tipo;
import java.awt.Component;
import java.awt.GridLayout;
import java.text.ParseException;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

final class DialogoCrearEntrada {

    private DialogoCrearEntrada() { }

    /** Solo el administrador puede elegir crear un directorio. */
    static OperacionArchivo solicitar(Component padre, String directorio, int maxBloques, String solicitante,
                                      boolean permitirDirectorio) {
        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 6));
        JTextField nombre = new JTextField(20);
        JComboBox<Tipo> tipo = new JComboBox<>(permitirDirectorio
                ? new Tipo[] {Tipo.FILE, Tipo.DIRECTORY} : new Tipo[] {Tipo.FILE});
        JSpinner bloques = new JSpinner(new SpinnerNumberModel(1, 1, maxBloques, 1));
        tipo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor,
                    int indice, boolean seleccionado, boolean foco) {
                return super.getListCellRendererComponent(lista, valor == Tipo.FILE ? "Archivo" : "Directorio",
                        indice, seleccionado, foco);
            }
        });
        tipo.addActionListener(e -> bloques.setEnabled(tipo.getSelectedItem() == Tipo.FILE));
        campos.add(new JLabel("Directorio destino"));
        campos.add(new JLabel(directorio));
        campos.add(new JLabel("Nombre"));
        campos.add(nombre);
        campos.add(new JLabel("Tipo"));
        campos.add(tipo);
        campos.add(new JLabel("Tamaño en bloques (1–" + maxBloques + ")"));
        campos.add(bloques);
        campos.add(new JLabel("Solicitante"));
        campos.add(new JLabel(solicitante));

        while (JOptionPane.showConfirmDialog(padre, campos, "Crear archivo o directorio",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                int cantidad = 0;
                if (tipo.getSelectedItem() == Tipo.FILE) {
                    bloques.commitEdit();
                    cantidad = (Integer) bloques.getValue();
                }
                return new OperacionArchivo(DiskOperation.CREATE,
                        SistemaArchivos.rutaHija(directorio, nombre.getText().strip()),
                        solicitante, cantidad, null);
            } catch (ParseException e) {
                mostrarError(padre, "El tamaño debe ser un entero entre 1 y " + maxBloques + ".");
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
