# AvilaOS

Simulador de sistemas operativos en Java. El Proyecto 2 parte de un unico
computador con CPU, RAM, planificacion y metricas del Proyecto 1.

## Requisitos y ejecucion

- JDK 25 y Apache NetBeans 31. El proyecto compila para Java 25.
- En NetBeans, abrir la carpeta `AvilaOS`, comprobar que usa JDK 25 y ejecutar
  **Run Project**. La clase principal es `edu.unimet.avilaos.AvilaOS`.
- Elegir RAM, politica de CPU, duracion de ciclo y quantum. La cantidad de
  computadores es fija: uno. No es necesario crear buffers ni configurar red.
- El quantum puede ser cero con FCFS, EDF y Prioridades. Antes de elegir Round
  Robin, configurar un quantum entre 1 y 50. Los cambios invalidos se explican
  y conservan la configuracion anterior.
- Para una carga de ejemplo, seleccionar `escenarios/07_un_computador.json`
  con **Cargar desde archivo**. Incluye CPU, E/S y un proceso que espera RAM
  hasta vencer su deadline. Las configuraciones del Proyecto 1 con varios
  computadores o buffers se rechazan con un mensaje, sin modificarlas.

La carga obligatoria de los dos JSON del Proyecto 2 y la conexion de E/S al
disco se incorporan en las tareas de sistema de archivos e integracion. Este
primer cambio conserva la E/S temporal del Proyecto 1.

## Verificacion

Desde la carpeta `AvilaOS`, con Java 25 y Maven disponibles:

```sh
./verificar.sh
```

El script compila y ejecuta comprobaciones Java de configuracion, cambios de
quantum/politica, PC/MAR, memoria, deadlines, metricas, E/S y guardado/carga.
Usa las dependencias existentes; no agrega bibliotecas de pruebas. Tambien
se puede ejecutar un grupo, por ejemplo `./verificar.sh memoria`.

En macOS se puede usar directamente el Maven incluido con NetBeans:

```sh
MVN="/Applications/Apache NetBeans.app/Contents/Resources/netbeans/java/maven/bin/mvn" ./verificar.sh
```

`mvn test` compila las comprobaciones pero no ejecuta este programa de pruebas
sin un framework adicional; usar `verificar.sh` para ejecutarlas.

Se mantienen Gson 2.11.0 para JSON y JFreeChart 1.5.5 para graficas. Las
estructuras del simulador son propias; Swing, hilos y semaforos son del JDK.
