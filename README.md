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
- Carga `escenarios/09_procesos_archivos.json` con **Cargar procesos…**, en el
  diálogo inicial o desde la ventana principal. Para habilitar **Iniciar**,
  carga también `escenarios/08_sistema_archivos.json` desde la pestaña
  **Sistema de archivos**. Los dos archivos pueden cargarse en cualquier orden
  desde la ventana principal.
- Para una carga de CPU y E/S temporal del Proyecto 1, usa
  `escenarios/07_un_computador.json` como archivo de procesos.

Para explorar el ejemplo del enunciado, abre la pestaña **Sistema de archivos**
y carga `escenarios/08_sistema_archivos.json`. La pestaña muestra la jerarquía,
los datos de la entrada seleccionada y la tabla de asignación. El JSON aporta
el tamaño del disco y el cabezal inicial. La configuración del computador
aporta el tamaño de swap, que debe caber junto con los archivos.

Para archivos de un bloque basta con `startBlock` y `blocks`. Los archivos
de varios bloques incluyen también `blockChain`, un arreglo ordenado con
todas las posiciones que empieza en `startBlock` y tiene `blocks` elementos.

Para crear una lectura, selecciona un archivo y pulsa **Crear proceso READ…**.
La primera instrucción consume un ciclo de CPU en modo sistema operativo.
Después el proceso espera a que el disco termine la cadena y vuelve a Listo.

El JSON de procesos asigna el computador 1 y el tipo `IO_BOUND`. Si se omiten,
`priority` vale 1, `quantum` vale 3, `headSpeed` vale 1 y `fileSizeBlocks`
vale 0. `requester` es la identidad del usuario dentro de la simulación.
Si se omite, la lectura se limita a archivos públicos. Para leer un archivo
privado, el solicitante debe coincidir con su dueño.
El ejemplo ejecuta cinco lecturas y registra tres errores de E/S: los dos
`UPDATE` no traen `newName` ni `requester` y el `DELETE` no trae `requester`.

Selecciona un directorio y pulsa **Crear archivo o directorio…** para encolar
un proceso CREATE. El árbol, la tabla y el mapa muestran el cambio al terminar
la E/S. Se registra el PCB creador y la entrada nace privada. En esta simulación
`admin` puede crear en cualquier directorio. Los demás solicitantes solo
pueden crear archivos en sus propios directorios.

Selecciona una entrada y pulsa **Renombrar…** o **Eliminar…** para encolar un
proceso UPDATE o DELETE. La interfaz solo crea el proceso: el árbol, la tabla y
el mapa cambian cuando el disco termina la solicitud. Renombrar usa un ciclo de
metadatos, solo lo puede pedir `admin` y, si es un directorio, actualiza la ruta
de todos sus descendientes sin tocar sus cadenas de bloques. Eliminar lo pueden
pedir `admin` o el dueño de un archivo; el disco recorre los bloques de cada
archivo y al terminar los libera. Eliminar un directorio quita todo su
contenido con una sola solicitud.

Se rechazan, con el motivo en el log y el proceso en Terminado por error de E/S:
un nombre destino ausente, igual al actual o que ya existe, un solicitante sin
permiso, una entrada que no existe o ya fue eliminada, la raíz y una entrada
que tiene otra operación pendiente (también sus ancestros y descendientes).
Un UPDATE o DELETE cuyo proceso vence antes de que el disco termine no se aplica.
En el JSON, UPDATE requiere `newName` (solo el nombre, sin `/`) y `requester`;
DELETE requiere `requester`.

En el JSON, CREATE requiere `requester` para autorizar la ejecución y
`fileSizeBlocks` para elegir qué crear. Un tamaño positivo indica un archivo
y 0 indica un directorio. El directorio usa un ciclo de metadatos en el disco
y no ocupa bloques.

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
