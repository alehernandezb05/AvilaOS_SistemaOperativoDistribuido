package edu.unimet.avilaos.gui;

import edu.unimet.avilaos.cluster.Simulador;
import edu.unimet.avilaos.config.ArchivoConfiguracion;
import edu.unimet.avilaos.config.ArchivoSistemaArchivos;
import edu.unimet.avilaos.config.Configuracion;
import edu.unimet.avilaos.config.ConfiguracionInvalidaException;
import edu.unimet.avilaos.so.ModoUsuario;
import edu.unimet.avilaos.so.PCB;
import edu.unimet.avilaos.so.TipoProceso;
import edu.unimet.avilaos.so.archivos.OperacionArchivo;
import edu.unimet.avilaos.so.archivos.SesionUsuario;
import edu.unimet.avilaos.so.archivos.SistemaArchivos;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.Timer;

/**
 * Ventana del computador: controles, CPU/RAM, metricas, log y grafico de utilizacion.
 */
public class VentanaPrincipal extends JFrame {

    public static final int REFRESCO_MS = 150;
    public static final int ALTO_PANEL_COMPUTADOR = 380;

    private final Simulador simulador;
    private final PanelControles panelControles = new PanelControles();
    private final PanelLog panelLog = new PanelLog();
    private final PanelGraficoUtilizacion panelGrafico;
    private final PanelMetricas panelMetricas;
    private final PanelComputador panelComputador;
    private final PanelSistemaArchivos panelArchivos;
    private final PanelDisco panelDisco;
    private final Timer refresco;
    private final BiConsumer<Simulador, String> alReiniciar;

    /**
     * @param alReiniciar abre una simulación ya validada y restaura la ruta seleccionada
     */
    public VentanaPrincipal(Simulador simulador, String rutaSeleccionada,
                            BiConsumer<Simulador, String> alReiniciar) {
        super("AvilaOS - Proyecto 2");
        this.simulador = simulador;
        this.alReiniciar = alReiniciar;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(panelControles, BorderLayout.NORTH);
        conectarControles();

        panelComputador = new PanelComputador(simulador.getComputador(1));
        panelComputador.setPreferredSize(new Dimension(900, ALTO_PANEL_COMPUTADOR));
        panelComputador.alCambiarPolitica(p -> {
            try {
                simulador.cambiarPolitica(1, p);
            } catch (IllegalArgumentException e) {
                error(e.getMessage());
                actualizar();
            }
        });

        panelGrafico = new PanelGraficoUtilizacion(simulador);
        panelMetricas = new PanelMetricas(simulador);
        JTabbedPane pestanasIzquierda = new JTabbedPane();
        pestanasIzquierda.addTab("Metricas", panelMetricas);

        JSplitPane inferiorDerecha = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelLog, panelGrafico);
        inferiorDerecha.setResizeWeight(0.5);

        JSplitPane inferior = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pestanasIzquierda, inferiorDerecha);
        inferior.setResizeWeight(0.4);

        JScrollPane scrollComputadores = new JScrollPane(panelComputador);
        scrollComputadores.getVerticalScrollBar().setUnitIncrement(16);
        panelArchivos = new PanelSistemaArchivos(simulador.getSistemaArchivos(), simulador.getDisco(), rutaSeleccionada,
                this::cargarSistemaArchivos, this::guardarSistemaArchivos, this::abrirDialogoCrearLectura, this::abrirDialogoCrearEntrada,
                this::abrirDialogoRenombrar, this::abrirDialogoEliminar);
        panelDisco = new PanelDisco(simulador.getDisco());
        panelDisco.alCambiarPolitica(p -> {
            try {
                simulador.cambiarPoliticaDisco(p);
            } catch (IllegalArgumentException e) {
                error(e.getMessage());
            }
            actualizar();
        });
        panelDisco.alCambiarVelocidad(v -> {
            try {
                simulador.setVelocidadDisco(v);
            } catch (IllegalArgumentException e) {
                error(e.getMessage());
            }
            actualizar();
        });
        JTabbedPane vistas = new JTabbedPane();
        vistas.addTab("Computador", scrollComputadores);
        vistas.addTab("Disco", panelDisco);
        vistas.addTab("Sistema de archivos", panelArchivos);
        if (simulador.impedimentoInicio() != null || rutaSeleccionada != null) vistas.setSelectedComponent(panelArchivos);
        JSplitPane principal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vistas, inferior);
        principal.setResizeWeight(0.6);
        add(principal, BorderLayout.CENTER);

        setSize(1400, 850);
        setLocationRelativeTo(null);

        aplicarSesion();
        // javax.swing.Timer corre en el hilo de Swing, asi que puede tocar los componentes
        refresco = new Timer(REFRESCO_MS, e -> actualizar());
        actualizar();
        refresco.start();
    }

    /** Los controles solo llaman al Simulador; no tienen logica propia. */
    private void conectarControles() {
        panelControles.setValores(simulador.getDuracionCicloMs(), simulador.getQuantum());
        panelControles.setCorriendo(false, simulador.impedimentoInicio());
        panelControles.alIniciar(e -> simulador.iniciar());
        panelControles.alPausar(e -> simulador.pausar());
        panelControles.alCambiarDuracionCiclo(simulador::setDuracionCicloMs);
        panelControles.alCambiarQuantum(q -> {
            try {
                simulador.setQuantum(q);
            } catch (IllegalArgumentException e) {
                error(e.getMessage());
                panelControles.setValores(simulador.getDuracionCicloMs(), simulador.getQuantum());
            }
        });
        panelControles.alCrearProceso(e -> abrirDialogoCrearProceso());
        panelControles.alGuardarConfiguracion(e -> guardarConfiguracion());
        panelControles.alCargarConfiguracion(e -> cargarConfiguracion());
        panelControles.alCambiarModo(this::cambiarModo);
    }

    /** Guarda en un JSON los parametros actuales del computador. */
    private void guardarConfiguracion() {
        Path archivo = SelectorArchivoJson.elegirParaGuardar(this);
        if (archivo == null) {
            return;
        }
        if (Files.exists(archivo) && JOptionPane.showConfirmDialog(this,
                "El archivo " + archivo.getFileName() + " ya existe. Reemplazarlo?", "Guardar configuracion",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ArchivoConfiguracion.guardar(simulador.getConfiguracion(), archivo);
            JOptionPane.showMessageDialog(this, "Configuracion guardada en " + archivo,
                    "Guardar configuracion", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            error("No se pudo guardar el archivo: " + e.getMessage());
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
        }
    }

    /** Lee un JSON y, si es valido y el usuario confirma, reinicia la simulacion con el. */
    private void cargarConfiguracion() {
        Path archivo = SelectorArchivoJson.elegirParaAbrir(this, "Cargar procesos");
        if (archivo == null) {
            return;
        }
        Configuracion config;
        try {
            config = ArchivoConfiguracion.cargar(archivo);
        } catch (IOException e) {
            error("No se pudo leer el archivo: " + e.getMessage());
            return;
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
            return;
        }
        if ((simulador.getCicloActual() == 0 && !simulador.estaCorriendo()) || JOptionPane.showConfirmDialog(this,
                "Cargar la configuracion reinicia la simulacion y se pierde el estado actual. Continuar?",
                "Cargar configuracion", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            reiniciarCon(config, simulador.copiaSistemaArchivos());
        }
    }

    /** Guarda el arbol, dueños, permisos y cadenas tal como estan ahora, para recargarlos con otros procesos. */
    private void guardarSistemaArchivos() {
        SistemaArchivos sistema = simulador.getSistemaArchivos();
        if (sistema == null) return;
        Path archivo = SelectorArchivoJson.elegirParaGuardar(this);
        if (archivo == null) return;
        if (Files.exists(archivo) && JOptionPane.showConfirmDialog(this,
                "El archivo " + archivo.getFileName() + " ya existe. Reemplazarlo?", "Guardar sistema de archivos",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ArchivoSistemaArchivos.guardar(simulador.copiaSistemaArchivos(), archivo);
            simulador.getLog().registrar(simulador.getCicloActual(), "Se guarda el sistema de archivos en " + archivo);
            JOptionPane.showMessageDialog(this, "Sistema de archivos guardado en " + archivo,
                    "Guardar sistema de archivos", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            error("No se pudo guardar el archivo; el anterior queda igual: " + e.getMessage());
        } catch (ConfiguracionInvalidaException e) {
            error("El estado no se pudo guardar de forma válida; el archivo anterior queda igual: " + e.getMessage());
        }
    }

    private void cargarSistemaArchivos() {
        Path archivo = SelectorArchivoJson.elegirParaAbrir(this, "Cargar sistema de archivos");
        if (archivo == null) return;
        try {
            SistemaArchivos sistema = ArchivoSistemaArchivos.cargar(archivo);
            if ((simulador.estaCorriendo() || simulador.getCicloActual() > 0)
                    && JOptionPane.showConfirmDialog(this,
                        "Cargar el sistema de archivos reinicia la simulación. ¿Continuar?",
                        "Cargar sistema de archivos", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            reiniciarCon(simulador.getConfiguracion(), sistema);
        } catch (IOException e) {
            error("No se pudo leer el archivo: " + e.getMessage());
        } catch (ConfiguracionInvalidaException e) {
            error(e.getMessage());
        }
    }

    private void reiniciarCon(Configuracion config, SistemaArchivos sistema) {
        Simulador nuevo;
        try {
            nuevo = new Simulador(config, sistema);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return;
        }
        alReiniciar.accept(nuevo, panelArchivos.rutaSeleccionada());
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Configuracion", JOptionPane.WARNING_MESSAGE);
    }

    /** Al cerrar la ventana tambien se detiene su refresco. */
    @Override
    public void dispose() {
        refresco.stop();
        super.dispose();
    }

    /** Lee el estado del simulador y repinta los paneles. */
    private void actualizar() {
        // Una sola frontera de ciclo para todos los paneles. Ningun repintado bajo el mutex.
        Runnable vista = simulador.intentarObservar(() -> {
            long ciclo = simulador.getCicloActual();
            boolean corriendo = simulador.estaCorriendo();
            String impedimento = simulador.impedimentoInicio();
            Runnable computador = panelComputador.capturar();
            Runnable grafico = panelGrafico.capturar();
            Runnable log = panelLog.capturar(simulador.getLog());
            Runnable metricas = panelMetricas.capturar();
            Runnable archivos = panelArchivos.capturar();
            Runnable disco = panelDisco.capturar();
            return () -> {
                panelControles.setCiclo(ciclo);
                panelControles.setCorriendo(corriendo, impedimento);
                computador.run();
                grafico.run();
                log.run();
                metricas.run();
                archivos.run();
                disco.run();
            };
        });
        if (vista != null) vista.run();
    }

    /** Abre el dialogo de crear proceso y lo envia al simulador. */
    private void abrirDialogoCrearProceso() {
        abrirDialogoCrearLectura(null);
    }

    private void abrirDialogoCrearLectura(String ruta) {
        DialogoCrearProceso dialogo = new DialogoCrearProceso(this, simulador.getCicloActual(), ruta,
                simulador.getSesion().solicitante());
        dialogo.setVisible(true);
        PCB proceso = dialogo.getProcesoCreado();
        if (proceso != null) {
            solicitar(proceso);
        }
    }

    private void abrirDialogoCrearEntrada(String directorio) {
        OperacionArchivo operacion = DialogoCrearEntrada.solicitar(this, directorio,
                simulador.getDisco().getTotalBlocks(), simulador.getSesion().solicitante(),
                simulador.getSesion().esAdministrador());
        enviarOperacion(operacion, "Crear ");
    }

    private void abrirDialogoRenombrar(String ruta) {
        OperacionArchivo operacion = DialogoOperacionArchivo.renombrar(this, ruta, simulador.getSesion().solicitante());
        if (enviarOperacion(operacion, "Renombrar ")) {
            String padre = ruta.substring(0, ruta.lastIndexOf('/'));
            panelArchivos.seguirRenombre(ruta,
                    SistemaArchivos.rutaHija(padre.isEmpty() ? "/" : padre, operacion.nuevoNombre()));
        }
    }

    private void abrirDialogoEliminar(String ruta) {
        enviarOperacion(DialogoOperacionArchivo.eliminar(this, ruta, simulador.getSesion().solicitante()), "Eliminar ");
    }

    /** La interfaz solo crea el proceso de E/S; el sistema de archivos cambia cuando el disco lo atiende. */
    private boolean enviarOperacion(OperacionArchivo operacion, String prefijoNombre) {
        return operacion != null && solicitar(new PCB(prefijoNombre + operacion.ruta(), 1, TipoProceso.IO_BOUND,
                1, 1, 1, DialogoCrearProceso.MAX_DEADLINE, simulador.getCicloActual(), operacion));
    }

    /** El simulador valida el modo de uso; un rechazo se explica y la simulacion sigue. */
    private boolean solicitar(PCB proceso) {
        try {
            simulador.solicitarProceso(proceso);
            return true;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Acceso denegado", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    /** Cambia el modo de uso y habilita solo lo que ese modo puede hacer. */
    private void cambiarModo(ModoUsuario modo, String usuario) {
        try {
            simulador.setSesion(new SesionUsuario(modo, usuario));
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        }
        aplicarSesion();
    }

    private void aplicarSesion() {
        SesionUsuario sesion = simulador.getSesion();
        boolean admin = sesion.esAdministrador();
        panelControles.setSesion(sesion);
        panelComputador.setPoliticaEditable(admin);
        panelDisco.setControlesEditables(admin);
        panelArchivos.setAdministrador(admin);
    }
}
