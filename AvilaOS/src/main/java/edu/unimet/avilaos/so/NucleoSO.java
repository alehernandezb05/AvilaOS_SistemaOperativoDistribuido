package edu.unimet.avilaos.so;

import edu.unimet.avilaos.hardware.CPU;
import edu.unimet.avilaos.hardware.ComponenteReloj;
import edu.unimet.avilaos.hardware.ModoEjecucion;
import edu.unimet.avilaos.hardware.RAM;
import edu.unimet.avilaos.hardware.disco.DiskCompletion;
import edu.unimet.avilaos.hardware.disco.DiskRequest;
import edu.unimet.avilaos.hardware.disco.DiskScheduler;
import edu.unimet.avilaos.hardware.disco.enums.DiskOperation;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public class NucleoSO implements ComponenteReloj {

    private final int idComputador;
    private final CPU cpu;
    private final RAM ram;
    private final DiskScheduler disco;
    private final SistemaArchivos archivos;
    private final ColasSO colas = new ColasSO();
    private volatile EstrategiaPlanificacion estrategia;
    private int ciclosEnCpu = 0;
    private volatile Log log;
    private volatile ServicioCluster servicio;
    private volatile long cicloActual = 0;

    public NucleoSO(int idComputador, CPU cpu, RAM ram, EstrategiaPlanificacion estrategia,
                    DiskScheduler disco, SistemaArchivos archivos) {
        this.idComputador = idComputador;
        this.cpu = cpu;
        this.ram = ram;
        this.estrategia = estrategia;
        this.disco = disco;
        this.archivos = archivos;
    }

    public void agregarProceso(PCB proceso) {
        colas.agregarNuevo(proceso);
    }

    @Override
    public void alCiclo(long cicloGlobal) {
        cicloActual = cicloGlobal;
        avanzarBloqueados();
        procesarProtocolo();
        enviarSolicitudDisco();
        terminarSiCorresponde();
        bloquearPorESSiCorresponde();
        procesarDeadlines(cicloGlobal);
        admitirNuevos();
        // Despues de admitir: solo pierden deadline los que siguen esperando memoria
        procesarDeadlinesNuevos(cicloGlobal);
        planificar();
        recibirFinalizacionesDisco();
        if (!cpu.estaLibre()) {
            ciclosEnCpu++;
        }
        actualizarModo();
    }

    public void desbloquear(PCB proceso, long ciclo) {
        if (colas.quitarBloqueado(proceso)) {
            proceso.confirmarEsperaCumplida();
            colas.agregarListo(proceso);
            Log l = log;
            if (l != null) {
                l.registrar(ciclo, "Computador " + idComputador + " desbloquea el proceso " + proceso
                        + " (bloqueado -> listo)");
            }
        }
    }

    private void finalizar(PCB proceso, MotivoTerminacion motivo, long ciclo) {
        Semaforo esperado = proceso.getSemaforoEsperado();
        if (esperado != null) {
            esperado.quitar(proceso);
            proceso.setSemaforoEsperado(null);
        }
        if (proceso.esProductorConsumidor()) {
            liberarRecursosBuffer(proceso);
        }
        // Un proceso ya escrito en swap no ocupa RAM; uno en plena escritura todavia la ocupa,
        // y uno que se esta leyendo del swap ya tiene su RAM de destino reservada.
        if (!proceso.isEnSwap() || proceso.reactivacionEnCurso()) ram.liberar(proceso.getMemoria());
        if (proceso.isEnSwap() || proceso.suspensionEnCurso()) disco.getStorage().releaseSwap(proceso.getId());
        proceso.terminar(motivo, ciclo);
        colas.agregarTerminado(proceso);
    }

    private void liberarRecursosBuffer(PCB p) {
        ServicioCluster s = servicio;
        Buffer buffer = (s == null || p.getIdBuffer() == null) ? null : s.getBuffer(p.getIdBuffer());
        if (buffer == null) {
            return;
        }
        boolean productor = p.getTipo() == TipoProceso.PRODUCTOR;
        Semaforo conteoPropio = productor ? buffer.getVacios() : buffer.getLlenos();
        Semaforo conteoOpuesto = productor ? buffer.getLlenos() : buffer.getVacios();
        boolean teniaRecursos = p.isMutexAdquirido() || p.isAccionHecha() || p.isConteoAdquirido();
        if (p.isMutexAdquirido()) {
            senalar(buffer.getMutex());
            p.setMutexAdquirido(false);
        }
        if (p.isAccionHecha()) {
            senalar(conteoOpuesto);
        } else if (p.isConteoAdquirido()) {
            senalar(conteoPropio);
        }
        p.finalizarElemento();
        if (teniaRecursos) {
            registrar("libera los recursos que el proceso " + p + " tenia en el buffer " + buffer.getId());
        }
    }

    private void terminarSiCorresponde() {
        PCB actual = cpu.getProcesoActual();
        if (actual != null && actual.haTerminado()) {
            cpu.liberar();
            ciclosEnCpu = 0;
            finalizarCompletado(actual);
        }
    }

    private void finalizarCompletado(PCB proceso) {
        finalizar(proceso, MotivoTerminacion.COMPLETADO, cicloActual);
        registrar("termina el proceso " + proceso + " y libera " + proceso.getMemoria() + " de RAM");
    }

    private void enviarSolicitudDisco() {
        PCB proceso = cpu.getProcesoActual();
        if (proceso == null || !proceso.debeEnviarSolicitudDisco()) return;
        OperacionArchivo operacion = proceso.getOperacionArchivo();
        DiskRequest solicitud = null;
        try {
            solicitud = prepararSolicitudDisco(proceso, operacion);
            if (!disco.addRequest(solicitud)) {
                finalizarCreacion(solicitud, false);
                return;
            }
        } catch (IllegalArgumentException e) {
            if (solicitud != null) finalizarCreacion(solicitud, false);
            cpu.liberar();
            ciclosEnCpu = 0;
            rechazarOperacionArchivo(proceso, e.getMessage());
            return;
        }
        proceso.registrarSolicitudDisco(solicitud);
        cpu.liberar();
        ciclosEnCpu = 0;
        colas.agregarBloqueado(proceso, MotivoBloqueo.ESPERA_DISCO);
        registrar("encola solicitud " + solicitud.getId() + " " + operacion.tipo() + " " + operacion.ruta()
                + " de " + proceso + " (ejecución -> bloqueado)");
    }

    private DiskRequest prepararSolicitudDisco(PCB proceso, OperacionArchivo operacion) {
        if (archivos == null) throw new IllegalArgumentException("Falta cargar el sistema de archivos.");
        return switch (operacion.tipo()) {
            case READ -> {
                var archivo = archivos.archivoParaLectura(operacion.ruta(), operacion.solicitante());
                int[] bloques = disco.getStorage().getFileChain(archivo.ruta(), archivo.primerBloque());
                yield new DiskRequest(proceso.getId(), proceso.getNombre(), operacion.tipo(), bloques);
            }
            case CREATE -> archivos.prepararCreacion(operacion, proceso.getId(), proceso.getNombre(), disco.getStorage());
            case UPDATE -> archivos.prepararActualizacion(operacion, proceso.getId(), proceso.getNombre());
            case DELETE -> archivos.prepararEliminacion(operacion, proceso.getId(), proceso.getNombre());
            default -> throw new IllegalArgumentException(operacion.tipo() + " no es una operación de archivo.");
        };
    }

    private void finalizarCreacion(DiskRequest solicitud, boolean confirmar) {
        if (cambiaSistemaArchivos(solicitud.getOperation())) {
            archivos.finalizarOperacion(solicitud.getId(), confirmar, disco.getStorage());
        }
    }

    private static boolean cambiaSistemaArchivos(DiskOperation operacion) {
        return operacion == DiskOperation.CREATE || operacion == DiskOperation.UPDATE
                || operacion == DiskOperation.DELETE;
    }

    private void rechazarOperacionArchivo(PCB proceso, String motivo) {
        finalizar(proceso, MotivoTerminacion.ERROR_ES, cicloActual);
        OperacionArchivo operacion = proceso.getOperacionArchivo();
        registrar("rechaza " + operacion.tipo() + " " + operacion.ruta() + " de " + proceso + ". " + motivo);
    }

    /** Publica los despertares después de planificar para que pasen por Listo. */
    private void recibirFinalizacionesDisco() {
        DiskCompletion resultado;
        while ((resultado = disco.pollCompleted()) != null) {
            if (resultado.operation() == DiskOperation.SUSPEND) {
                completarSuspension(resultado);
                continue;
            }
            if (resultado.operation() == DiskOperation.REACTIVATE) {
                completarReactivacion(resultado);
                continue;
            }
            PCB proceso = buscarEsperandoDisco(resultado, colas::cantidadBloqueados, colas::obtenerBloqueado);
            PCB suspendido = proceso != null ? null : buscarEsperandoDisco(resultado,
                    colas::cantidadBloqueadosSuspendidos, colas::obtenerBloqueadoSuspendido);
            // Si se esta escribiendo al swap, sigue en Bloqueados hasta que termine esa escritura.
            boolean enTransito = proceso != null && proceso.suspensionEnCurso();
            boolean vigente = suspendido != null ? colas.quitarBloqueadoSuspendido(suspendido)
                    : proceso != null && (enTransito || colas.quitarBloqueado(proceso));
            if (!vigente) {
                // Un CREATE, UPDATE o DELETE vencido no se publica y libera su reserva al drenar el disco.
                if (archivos != null && cambiaSistemaArchivos(resultado.operation())) {
                    archivos.finalizarOperacion(resultado.requestId(), false, disco.getStorage());
                }
                continue;
            }
            if (suspendido != null) proceso = suspendido;
            DiskRequest solicitud = proceso.getSolicitudDisco();
            try {
                finalizarCreacion(solicitud, true);
            } catch (IllegalArgumentException e) {
                finalizarCreacion(solicitud, false);
                if (enTransito) colas.quitarBloqueado(proceso);
                rechazarOperacionArchivo(proceso, e.getMessage());
                continue;
            }
            OperacionArchivo hecha = proceso.getOperacionArchivo();
            if (resultado.operation() == DiskOperation.UPDATE) {
                registrar("renombra " + hecha.ruta() + " a " + hecha.nuevoNombre());
            } else if (resultado.operation() == DiskOperation.DELETE) {
                registrar("elimina " + hecha.ruta() + " y libera sus bloques");
            }
            String fin = "termina la solicitud " + resultado.requestId() + " " + resultado.operation()
                    + " " + hecha.ruta() + " de " + proceso;
            if (suspendido != null) {
                colas.agregarListoSuspendido(proceso);
                registrar(fin + " (bloqueado/suspendido -> listo/suspendido)");
            } else if (enTransito) {
                proceso.registrarEventoDuranteSuspension();
                registrar(fin + " mientras se escribe al swap; quedara listo/suspendido");
            } else {
                colas.agregarListo(proceso);
                registrar(fin + " (bloqueado -> listo)");
            }
        }
    }

    /**
     * Termina la escritura al swap: recien ahora se libera la RAM, una sola vez.
     * Si el evento ocurrio durante la escritura, el proceso queda Listo/Suspendido.
     */
    private void completarSuspension(DiskCompletion resultado) {
        PCB proceso = null;
        for (int i = 0; i < colas.cantidadBloqueados() && proceso == null; i++) {
            PCB candidato = colas.obtenerBloqueado(i);
            DiskRequest escritura = candidato == null ? null : candidato.getSolicitudSwap();
            if (escritura != null && escritura.getId() == resultado.requestId()) proceso = candidato;
        }
        // Si termino antes (por deadline), finalizar ya libero su RAM y su swap.
        if (proceso == null || !colas.quitarBloqueado(proceso)) return;
        ram.liberar(proceso.getMemoria());
        proceso.completarSuspension();
        if (proceso.isEventoDuranteSuspension()) {
            colas.agregarListoSuspendido(proceso);
            registrar("escribe " + proceso + " en el swap y libera " + proceso.getMemoria()
                    + " de RAM (bloqueado -> listo/suspendido)");
        } else {
            colas.agregarBloqueadoSuspendido(proceso);
            registrar("escribe " + proceso + " en el swap y libera " + proceso.getMemoria()
                    + " de RAM (bloqueado -> bloqueado/suspendido)");
        }
        admitirNuevos();
    }

    private static PCB buscarEsperandoDisco(DiskCompletion resultado, IntSupplier cantidad,
                                           IntFunction<PCB> obtener) {
        for (int i = 0; i < cantidad.getAsInt(); i++) {
            PCB proceso = obtener.apply(i);
            if (proceso == null || proceso.getMotivoBloqueo() != MotivoBloqueo.ESPERA_DISCO) continue;
            DiskRequest pendiente = proceso.getSolicitudDisco();
            if (proceso.getId() == resultado.processId() && pendiente != null
                    && pendiente.getId() == resultado.requestId()) return proceso;
        }
        return null;
    }

    private void avanzarBloqueados() {
        int i = 0;
        PCB bloqueado = colas.obtenerBloqueado(i);
        while (bloqueado != null) {
            bloqueado.sumarCicloBloqueado();
            MotivoBloqueo motivo = bloqueado.getMotivoBloqueo();
            if (motivo == MotivoBloqueo.ESPERA_SEMAFORO) {
                bloqueado.sumarCicloBloqueadoSemaforo();
            }
            boolean esperaTemporal = motivo == MotivoBloqueo.ESPERA_ES || motivo == MotivoBloqueo.ESPERA_RED;
            if (esperaTemporal && bloqueado.suspensionEnCurso()) {
                // Se escribe al swap: no puede volver a Listo con su RAM a medio liberar.
                if (!bloqueado.isEventoDuranteSuspension() && bloqueado.avanzarEspera()) {
                    bloqueado.registrarEventoDuranteSuspension();
                    registrar("termina la E/S del proceso " + bloqueado
                            + " mientras se escribe al swap; quedara listo/suspendido");
                }
                i++;
            } else if (esperaTemporal && bloqueado.avanzarEspera()) {
                colas.quitarBloqueado(bloqueado);
                colas.agregarListo(bloqueado);
                if (motivo == MotivoBloqueo.ESPERA_ES) {
                    registrar("termina la E/S del proceso " + bloqueado + " (bloqueado -> listo)");
                } else {
                    registrar("termina la espera de red del proceso " + bloqueado + " (bloqueado -> listo)");
                }
            } else {
                i++;
            }
            bloqueado = colas.obtenerBloqueado(i);
        }
        avanzarBloqueadosSuspendidos();
    }

    /** Un suspendido sigue esperando su evento; al cumplirse pasa a Listo/Suspendido, no a Listo. */
    private void avanzarBloqueadosSuspendidos() {
        int i = 0;
        PCB suspendido = colas.obtenerBloqueadoSuspendido(i);
        while (suspendido != null) {
            suspendido.sumarCicloBloqueado();
            if (suspendido.getMotivoBloqueo() == MotivoBloqueo.ESPERA_ES && suspendido.avanzarEspera()) {
                colas.quitarBloqueadoSuspendido(suspendido);
                colas.agregarListoSuspendido(suspendido);
                registrar("termina la E/S del proceso " + suspendido
                        + " (bloqueado/suspendido -> listo/suspendido)");
            } else {
                i++;
            }
            suspendido = colas.obtenerBloqueadoSuspendido(i);
        }
    }

    private void procesarProtocolo() {
        PCB p = cpu.getProcesoActual();
        if (p == null || !p.esProductorConsumidor()) {
            return;
        }
        ServicioCluster s = servicio;
        Buffer buffer = (s == null || p.getIdBuffer() == null) ? null : s.getBuffer(p.getIdBuffer());
        if (buffer == null) {
            cpu.liberar();
            ciclosEnCpu = 0;
            colas.agregarBloqueado(p, MotivoBloqueo.ESPERA_SEMAFORO);
            registrar("no encuentra el buffer del proceso " + p + " y lo bloquea");
            return;
        }
        boolean productor = p.getTipo() == TipoProceso.PRODUCTOR;
        Semaforo conteoPropio = productor ? buffer.getVacios() : buffer.getLlenos();
        Semaforo conteoOpuesto = productor ? buffer.getLlenos() : buffer.getVacios();
        switch (p.getPaso()) {
            case TRABAJO -> {
                if (p.avanzarTrabajo()) {
                    p.siguientePaso();
                }
            }
            case ESPERAR_CONTEO -> {
                if (buffer.esRemoto(p) && !p.isAccesoPagado() && s.getLatenciaRed() > 0) {
                    bloquearPorRed(p, buffer, s.getLatenciaRed());
                } else {
                    esperar(p, conteoPropio);
                }
            }
            case ESPERAR_MUTEX -> esperar(p, buffer.getMutex());
            case ACCEDER_BUFFER -> accederBuffer(p, buffer, productor);
            case LIBERAR_MUTEX -> {
                senalar(buffer.getMutex());
                p.setMutexAdquirido(false);
                p.siguientePaso();
            }
            case LIBERAR_CONTEO -> {
                senalar(conteoOpuesto);
                p.finalizarElemento();
                p.siguientePaso();
            }
        }
    }

    private void esperar(PCB p, Semaforo semaforo) {
        boolean continua = semaforo.semWait(p);
        if (continua) {
            p.confirmarAdquisicion();
            p.siguientePaso();
            return;
        }
        p.siguientePaso();
        p.setSemaforoEsperado(semaforo);
        cpu.liberar();
        ciclosEnCpu = 0;
        colas.agregarBloqueado(p, MotivoBloqueo.ESPERA_SEMAFORO);
        registrar("bloquea el proceso " + p + " en el semaforo '" + semaforo.getNombre()
                + "' (ejecucion -> bloqueado)");
    }

    private void senalar(Semaforo semaforo) {
        PCB despertado = semaforo.semSignal();
        ServicioCluster s = servicio;
        if (despertado != null && s != null) {
            s.desbloquear(despertado, cicloActual);
        }
    }

    private void accederBuffer(PCB p, Buffer buffer, boolean productor) {
        if (productor) {
            buffer.insertar();
        } else {
            buffer.extraer();
        }
        p.registrarAccion();
        p.siguientePaso();
        registrar("el proceso " + p + (productor ? " inserta un elemento en el buffer " : " extrae un elemento del buffer ")
                + buffer);
    }

    private void bloquearPorRed(PCB p, Buffer buffer, int latencia) {
        p.setAccesoPagado(true);
        p.iniciarEsperaRed(latencia);
        cpu.liberar();
        ciclosEnCpu = 0;
        colas.agregarBloqueado(p, MotivoBloqueo.ESPERA_RED);
        registrar("el proceso " + p + " accede al buffer " + buffer.getId() + " de forma remota (espera "
                + latencia + " ciclos)");
    }

    private void bloquearPorESSiCorresponde() {
        PCB actual = cpu.getProcesoActual();
        if (actual != null && actual.debePedirES()) {
            cpu.liberar();
            ciclosEnCpu = 0;
            actual.iniciarES();
            colas.agregarBloqueado(actual, MotivoBloqueo.ESPERA_ES);
            registrar("bloquea el proceso " + actual + " por E/S durante "
                    + actual.getDuracionES() + " ciclos (ejecucion -> bloqueado)");
        }
    }

    private void procesarDeadlines(long cicloGlobal) {
        PCB actual = cpu.getProcesoActual();
        if (actual != null) {
            actual.decrementarDeadline();
            if (actual.deadlineVencido()) {
                cpu.liberar();
                ciclosEnCpu = 0;
                finalizar(actual, MotivoTerminacion.DEADLINE_VENCIDO, cicloGlobal);
                registrar("termina el proceso " + actual + " por vencimiento de deadline");
            }
        }

        int i = 0;
        PCB listo = colas.obtenerListo(i);
        while (listo != null) {
            if (listo.haTerminado()) {
                colas.quitarListo(listo);
                finalizarCompletado(listo);
            } else if (venceDeadline(listo)) {
                colas.quitarListo(listo);
                finalizar(listo, MotivoTerminacion.DEADLINE_VENCIDO, cicloGlobal);
                registrar("vence deadline del proceso " + listo + " en cola de listos");
            } else {
                i++;
            }
            listo = colas.obtenerListo(i);
        }

        int j = 0;
        PCB bloqueado = colas.obtenerBloqueado(j);
        while (bloqueado != null) {
            bloqueado.decrementarDeadline();
            if (bloqueado.deadlineVencido()) {
                colas.quitarBloqueado(bloqueado);
                finalizar(bloqueado, MotivoTerminacion.DEADLINE_VENCIDO, cicloGlobal);
                registrar("vence deadline del proceso " + bloqueado + " en cola de bloqueados");
            } else {
                j++;
            }
            bloqueado = colas.obtenerBloqueado(j);
        }
    }

    private boolean venceDeadline(PCB proceso) {
        proceso.decrementarDeadline();
        return proceso.deadlineVencido();
    }

    private void admitirNuevos() {
        // Memoria que piden los Nuevos que no caben, en orden de llegada
        int demanda = 0;
        int i = 0;
        PCB p = colas.obtenerNuevo(i);
        while (p != null) {
            if (ram.reservar(p.getMemoria())) {
                colas.quitarNuevo(p);
                colas.agregarListo(p);
                registrar("admite el proceso " + p + " (nuevo -> listo)");
            } else {
                // Uno que nunca cabe no debe frenar las reactivaciones
                if (p.getMemoria() <= ram.getTotal()) demanda += p.getMemoria();
                suspenderPara(p, demanda);
                i++;
            }
            p = colas.obtenerNuevo(i);
        }
        reactivarSuspendidos(demanda);
    }

    /**
     * Reactiva Listos/Suspendidos en orden de llegada a esa cola, con la RAM que sobra despues de los Nuevos.
     * Los Nuevos tienen prioridad porque son los unicos que provocan suspensiones: una reactivacion
     * solo usa RAM que no necesitan, contando la que ya se esta liberando. Asi una reactivacion nunca
     * obliga a suspender a otro proceso y no se forman ciclos de suspension y reactivacion.
     * Bloqueados/Suspendidos no se reactivan: su evento no ha ocurrido.
     */
    private void reactivarSuspendidos(int demandaNuevos) {
        for (int i = 0; i < colas.cantidadListosSuspendidos(); i++) {
            PCB p = colas.obtenerListoSuspendido(i);
            if (p == null || p.reactivacionEnCurso()) continue;
            int sobrante = ram.getLibre() + memoriaEscribiendoseAlSwap() - demandaNuevos;
            if (p.getMemoria() > Math.min(ram.getLibre(), sobrante)) continue;
            int[] bloques = disco.getStorage().getSwapBlocks(p.getId());
            if (bloques.length == 0 || !ram.reservar(p.getMemoria())) continue;
            DiskRequest lectura = new DiskRequest(p.getId(), p.getNombre(), DiskOperation.REACTIVATE, bloques);
            if (!disco.addRequest(lectura)) {
                ram.liberar(p.getMemoria());
                continue;
            }
            p.iniciarReactivacion(lectura);
            registrar("reactiva " + p + ": reserva " + p.getMemoria() + " de RAM y lee " + bloques.length
                    + " bloques del swap con la solicitud " + lectura.getId());
        }
    }

    /** Termina la lectura del swap: libera sus bloques y el proceso vuelve a Listo con su contexto intacto. */
    private void completarReactivacion(DiskCompletion resultado) {
        PCB proceso = null;
        for (int i = 0; i < colas.cantidadListosSuspendidos() && proceso == null; i++) {
            PCB candidato = colas.obtenerListoSuspendido(i);
            DiskRequest lectura = candidato == null ? null : candidato.getSolicitudReactivacion();
            if (lectura != null && lectura.getId() == resultado.requestId()) proceso = candidato;
        }
        // Si termino antes (por deadline), finalizar ya libero su RAM reservada y su swap.
        if (proceso == null || !colas.quitarListoSuspendido(proceso)) return;
        disco.getStorage().releaseSwap(proceso.getId());
        proceso.completarReactivacion();
        colas.agregarListo(proceso);
        registrar("lee " + proceso + " del swap y libera sus bloques (listo/suspendido -> listo)");
    }

    /**
     * Planificador a mediano plazo. Suspende Bloqueados hasta cubrir la demanda de los Nuevos
     * que esperan, contando la RAM que ya se esta escribiendo al swap para no suspender de mas.
     * Cada suspension es una escritura SUSPEND en la misma cola del disco; la RAM se libera al terminarla.
     */
    private void suspenderPara(PCB nuevo, int demanda) {
        if (nuevo.getMemoria() > ram.getTotal()) {
            avisarAdmision(nuevo, "no cabe en la RAM aunque se suspendan todos los procesos");
            return;
        }
        int faltante = demanda - ram.getLibre() - memoriaEscribiendoseAlSwap();
        while (faltante > 0) {
            PCB victima = elegirVictima();
            if (victima == null) {
                avisarAdmision(nuevo, "espera RAM: no hay procesos bloqueados para suspender");
                return;
            }
            int[] bloques = disco.getStorage().allocateSwap(victima.getId(), victima.getMemoria());
            if (bloques.length == 0) {
                avisarAdmision(nuevo, "espera RAM: el swap no tiene " + victima.getMemoria()
                        + " bloques libres para suspender a " + victima);
                return;
            }
            DiskRequest escritura = new DiskRequest(victima.getId(), victima.getNombre(), DiskOperation.SUSPEND, bloques);
            if (!disco.addRequest(escritura)) {
                disco.getStorage().releaseSwap(victima.getId());
                return;
            }
            victima.iniciarSuspension(escritura);
            faltante -= victima.getMemoria();
            registrar("suspende " + victima + " (" + victima.getMotivoBloqueo() + ", bloqueado hace "
                    + victima.getCiclosBloqueoActual() + " ciclos) para admitir " + nuevo + ": escribe "
                    + victima.getMemoria() + " bloques al swap con la solicitud " + escritura.getId());
        }
    }

    /**
     * Victima: el Bloqueado que mas ciclos lleva en su bloqueo actual; en empate, el de menor ID.
     * Solo esperas de E/S o de disco, que pueden cumplirse con el proceso en swap.
     */
    private PCB elegirVictima() {
        PCB elegida = null;
        for (int i = 0; i < colas.cantidadBloqueados(); i++) {
            PCB p = colas.obtenerBloqueado(i);
            if (p == null || p.suspensionEnCurso()) continue;
            MotivoBloqueo motivo = p.getMotivoBloqueo();
            if (motivo != MotivoBloqueo.ESPERA_ES && motivo != MotivoBloqueo.ESPERA_DISCO) continue;
            if (elegida == null || p.getCiclosBloqueoActual() > elegida.getCiclosBloqueoActual()
                    || p.getCiclosBloqueoActual() == elegida.getCiclosBloqueoActual() && p.getId() < elegida.getId()) {
                elegida = p;
            }
        }
        return elegida;
    }

    private int memoriaEscribiendoseAlSwap() {
        int total = 0;
        for (int i = 0; i < colas.cantidadBloqueados(); i++) {
            PCB p = colas.obtenerBloqueado(i);
            if (p != null && p.suspensionEnCurso()) total += p.getMemoria();
        }
        return total;
    }

    /** Registra por que un Nuevo sigue esperando, sin repetir el mismo mensaje cada ciclo. */
    private void avisarAdmision(PCB nuevo, String motivo) {
        if (motivo.equals(nuevo.getAvisoAdmision())) return;
        nuevo.setAvisoAdmision(motivo);
        registrar("no admite " + nuevo + ": " + motivo);
    }

    /** Vence el deadline de los procesos que no han sido admitidos. No liberan RAM porque nunca la reservaron. */
    private void procesarDeadlinesNuevos(long cicloGlobal) {
        int i = 0;
        PCB nuevo = colas.obtenerNuevo(i);
        while (nuevo != null) {
            nuevo.decrementarDeadline();
            if (nuevo.deadlineVencido()) {
                colas.quitarNuevo(nuevo);
                nuevo.terminar(MotivoTerminacion.DEADLINE_VENCIDO, cicloGlobal);
                colas.agregarTerminado(nuevo);
                registrar("vence deadline del proceso " + nuevo + " en cola de nuevos, sin haber sido admitido");
            } else {
                i++;
            }
            nuevo = colas.obtenerNuevo(i);
        }
    }

    private void planificar() {
        PCB actual = cpu.getProcesoActual();
        if (actual != null && colas.debeExpropiar(estrategia, actual, ciclosEnCpu)) {
            cpu.liberar();
            colas.agregarListo(actual);
            ciclosEnCpu = 0;
            registrar("expropia el proceso " + actual + " (" + estrategia.getPolitica() + ")");
            actual = null;
        }
        if (actual == null) {
            PCB siguiente = colas.seleccionarSiguiente(estrategia);
            if (siguiente != null) {
                siguiente.setEstado(EstadoProceso.EJECUCION);
                siguiente.registrarPrimeraEjecucion(cicloActual);
                cpu.asignar(siguiente);
                ciclosEnCpu = 0;
                registrar("selecciona el proceso " + siguiente);
            }
        }
    }

    private void actualizarModo() {
        PCB p = cpu.getProcesoActual();
        if (p == null) {
            cpu.setModo(ModoEjecucion.SISTEMA_OPERATIVO);
        } else if (p.ejecutaraOperacionArchivo() || (p.esProductorConsumidor() && p.getPaso() != PasoProtocolo.TRABAJO)) {
            cpu.setModo(ModoEjecucion.SISTEMA_OPERATIVO);
        } else {
            cpu.setModo(ModoEjecucion.USUARIO);
        }
    }

    private void registrar(String mensaje) {
        Log l = log;
        if (l != null) {
            l.registrar(cicloActual, "Computador " + idComputador + " " + mensaje);
        }
    }

    public void setEstrategia(EstrategiaPlanificacion estrategia) {
        this.estrategia = estrategia;
        registrar("cambia su politica a " + estrategia.getPolitica());
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void setServicio(ServicioCluster servicio) {
        this.servicio = servicio;
    }

    public EstrategiaPlanificacion getEstrategia() { return estrategia; }
    public int getIdComputador() { return idComputador; }
    public CPU getCpu() { return cpu; }
    public RAM getRam() { return ram; }
    public ColasSO getColas() { return colas; }
}
