/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package edu.unimet.avilaos;

import edu.unimet.avilaos.gui.VentanaPrincipal;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 *
 * @author aleja
 */
public class AvilaOS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Si falla se queda el look and feel por defecto de Swing
            }
            new VentanaPrincipal(2).setVisible(true);
        });
    }
}
