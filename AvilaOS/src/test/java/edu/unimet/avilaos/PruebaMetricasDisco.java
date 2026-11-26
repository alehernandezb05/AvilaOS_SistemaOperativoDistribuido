package edu.unimet.avilaos;

import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;

public final class PruebaMetricasDisco {

    public static void main(String[] args) {
        esperaYCambioPolitica();
        historialPorCiclo();
        retornoCircular();
    }

    private static void esperaYCambioPolitica() {
        DiskScheduler disco = new DiskScheduler(
                200, 50, 0, DiskPolicy.FIFO);

        comprobar(disco.getAverageWaitCycles() == 0,
                "La espera inicial debe ser cero.");

        DiskRequest a = new DiskRequest(
                1, "A", DiskOperation.READ, 52);

        DiskRequest b = new DiskRequest(
                2, "B", DiskOperation.READ, 50);

        disco.addRequest(a);
        disco.addRequest(b);

        disco.alCiclo(1);
        disco.setPolicy(DiskPolicy.SSTF);

        for (int ciclo = 2; ciclo <= 6; ciclo++) {
            disco.alCiclo(ciclo);
        }

        comprobar(a.getArrivalCycle() == 0
                && a.getSelectionCycle() == 1
                && a.getStartCycle() == 1
                && a.getCompletionCycle() == 3,
                "Registro temporal incorrecto.");

        comprobar(disco.getServedCount(DiskPolicy.FIFO) == 1
                && disco.getServedCount(DiskPolicy.SSTF) == 1,
                "Atribucion incorrecta de politica.");

        comprobar(disco.getAverageWaitCycles() == 2.5,
                "Espera promedio general incorrecta.");

        comprobar(disco.getAverageWaitCycles(DiskPolicy.FIFO) == 1
                && disco.getAverageWaitCycles(DiskPolicy.SSTF) == 4,
                "Espera por politica incorrecta.");

        System.out.println(
                "OK: espera, ciclos y cambio de politica.");
    }

    private static void historialPorCiclo() {
        DiskScheduler disco = new DiskScheduler(
                200, 50, 0, DiskPolicy.FIFO);

        disco.addRequest(new DiskRequest(
                1, "A", DiskOperation.READ, 52));

        disco.alCiclo(1);
        disco.alCiclo(1);
        disco.alCiclo(2);
        disco.setPolicy(DiskPolicy.SSTF);
        disco.alCiclo(3);
        disco.alCiclo(4);

        var historial = disco.getMovementHistory(0);

        comprobar(historial.length == 4,
                "No deben existir ciclos duplicados.");

        long[] esperado = {1, 2, 2, 2};

        for (int i = 0; i < historial.length; i++) {
            comprobar(historial[i].cycle() == i + 1
                    && historial[i].movement() == esperado[i],
                    "Historial incorrecto.");
        }

        comprobar(disco.getMovementHistory(4).length == 0,
                "No deben generarse muestras ficticias.");

        comprobar(disco.getMovementHistory(0).length == 4,
                "Consultar no debe alterar el historial.");

        System.out.println(
                "OK: historial real sin duplicados.");
    }

    private static void retornoCircular() {
        DiskScheduler disco = new DiskScheduler(
                10, 5, 0, DiskPolicy.C_SCAN);

        disco.setSpeed(2);

        disco.addRequest(new DiskRequest(
                1, "A", DiskOperation.READ, 2));

        disco.alCiclo(1);
        disco.alCiclo(2);

        comprobar(disco.getTotalHeadMovement() == 4,
                "Desplazamiento inicial incorrecto.");

        for (int ciclo = 3; ciclo <= 7; ciclo++) {
            disco.alCiclo(ciclo);

            comprobar(disco.getTotalHeadMovement() == 4,
                    "El retorno no suma desplazamiento.");
        }

        comprobar(disco.getTotalReturnCycles() == 5,
                "Contador de retorno incorrecto.");

        disco.alCiclo(8);
        disco.alCiclo(9);

        comprobar(disco.getTotalHeadMovement() == 6,
                "Desplazamiento final incorrecto.");

        comprobar(disco.getTotalRequestsServed() == 1,
                "Debe completarse una solicitud.");

        comprobar(disco.getAverageWaitCycles() == 8,
                "La espera debe incluir el retorno.");

        System.out.println(
                "OK: retorno C-SCAN contabilizado por separado.");
    }

    private static void comprobar(
            boolean condicion, String mensaje) {

        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}