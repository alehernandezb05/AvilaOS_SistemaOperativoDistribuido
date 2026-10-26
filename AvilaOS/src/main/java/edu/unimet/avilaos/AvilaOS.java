/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.gui.DialogoConfiguracionInicial;
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
            // Primero se configura el cluster; si el usuario sale, el programa termina
            DialogoConfiguracionInicial dialogo = new DialogoConfiguracionInicial(Configuracion.porDefecto());
            dialogo.setVisible(true);
            Configuracion config = dialogo.getConfiguracion();
            if (config == null) {
                System.exit(0);
            }
            new VentanaPrincipal(new Simulador(config)).setVisible(true);
        });
    }
}
