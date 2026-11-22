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
        ram.liberar(proceso.getMemoria());
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
            case UPDATE -> throw new IllegalArgumentException(operacion.nuevoNombre() == null
                    ? "UPDATE requiere newName." : "UPDATE todavía no está disponible.");
            default -> throw new IllegalArgumentException(operacion.tipo() + " todavía no está disponible.");
        };
    }

    private void finalizarCreacion(DiskRequest solicitud, boolean confirmar) {
        if (solicitud.getOperation() == DiskOperation.CREATE) {
            archivos.finalizarCreacion(solicitud.getId(), confirmar, disco.getStorage());
        }
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
            PCB proceso = buscarBloqueado(resultado);
            if (proceso == null || !colas.quitarBloqueado(proceso)) {
                // Un CREATE vencido conserva su reserva hasta que el disco deja de usarla.
                if (archivos != null && resultado.operation() == DiskOperation.CREATE) {
                    archivos.finalizarCreacion(resultado.requestId(), false, disco.getStorage());
                }
                continue;
            }
            DiskRequest solicitud = proceso.getSolicitudDisco();
            try {
                finalizarCreacion(solicitud, true);
            } catch (IllegalArgumentException e) {
                finalizarCreacion(solicitud, false);
                rechazarOperacionArchivo(proceso, e.getMessage());
                continue;
            }
            colas.agregarListo(proceso);
            registrar("termina la solicitud " + resultado.requestId() + " " + resultado.operation()
                    + " " + proceso.getOperacionArchivo().ruta() + " de " + proceso + " (bloqueado -> listo)");
        }
    }

    private PCB buscarBloqueado(DiskCompletion resultado) {
        for (int i = 0; i < colas.cantidadBloqueados(); i++) {
            PCB proceso = colas.obtenerBloqueado(i);
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
            if (esperaTemporal && bloqueado.avanzarEspera()) {
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
        int i = 0;
        PCB p = colas.obtenerNuevo(i);
        while (p != null) {
            if (ram.reservar(p.getMemoria())) {
                colas.quitarNuevo(p);
                colas.agregarListo(p);
                registrar("admite el proceso " + p + " (nuevo -> listo)");
            } else {
                i++;
            }
            p = colas.obtenerNuevo(i);
        }
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
