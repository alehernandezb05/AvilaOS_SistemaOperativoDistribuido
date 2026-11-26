# Validación y demostración de entrega (Issue #95)

## Entorno y reproducción desde cero

1. Clonar el repositorio del equipo y seleccionar la rama de entrega acordada. Registrar `git rev-parse --short HEAD` y `git status -sb`.
2. JDK 25, Apache NetBeans 31 (versiones acordadas en README). En Tools > Java Platforms comprobar el JDK; Help > About muestra la versión del IDE. Registrar las versiones efectivas; el Output de PruebaEntrega imprime `java.version`.
3. File > Open Project, seleccionar la carpeta `AvilaOS` (contiene pom.xml). Clean and Build. Maven resuelve Gson 2.11.0 y JFreeChart 1.5.5; no se requieren bibliotecas manuales. No cambiar el pom ni el JDK para omitir errores.
4. En Test Packages > edu.unimet.avilaos > PruebaEntrega.java, Run File (Shift+F6). Ejecutar desde AvilaOS, como hace NetBeans. Debe mostrar 7/7 grupos aprobados y BUILD SUCCESS; si falla, registrar el primer mensaje y no afirmar que pasó. `mvn test` por sí solo no ejecuta estos programas main.
5. Los resultados de la nueva ejecución de los casos de disco quedan en `AvilaOS/target/entrega/resultados_disco`, sin sustituir la evidencia del #94. Conservar capturas y copiar resultados si hacen falta para el informe.

## Cobertura de la prueba conjunta

| Grupo | Comprobación |
|---|---|
| 1 | Ambos JSON obligatorios; errores de tipo, rango y ruta con reloj activo. Valida cargadores y modelo; la interacción visual se comprueba aparte. |
| 2 | CPU, cuatro políticas, quantum, PC/MAR, memoria, deadlines, E/S temporal, configuración y JSON de un computador. |
| 3 | Disco, cadenas, CRUD, permisos propios/públicos/ajenos, eliminación recursiva, falta de víctima/swap, suspensión y reactivación, guardado/recarga. Ejecuta una segunda carga de CPU/RAM sobre disco fragmentado modificado. |
| 4 | Vencimiento durante swap-out, swap-in, cola y estados suspendidos; cancelación y liberación. |
| 5 | Métricas del disco y CPU, historial y contadores de intercambios. |
| 6 | Reloj, pausa, interrupción, CRUD concurrente, cambios de política y observación. |
| 7 | Casos P1–P4, anexo, bordes y simulación completa con JSON oficiales. |

El código de producción usa las estructuras propias Cola, Lista y Nodo, arreglos y semáforos. Revisión estática de imports de java.util del ZIP base: Arrays, funciones y semáforos; no se encontraron imports de colecciones Java en producción. Revisar también referencias calificadas; no es una prueba formal de ausencia de colecciones. Gson y JFreeChart son las únicas dependencias directas declaradas en el pom. Las pruebas agregadas usan Java estándar y las clases existentes.

## Demostración en NetBeans (tres integrantes; propuesta de reparto)

**Integrante 1: disco y casos oficiales.** Run Project, abrir simulador sin cargar procesos. Mostrar Iniciar deshabilitado. Cargar únicamente 08_sistema_archivos.json: todavía debe estar deshabilitado. Cargar 09_procesos_archivos.json: debe habilitarse. Iniciar; pestaña Disco; mostrar cabezal, mapa, cola, política y gráfica acumulada. Cambiar política durante ejecución. Explicar la diferencia entre lote y llegadas escalonadas con 12_validacion_politicas.md y sus CSV. No confundir el retorno C-SCAN (tiempo) con desplazamiento contabilizado.

**Integrante 2: archivos y permisos.** Con ambos JSON cargados, en administrador, seleccionar /usuarios en el árbol, crear un directorio demo95 y luego un archivo dentro, esperar su aparición después de la E/S; leer, renombrar y eliminar el directorio con su contenido. Comprobar árbol, tabla, mapa y log después de cada finalización. En modo usuario user2, intentar leer /usuarios/notas.txt (privado de user1): debe rechazarse. Leer /usuarios/data.csv (propio/público) y /sistema/boot.bin (público ajeno): permitido. En user1 leer notas.txt (propio privado): permitido. Volver a administrador. Intentar cargar un JSON inválido (copia con un bloque fuera de rango), comprobar el aviso y que la carga válida permanece. Guardar en archivo nuevo fuera del repositorio; no sobrescribir 08 ni 09. La prueba conjunta ya comprueba persistencia de una cadena fragmentada con una segunda carga efectiva; para mostrarla también en GUI, crear un archivo de varios bloques que cruce huecos, guardar, recargar y verificar los mismos bloques. Si se usa una cadena contigua no se debe afirmar que se probó fragmentación visualmente.

**Integrante 3: swap, CPU y sincronización.** Reiniciar; cargar 10_metricas_suspension.json y 08_sistema_archivos.json. RAM 32, FCFS, ciclo 100 ms, velocidad 10; mostrar colas suspendidas, mapa de swap, contadores y log. Esperar la reactivación (contadores 1 y 1), pausar. Explicar el orden de semáforos y la captura entre ciclos con 11_sincronizacion.md. Mostrar la prueba de cancelación/deadlines del grupo 4 y los casos de falta de víctima/swap del grupo 3. Para regresión CPU/RAM cargar 07_un_computador.json con el sistema de archivos: dos procesos completan y NoCabe vence. No utilizar las cifras históricas de P1 como mediciones nuevas.

## Registro de evidencias y fallos

- Captura del Output final de PruebaEntrega y de su inicio (versión Java).
- Captura de inicio bloqueado con un JSON y de operación normal con ambos.
- Captura del CRUD/permiso, log y actualización de vistas; y de las métricas de suspensión/reactivación con ciclo visible. Reutilizar evidencia #90–#94 cuando corresponda.
- Registrar versión de NetBeans, Java, commit, archivos cargados, resultado observado y responsable de la demostración. Este documento es una guía, no una certificación de que se ejecutaron todos los pasos visuales.
- Si un criterio falla, abrir un issue de tipo bug con entrada, pasos, resultado esperado, observado, versión y evidencia; no cerrar la validación hasta registrar y gestionar el fallo según el equipo.
