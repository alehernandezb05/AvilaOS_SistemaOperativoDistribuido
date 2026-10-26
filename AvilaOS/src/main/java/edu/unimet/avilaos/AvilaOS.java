/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.gui.VentanaPrincipal;
import edu.unimet.avilaos.so.PoliticaPlanificacion;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 *
 * @author aleja
 */
public class AvilaOS {

    public static void main(String[] args) {
        // Configuracion fija mientras no exista la vista de configuracion inicial (#39)
        Simulador simulador = new Simulador(500, 3, new int[] {256, 256},
                new PoliticaPlanificacion[] {PoliticaPlanificacion.FCFS, PoliticaPlanificacion.FCFS});

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Si falla se queda el look and feel por defecto de Swing
            }
            new VentanaPrincipal(simulador).setVisible(true);
        });
    }
}
