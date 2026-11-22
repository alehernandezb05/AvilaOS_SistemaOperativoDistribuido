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
cambio conserva la E/S temporal de los procesos del Proyecto 1; el disco ya
comparte su reloj, pero la conexion de los PCBs a las solicitudes corresponde
a la integracion de E/S.

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

## Disco y reloj global

El computador registra el disco antes del nucleo y de la CPU en el mismo
reloj. El disco atiende una sola solicitud a la vez; el movimiento consume
ciclos segun su velocidad y cada bloque ocupa otro ciclo de transferencia.
FIFO conserva el orden de llegada y SSTF elige el bloque mas cercano; ante
un empate conserva el orden de llegada. Cambiar politica afecta la siguiente
seleccion, sin interrumpir la solicitud activa.

`DiskRequest` identifica proceso, operacion y bloques. Se envia una sola vez
con `addRequest`; `pollCompleted` entrega cada resultado una vez, fuera del
semaforo del motor. El resultado tambien queda consultable en la solicitud.
Las estrategias implementan `IDiskSchedulerPolicy` y se pueden inyectar en el
constructor o con `setPolicy`, sin editar el motor.

Los JSON existentes funcionan sin el campo opcional `disco`. Para configurar
sus parametros iniciales se puede agregar este objeto a la configuracion:

```json
"disco": {
  "totalBlocks": 200,
  "initialHead": 50,
  "swapSizeBlocks": 100,
  "headSpeed": 1,
  "initialPolicy": "FIFO"
}
```

El disco admite entre 1 y 500 bloques; el cabezal usa indices desde cero y
la velocidad se expresa en posiciones por ciclo (1 hasta el total de bloques).
`swapSizeBlocks` conserva el parametro de la reserva futura; la asignacion
de bloques y el swap se implementan en sus propias tareas. Guardar esta
configuracion conserva el cabezal inicial, sin guardar el estado de una
simulacion en curso.

Para probar solo el disco: `./verificar.sh disco`. Para abrir la ventana de
prueba, ejecutar la clase `edu.unimet.avilaos.gui.TestDiskGUI` en NetBeans, o:

```sh
mvn compile exec:java -Dexec.mainClass=edu.unimet.avilaos.gui.TestDiskGUI
```

La ventana incluye ocho solicitudes de prueba, politica FIFO/SSTF, velocidad
y pausa/reanudacion. Su Timer de Swing solo refresca la vista; el reloj del
simulador realiza el trabajo. Las solicitudes de esta ventana identifican
procesos de prueba, sin crear PCBs ni ejecutar CRUD. La vista principal del
disco, SCAN, C-SCAN, archivos y suspension se completan en los issues #77–#93.
