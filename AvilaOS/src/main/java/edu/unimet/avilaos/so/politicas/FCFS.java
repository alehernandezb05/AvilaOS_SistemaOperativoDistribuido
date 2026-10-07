package edu.unimet.avilaos.so.politicas;

import edu.unimet.avilaos.estructuras.Cola;
import edu.unimet.avilaos.so.EstrategiaPlanificacion;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.PoliticaPlanificacion;

/** First Come, First Served: atiende en orden de llegada y nunca expulsa. */
public class FCFS implements EstrategiaPlanificacion {

    @Override
    public PCB seleccionarSiguiente(Cola<PCB> listos) {
        return listos.desencolar();
    }

    @Override
    public boolean debeExpropiar(PCB enEjecucion, Cola<PCB> listos, int ciclosEnCpu) {
        return false;
    }

    @Override
    public PoliticaPlanificacion getPolitica() {
        return PoliticaPlanificacion.FCFS;
    }
}
