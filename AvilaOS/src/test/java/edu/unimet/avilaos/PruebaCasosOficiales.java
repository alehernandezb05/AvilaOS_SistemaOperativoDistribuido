package edu.unimet.avilaos;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ArchivoSistemaArchivos;
import edu.unimet.avilaos.config.ConfigDisco;
import edu.unimet.avilaos.hardware.disco.DiskCompletion;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.hardware.disco.enums.DiskPolicy;
import edu.unimet.avilaos.so.MotivoTerminacion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Validacion determinista: no usa temporizadores, frameworks ni colecciones Java. */
public final class PruebaCasosOficiales {
    private static final int[] CASOS = {95,180,34,119,11,123,62,64};
    private static final int[] ANEXO = {82,17,43,140,24,16,190};
    private static final int[][] ORDEN_CASOS = {
        {95,180,34,119,11,123,62,64}, {62,64,34,11,95,119,123,180},
        {62,64,95,119,123,180,34,11}, {62,64,95,119,123,180,11,34}
    };
    private static final int[][] ORDEN_ANEXO = {
        {82,17,43,140,24,16,190}, {43,24,17,16,82,140,190},
        {82,140,190,43,24,17,16}, {82,140,190,16,17,24,43}
    };
    private static final long[] MOV_CASOS = {644,236,337,183};
    private static final long[] MOV_ANEXO = {518,208,332,192};
    private static final int LIMITE = 3000;
    private static final String[] COLORES = {"#2563eb", "#b45309", "#047857", "#9333ea"};

    private record Serie(long[] movimiento, int ciclos) { }
    private static final StringBuilder resumen = new StringBuilder(
            "escenario,politica,ciclo_final,atendidas,desplazamiento,espera_promedio,retorno_ciclos,orden\n");

    public static void main(String[] args) throws Exception {
        Path escenarios = Files.isDirectory(Path.of("escenarios")) ? Path.of("escenarios") : Path.of("..", "escenarios");
        // Evidencia versionable: este ejecutable manual no forma parte de las pruebas automaticas Maven.
        Path salida = args.length == 0 ? escenarios.resolve("12_resultados_disco") : Path.of(args[0]);
        resumen.setLength(0);
        resumen.append("escenario,politica,ciclo_final,atendidas,desplazamiento,espera_promedio,retorno_ciclos,orden\n");
        Files.createDirectories(salida);
        System.out.println("LOTE: disco 200, cabezal 50 UP, velocidad 1, llegada 0.");
        Serie[] casos = new Serie[4], anexo = new Serie[4], completos = new Serie[4];
        for (DiskPolicy p : DiskPolicy.values()) {
            int i = p.ordinal();
            casos[i] = lote("P"+(i+1), p, CASOS, ORDEN_CASOS[i], MOV_CASOS[i], salida);
            anexo[i] = lote("Anexo", p, ANEXO, ORDEN_ANEXO[i], MOV_ANEXO[i], salida);
        }
        grafica(salida, "P1-P4", casos);
        grafica(salida, "Anexo", anexo);
        bordes();
        System.out.println("COMPLETO: JSON oficiales sin alterar; llegadas escalonadas, velocidad 1.");
        for (DiskPolicy p : DiskPolicy.values()) completos[p.ordinal()] = completo(p, escenarios, salida);
        grafica(salida, "Completo", completos);
        Files.writeString(salida.resolve("resumen.csv"), resumen);
        System.out.println("OK: casos P1-P4, anexo, bordes y simulacion completa.");
        System.out.println("Evidencias: " + salida.toAbsolutePath().normalize());
    }

    private static Serie lote(String nombre, DiskPolicy p, int[] bloques, int[] esperado,
                               long movimientoEsperado, Path salida) throws Exception {
        DiskScheduler d = new DiskScheduler(200,50,0,p);
        DiskRequest[] solicitudes = new DiskRequest[bloques.length];
        for(int i=0;i<bloques.length;i++) {
            solicitudes[i] = new DiskRequest(i+1,"R"+(i+1),DiskOperation.READ,bloques[i]);
            exigir(d.addRequest(solicitudes[i]),"No encolada");
        }
        long[] movimiento = new long[LIMITE+1];
        StringBuilder recorrido = encabezadoRecorrido(), detalle = encabezadoSolicitudes();
        StringBuilder orden = new StringBuilder();
        muestra(d,recorrido,movimiento,0);
        int c=0, n=0;
        long suma=0;
        boolean extremo=false, retorno=false;
        while(n<bloques.length && c<LIMITE) {
            d.alCiclo(++c);
            muestra(d,recorrido,movimiento,c);
            extremo |= d.getCurrentHead()==199;
            retorno |= extremo && d.getCurrentHead()==0;
            DiskCompletion fin = d.pollCompleted();
            if(fin!=null) {
                DiskRequest r=solicitudes[fin.processId()-1];
                exigir(r.getBlockNumber()==esperado[n],nombre+" orden incorrecto");
                exigir(fin.arrivalCycle()==0 && fin.startCycle()==r.getSelectionCycle()
                        && fin.completionCycle()==c && fin.policy()==p,"Ciclos incorrectos");
                sumarOrden(orden,Integer.toString(r.getBlockNumber()));
                suma+=fin.startCycle()-fin.arrivalCycle();
                solicitud(detalle,r);
                n++;
            }
        }
        exigir(n==bloques.length && d.getTotalRequestsServed()==n && d.pollCompleted()==null,"Lote incompleto");
        exigir(d.getTotalHeadMovement()==movimientoEsperado,nombre+" desplazamiento incorrecto: "+d.getTotalHeadMovement());
        exigir(d.getAverageWaitCycles()==(double)suma/n,"Espera inconsistente");
        if(p==DiskPolicy.SCAN || p==DiskPolicy.C_SCAN) exigir(extremo,"Falta extremo 199");
        if(p==DiskPolicy.C_SCAN) exigir(retorno && d.getTotalReturnCycles()==199,"Falta retorno circular");
        else exigir(d.getTotalReturnCycles()==0,"Retorno inesperado");
        exigir(d.getMovementHistory(0).length==c,"Historial incompleto");
        escribir(nombre,p,d,c,orden,recorrido,detalle,salida);
        return new Serie(movimiento,c);
    }

    private static Simulador cargar(DiskPolicy p, Path escenarios) throws Exception {
        var config=ArchivoConfiguracion.cargar(escenarios.resolve("09_procesos_archivos.json"));
        var archivos=ArchivoSistemaArchivos.cargar(escenarios.resolve("08_sistema_archivos.json"));
        var disco=config.getDisco();
        config.setDisco(new ConfigDisco(200,50,disco.getSwapSizeBlocks(),1,p));
        return new Simulador(config,archivos);
    }

    private static Serie completo(DiskPolicy p, Path escenarios, Path salida) throws Exception {
        Simulador s=cargar(p,escenarios);
        var comp=s.getComputador(1); var colas=comp.getNucleo().getColas();
        DiskScheduler d=s.getDisco();
        DiskRequest[] solicitudes=new DiskRequest[8];
        boolean[] anotadas=new boolean[8];
        long[] movimiento=new long[LIMITE+1];
        StringBuilder recorrido=encabezadoRecorrido(), detalle=encabezadoSolicitudes(), orden=new StringBuilder();
        muestra(d,recorrido,movimiento,0);
        int c=0, cantidad=0, atendidas=0; long suma=0;
        while(colas.cantidadTerminados()<8 && c<LIMITE) {
            // Igual orden que Computador registra en el reloj: disco, nucleo, CPU.
            d.alCiclo(++c); comp.getNucleo().alCiclo(c); comp.getCpu().alCiclo(c);
            for(DiskRequest r:d.getPendingRequests()) {
                boolean existe=false;
                for(int i=0;i<cantidad;i++) if(solicitudes[i]==r) existe=true;
                if(!existe) { exigir(cantidad<solicitudes.length,"Solicitudes adicionales"); solicitudes[cantidad++]=r; }
            }
            for(int i=0;i<cantidad;i++) {
                DiskRequest r=solicitudes[i];
                if(!anotadas[i] && r.getResult()!=null) {
                    anotadas[i]=true; atendidas++;
                    suma+=r.getStartCycle()-r.getArrivalCycle();
                    sumarOrden(orden,r.getProcessName()+":"+r.getBlockNumber());
                    solicitud(detalle,r);
                }
            }
            muestra(d,recorrido,movimiento,c);
        }
        exigir(colas.cantidadTerminados()==8 && comp.getRam().getUsada()==0,"Procesos sin terminar o RAM ocupada");
        int completados=0, errores=0, vencidos=0;
        StringBuilder estados=new StringBuilder("proceso,motivo,ciclo_fin\n");
        for(int i=0;i<8;i++) {
            var pcb=colas.obtenerTerminado(i);
            MotivoTerminacion motivo=pcb.getMotivoTerminacion();
            if(motivo==MotivoTerminacion.COMPLETADO) completados++;
            else if(motivo==MotivoTerminacion.ERROR_ES) errores++;
            else if(motivo==MotivoTerminacion.DEADLINE_VENCIDO) vencidos++;
            estados.append(pcb.getNombre()).append(',').append(motivo).append(',').append(pcb.getCicloFin()).append('\n');
        }
        // FIFO recorre 644 bloques: a velocidad 1 tres procesos vencen el deadline 500. Las demas atienden las ocho.
        int esperados=p==DiskPolicy.FIFO?5:8;
        exigir(completados==esperados && errores==0 && vencidos==8-esperados, "Resultados de los JSON oficiales inesperados");
        for(int i=0;i<cantidad;i++) if(!anotadas[i]) solicitud(detalle,solicitudes[i]);
        exigir(atendidas==d.getTotalRequestsServed() && atendidas==esperados && d.getServedCount(p)==atendidas,"Conteo de solicitudes inconsistente");
        exigir(d.getAverageWaitCycles()==(double)suma/atendidas,"Espera completa inconsistente");
        exigir(d.getSnapshot().pendingCount()==0 && d.getSnapshot().activeRequestId()==-1,"Cola residual");
        Files.writeString(salida.resolve("Completo_"+p+"_procesos.csv"),estados);
        String eventos=String.join("\n",s.getLog().obtenerDesde(0));
        if(p==DiskPolicy.SCAN) exigir(eventos.contains("llega al extremo 199 y cambia de UP a DOWN"),"SCAN no registra el cambio de dirección");
        if(p==DiskPolicy.C_SCAN) exigir(eventos.contains("regresa al bloque 0") && eventos.contains("termina el retorno"),"C-SCAN no registra el retorno");
        Files.writeString(salida.resolve("Completo_"+p+"_eventos.txt"),eventos);
        escribir("Completo",p,d,c,orden,recorrido,detalle,salida);
        System.out.printf("  procesos: %d completados, %d error E/S, %d deadline%n",completados,errores,vencidos);
        return new Serie(movimiento,c);
    }

    private static void bordes() {
        for(DiskPolicy p:DiskPolicy.values()) {
            DiskScheduler d=new DiskScheduler(200,199,0,p);
            d.alCiclo(1); exigir(d.getCurrentHead()==199 && d.getTotalHeadMovement()==0 && d.getAverageWaitCycles()==0,"Cola vacia");
            d.addRequest(new DiskRequest(1,"A",DiskOperation.READ,199));
            d.alCiclo(2); exigir(d.pollCompleted().processId()==1 && d.getTotalHeadMovement()==0,"Solicitud bajo cabezal extremo");
            d.addRequest(new DiskRequest(2,"B",DiskOperation.READ,198));
            for(int c=3;c<=405 && d.getTotalRequestsServed()<2;c++) d.alCiclo(c);
            exigir(d.getTotalRequestsServed()==2,"No sale del extremo: "+p);
        }
        DiskScheduler d=new DiskScheduler(200,50,0,DiskPolicy.SSTF);
        DiskRequest a=new DiskRequest(1,"A",DiskOperation.READ,40), b=new DiskRequest(2,"B",DiskOperation.READ,60);
        d.addRequest(a); d.addRequest(b); d.alCiclo(1); d.setPolicy(DiskPolicy.FIFO);
        for(int c=2;c<=32;c++) d.alCiclo(c);
        exigir(a.getResult()!=null && b.getResult()!=null && a.getResult().policy()==DiskPolicy.SSTF
                && b.getResult().policy()==DiskPolicy.FIFO && a.getStartCycle()==1 && d.getTotalHeadMovement()==30,
                "Empate o cambio de politica incorrecto");
        System.out.println("OK: cola vacia, cabezal extremo, solicitud en cabezal, empate y cambio en ejecucion.");
    }

    private static StringBuilder encabezadoRecorrido() { return new StringBuilder("ciclo,cabezal,desplazamiento,retorno_ciclos,atendidas\n"); }
    private static StringBuilder encabezadoSolicitudes() { return new StringBuilder("proceso,operacion,bloque,llegada,seleccion,inicio,fin,espera,politica,estado\n"); }
    private static void muestra(DiskScheduler d,StringBuilder b,long[] m,int c) {
        m[c]=d.getTotalHeadMovement();
        b.append(c).append(',').append(d.getCurrentHead()).append(',').append(m[c]).append(',')
                .append(d.getTotalReturnCycles()).append(',').append(d.getTotalRequestsServed()).append('\n');
    }
    private static void solicitud(StringBuilder b,DiskRequest r) {
        b.append(r.getProcessName()).append(',').append(r.getOperation()).append(',').append(r.getBlockNumber()).append(',')
                .append(r.getArrivalCycle()).append(',').append(r.getSelectionCycle()).append(',').append(r.getStartCycle()).append(',')
                .append(r.getCompletionCycle()).append(',').append(r.getStartCycle()<0?-1:r.getStartCycle()-r.getArrivalCycle()).append(',')
                .append(r.getResult()==null?"":r.getResult().policy()).append(',').append(r.getStatus()).append('\n');
    }
    private static void sumarOrden(StringBuilder b,String valor) { if(b.length()>0)b.append(" -> "); b.append(valor); }
    private static void escribir(String nombre,DiskPolicy p,DiskScheduler d,int c,StringBuilder orden,
                                  StringBuilder recorrido,StringBuilder detalle,Path salida) throws Exception {
        String base=nombre+"_"+p;
        Files.writeString(salida.resolve(base+"_recorrido.csv"),recorrido);
        Files.writeString(salida.resolve(base+"_solicitudes.csv"),detalle);
        resumen.append(String.format(Locale.ROOT,"%s,%s,%d,%d,%d,%.2f,%d,%s%n",nombre,p,c,d.getTotalRequestsServed(),d.getTotalHeadMovement(),d.getAverageWaitCycles(),d.getTotalReturnCycles(),orden));
        System.out.printf(Locale.ROOT,"%s %s | ciclo=%d | atendidas=%d | mov=%d | espera=%.2f | retorno=%d%n",nombre,p,c,d.getTotalRequestsServed(),d.getTotalHeadMovement(),d.getAverageWaitCycles(),d.getTotalReturnCycles());
        System.out.println("  orden: "+orden);
    }
    private static void grafica(Path salida,String nombre,Serie[] series) throws Exception {
        int xMax=1; long yMax=1;
        for(Serie s:series) { xMax=Math.max(xMax,s.ciclos()); yMax=Math.max(yMax,s.movimiento()[s.ciclos()]); }
        xMax=((xMax+99)/100)*100; yMax=((yMax+99)/100)*100;
        StringBuilder svg=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1000\" height=\"500\" viewBox=\"0 0 1000 500\"><rect width=\"1000\" height=\"500\" fill=\"white\"/><g font-family=\"sans-serif\" fill=\"#172033\">");
        svg.append("<text x=\"70\" y=\"30\" font-size=\"22\">Desplazamiento acumulado - ").append(nombre).append("</text>");
        for(int i=0;i<=5;i++) {
            int x=75+i*175,y=410-i*68;
            svg.append("<path stroke=\"#d9dee5\" d=\"M").append(x).append(" 70V410 M75 ").append(y).append("H950\"/>");
            svg.append("<text x=\"").append(x).append("\" y=\"430\" text-anchor=\"middle\">").append(xMax*i/5).append("</text>");
            svg.append("<text x=\"65\" y=\"").append(y+5).append("\" text-anchor=\"end\">").append(yMax*i/5).append("</text>");
        }
        svg.append("<path d=\"M75 70V410H950\" stroke=\"#172033\" fill=\"none\"/><text x=\"510\" y=\"455\" text-anchor=\"middle\">Ciclo global</text><text transform=\"translate(20 240) rotate(-90)\" text-anchor=\"middle\">Bloques acumulados</text>");
        for(DiskPolicy p:DiskPolicy.values()) {
            int i=p.ordinal(); Serie s=series[i]; String color=COLORES[i];
            svg.append("<polyline fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"2.5\" points=\"");
            for(int c=0;c<=s.ciclos();c++) svg.append(String.format(Locale.ROOT,"%.2f,%.2f ",75+875.0*c/xMax,410-340.0*s.movimiento()[c]/yMax));
            svg.append("\"/><text x=\"").append(75+i*225).append("\" y=\"485\" fill=\"").append(color).append("\">").append(p).append(" (fin c").append(s.ciclos()).append(")</text>");
        }
        svg.append("</g></svg>");
        Files.writeString(salida.resolve(nombre+"_comparacion.svg"),svg);
    }
    private static void exigir(boolean ok,String mensaje) { if(!ok) throw new AssertionError(mensaje); }
}
