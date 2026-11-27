package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Curvas de desplazamiento acumulado que sobreviven al reiniciar la simulación. Cada ejecución y cada
 * política activa dentro de ella forman una serie, así se comparan políticas en un mismo gráfico.
 * Solo se usa desde el hilo de Swing.
 */
public final class ComparacionDisco {

    private final XYSeriesCollection datos = new XYSeriesCollection();
    private int ejecuciones;

    XYSeriesCollection datos() { return datos; }

    /** Número de la ejecución que empieza; numera las series de esa simulación. */
    int nuevaEjecucion() { return ++ejecuciones; }

    /** Nombre de la serie: volver a una política dentro de la misma ejecución abre otro tramo. */
    static String nombre(int ejecucion, DiskPolicy politica, int tramo) {
        return "Ejecución " + ejecucion + " - " + politica + (tramo > 1 ? " (" + tramo + ")" : "");
    }

    void agregar(String nombre, long ciclo, long desplazamiento, long cicloAnterior, long desplazamientoAnterior) {
        int indice = datos.getSeriesIndex(nombre);
        if (indice == -1) {
            XYSeries serie = new XYSeries(nombre, true, false);
            // Una política elegida a mitad de ejecución arranca donde terminó la anterior.
            if (cicloAnterior >= 0) serie.add(cicloAnterior, desplazamientoAnterior);
            datos.addSeries(serie);
            indice = datos.getSeriesIndex(nombre);
        }
        datos.getSeries(indice).addOrUpdate(ciclo, desplazamiento);
    }

    /** Borra las curvas; la numeración sigue para no repetir nombres. */
    void limpiar() { datos.removeAllSeries(); }
}
