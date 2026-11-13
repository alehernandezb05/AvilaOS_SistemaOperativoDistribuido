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
            // Primero se configura el computador; si el usuario sale, el programa termina
            DialogoConfiguracionInicial dialogo = new DialogoConfiguracionInicial(Configuracion.porDefecto());
            dialogo.setVisible(true);
            Configuracion config = dialogo.getConfiguracion();
            if (config == null) {
                System.exit(0);
            }
            abrirSimulacion(config);
        });
    }

    /**
     * Crea el Simulador y su ventana. Al cargar otra configuracion desde la ventana,
     * se detiene esta simulacion, se cierra su ventana y se abre una nueva.
     */
    private static void abrirSimulacion(Configuracion config) {
        Simulador simulador = new Simulador(config);
        VentanaPrincipal[] ventana = new VentanaPrincipal[1];
        ventana[0] = new VentanaPrincipal(simulador, nueva -> {
            simulador.detener();
            ventana[0].dispose();
            abrirSimulacion(nueva);
        });
        ventana[0].setVisible(true);
    }
}
