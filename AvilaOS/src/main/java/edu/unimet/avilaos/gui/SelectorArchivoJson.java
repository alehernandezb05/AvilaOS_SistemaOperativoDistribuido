package edu.unimet.avilaos.gui;

import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Selector de archivos .json para guardar y cargar configuraciones. */
final class SelectorArchivoJson {

    private static final String EXTENSION = "json";

    // Se recuerda la ultima carpeta usada mientras el programa esta abierto
    private static File ultimaCarpeta = null;

    private SelectorArchivoJson() {
    }

    /** Pide un archivo para abrir; devuelve null si el usuario cancela. */
    static Path elegirParaAbrir(Component padre) {
        return elegirParaAbrir(padre, "Cargar configuracion");
    }

    static Path elegirParaAbrir(Component padre, String titulo) {
        JFileChooser selector = crearSelector(titulo);
        if (selector.showOpenDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        ultimaCarpeta = selector.getCurrentDirectory();
        return selector.getSelectedFile().toPath();
    }

    /** Pide donde guardar; agrega la extension .json si falta. Devuelve null si el usuario cancela. */
    static Path elegirParaGuardar(Component padre) {
        JFileChooser selector = crearSelector("Guardar configuracion");
        if (selector.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        ultimaCarpeta = selector.getCurrentDirectory();
        File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith("." + EXTENSION)) {
            archivo = new File(archivo.getParentFile(), archivo.getName() + "." + EXTENSION);
        }
        return archivo.toPath();
    }

    private static JFileChooser crearSelector(String titulo) {
        JFileChooser selector = new JFileChooser(ultimaCarpeta);
        selector.setDialogTitle(titulo);
        selector.setFileFilter(new FileNameExtensionFilter("Archivos JSON (*.json)", EXTENSION));
        return selector;
    }
}
