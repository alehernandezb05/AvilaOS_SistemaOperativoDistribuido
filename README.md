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

Basta con `startBlock` y `blocks`, como en el enunciado: sin más datos, la
cadena ocupa `blocks` posiciones consecutivas desde `startBlock`. Una cadena
fragmentada se indica con `blockChain`, un arreglo ordenado con todas las
posiciones que empieza en `startBlock` y tiene `blocks` elementos.

Para crear una lectura, selecciona un archivo y pulsa **Crear proceso READ…**.
La primera instrucción consume un ciclo de CPU en modo sistema operativo.
Después el proceso espera a que el disco termine la cadena y vuelve a Listo.

El JSON de procesos asigna el computador 1 y el tipo `IO_BOUND`. Si se omiten,
`priority` vale 1, `quantum` vale 3, `headSpeed` vale 1 y `fileSizeBlocks`
vale 0. `requester` es la identidad del usuario dentro de la simulación.
Solo el administrador carga este archivo, así que si se omite el proceso actúa
como `admin`. Con otro `requester`, para leer un archivo privado el solicitante
debe coincidir con su dueño.
El ejemplo atiende sus ocho solicitudes: cinco lecturas, dos escrituras (`UPDATE`
sin `newName`) y un borrado. Con FIFO y velocidad 1 el cabezal recorre tanto que
tres procesos vencen el deadline de 500 ciclos; con SSTF, SCAN y C-SCAN terminan
los ocho.

Selecciona un directorio y pulsa **Crear archivo o directorio…** para encolar
un proceso CREATE. El árbol, la tabla y el mapa muestran el cambio al terminar
la E/S. Se registra el PCB creador y la entrada nace privada. En esta simulación
`admin` puede crear en cualquier directorio. Los demás solicitantes solo
pueden crear archivos en sus propios directorios.

`UPDATE` sin `newName` escribe sobre el archivo: el disco recorre su cadena
igual que en una lectura y la estructura no cambia. Lo pueden pedir `admin` o
el dueño del archivo.

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
En el JSON, `newName` (solo el nombre, sin `/`) convierte el UPDATE en un
renombrado.

En el JSON, CREATE usa `fileSizeBlocks` para elegir qué crear. Un tamaño positivo indica un archivo
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

## Suspension y planificador a mediano plazo

Los procesos usan siete estados: Nuevo, Listo, Ejecucion, Bloqueado,
Listo/Suspendido, Bloqueado/Suspendido y Terminado. Cuando un Nuevo no cabe en
la RAM, el nucleo suspende Bloqueados hasta cubrir la memoria que piden los
Nuevos en espera, contando la que ya se esta escribiendo al swap.

- **Victima:** el Bloqueado (por E/S o por disco) que mas ciclos lleva en su
  bloqueo actual; en empate, el de menor ID.
- **Escritura:** se reservan tantos bloques de swap como unidades de RAM tiene
  el proceso y se encola una solicitud SUSPEND en la misma cola del disco. El
  sistema operativo la emite directamente, sin dar CPU al proceso. La RAM se
  libera una sola vez, cuando la escritura termina.
- **Destino:** si el evento que esperaba ocurre durante la escritura, al
  terminarla queda Listo/Suspendido; si no, Bloqueado/Suspendido, y pasa a
  Listo/Suspendido cuando ocurra el evento.
- **Sin victima o sin swap:** el Nuevo sigue esperando y el registro lo explica
  una vez, sin reservar nada.
- **Reactivacion:** cada ciclo se revisa la cola Listo/Suspendido en orden de
  llegada. Si hay RAM, se reserva antes de empezar y se encola una lectura
  REACTIVATE de sus bloques de swap en la misma cola del disco. El proceso sigue
  Listo/Suspendido hasta que la lectura termina; entonces libera el swap y pasa a
  Listo con su PC, instrucciones, prioridad y deadline intactos. Un
  Bloqueado/Suspendido no se reactiva hasta que ocurra su evento.
- **Prioridad:** los Nuevos van primero, porque son los unicos que provocan
  suspensiones. Una reactivacion solo usa RAM que los Nuevos en espera no
  necesitan, contando la que ya se esta liberando. Asi reactivar nunca obliga a
  suspender a otro proceso y no se forman ciclos de suspension y reactivacion.

## Modos de uso y permisos

El modo de uso (`ModoUsuario`: administrador o usuario) es distinto del modo de
la CPU (`ModoEjecucion`). Se elige en la barra superior; en modo usuario se
escribe el nombre del usuario (por ejemplo `user1`) y se aplica con Enter.

| Accion | Administrador | Usuario |
|---|---|---|
| Leer archivos | Todos, incluso privados ajenos | Propios o publicos |
| Crear archivos | En cualquier directorio | Solo en directorios propios |
| Crear directorios, renombrar | Si | No |
| Eliminar | Archivos y directorios | Solo archivos propios |
| Politicas, quantum, velocidad del disco | Si | No |
| Crear procesos de CPU, cargar o guardar JSON | Si | No |

Cada operacion de archivo guarda su solicitante (`admin` o el nombre del
usuario), tomado del modo actual y no de un campo editable. El simulador valida
los permisos antes de crear el proceso y el sistema de archivos los vuelve a
validar al ejecutarlo, asi que no se pueden eludir desde la interfaz ni con un
cambio de modo posterior. Un rechazo muestra su motivo, queda en el registro,
no crea el proceso y la simulacion sigue. Los procesos del JSON sin `requester`
actuan como `admin`, porque solo el administrador puede cargar ese archivo.

## Guardar y recargar el sistema de archivos

**Guardar sistema de archivos…** (pestaña Sistema de archivos, solo administrador)
escribe el estado actual: jerarquía, dueños, permisos y la cadena exacta de
bloques de cada archivo. Las operaciones que el disco todavía no terminó no se
incluyen. El formato es el mismo del ejemplo, con un campo opcional para las
cadenas de más de un bloque:

```json
{"path": "/usuarios/datos.bin", "type": "FILE", "startBlock": 106, "blocks": 3,
 "blockChain": [106, 107, 120], "owner": "admin", "permissions": "private"}
```

`blockChain` debe empezar en `startBlock` y tener `blocks` posiciones; sin él,
la cadena son los bloques consecutivos desde `startBlock`. Al cargar se rechazan
bloques repetidos (ciclos o doble asignación), posiciones fuera del disco,
tamaños que no coinciden y padres inexistentes. Si los archivos no dejan
espacio para el swap, la simulación no se crea y se explica el motivo.

El guardado escribe primero un archivo temporal, lo vuelve a leer con las mismas
validaciones y solo entonces reemplaza al destino, así un fallo no deja un JSON a
medias ni toca el archivo anterior. Para reiniciar con otros procesos sobre el
disco guardado: cargar ese JSON y luego **Cargar procesos…**. Los procesos en
ejecución o suspendidos no se guardan.

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

Para probar solo el disco: `./verificar.sh disco`.

La pestaña **Disco** de la ventana principal muestra el mapa de bloques
(libres, archivos, swap disponible y ocupado), el cabezal con su dirección,
el bloque destino de la solicitud activa y la cola de E/S, con la solicitud
que se atiende primero y las pendientes en orden de llegada. Desde ahí se
cambia la política (FIFO, SSTF, SCAN, C-SCAN) y la velocidad del cabezal
durante la ejecución: la solicitud activa termina con la política que la
eligió y la nueva política elige la siguiente. La pausa y la duración del
ciclo son las mismas del reloj global; el Timer de Swing solo refresca la
vista con una copia del estado tomada en un mismo ciclo.
