# Proyecto 2 — Validación de políticas de disco (#94)

## Reproducción

En NetBeans, con JDK 25, abrir `Test Packages/edu.unimet.avilaos/PruebaCasosOficiales.java` y ejecutar `Run File` (Shift+F6). Desde el directorio `AvilaOS`, también se puede usar `mvn test-compile exec:java -Dexec.mainClass=edu.unimet.avilaos.PruebaCasosOficiales -Dexec.classpathScope=test`.

La prueba ejecutable utiliza el `DiskScheduler`, el núcleo y la CPU existentes, sin bibliotecas adicionales. Es una prueba manual que genera evidencia en `escenarios/12_resultados_disco/` (puede sobrescribir resultados de una ejecución anterior). No modifica los dos JSON oficiales. No es un benchmark de tiempo de pared.

### Experimento A: lote de solicitudes

- Disco de 200 bloques (0–199), cabezal inicial 50, dirección ascendente, velocidad 1 bloque/ciclo, sin swap.
- Todas las solicitudes se encolan en el ciclo 0 antes de avanzar el reloj. Cada solicitud es una lectura de un bloque. Se avanza desde el ciclo 1 hasta completar el lote; se mide el motor de disco, no los procesos CPU.
- P1–P4: 95, 180, 34, 119, 11, 123, 62, 64. Anexo: 82, 17, 43, 140, 24, 16, 190. Las cuatro políticas reciben los mismos datos iniciales de cada lote.
- Se verifica el orden de atención, desplazamiento acumulado, número de solicitudes, ciclos de llegada/selección/inicio/fin, promedio de espera, extremos y retorno C-SCAN. El valor esperado nunca se usa como medición: se compara con los datos producidos por el motor.
- Espera = ciclo de selección/inicio de atención menos ciclo de llegada; se promedia solo sobre solicitudes completadas. El inicio incluye la búsqueda del bloque. Cada transferencia ocupa un ciclo después de llegar al destino. C-SCAN recorre el retorno a velocidad configurada, contabiliza sus ciclos pero no sus bloques en el desplazamiento exigido.

### Experimento B: simulación completa

- Archivos originales `08_sistema_archivos.json` y `09_procesos_archivos.json` del enunciado: RAM 1024, CPU FCFS, quantum 0, swap 100, disco 200, cabezal 50, velocidad 1, ocho procesos. Se carga de nuevo el estado inicial para cada política y se cambia únicamente la política de disco.
- Para reproducibilidad se avanza manualmente el ciclo global llamando disco, núcleo y CPU en ese orden (el mismo orden de registro del reloj). Esta prueba no sustituye las pruebas concurrentes del #93 ni la ejecución gráfica.
- Los procesos generan llegadas escalonadas; las operaciones no son equivalentes al lote. El JSON original trae dos UPDATE sin `newName` ni solicitante administrador y un DELETE sin solicitante autorizado sobre un archivo privado. Se conserva la validación de permisos y campos: no se inventan datos para hacer pasar esas solicitudes. Se esperan cinco lecturas completadas y tres errores de E/S, sin deadlines vencidos en esta configuración. No se compara su orden con el del lote.
- Se registran terminaciones, ciclo de finalización y log del núcleo, además del recorrido y solicitudes del disco.

### Casos de borde

Cola vacía, cabezal inicialmente en 199, solicitud en el propio cabezal, salida desde el extremo, empate SSTF por orden de llegada y cambio de política durante una solicitud activa. La solicitud activa conserva la política que la seleccionó. Las pruebas previas `PruebaDisco` y `PruebaMetricasDisco` complementan esta validación.

### Evidencias

La ejecución crea `resumen.csv` (comparación de las 12 ejecuciones), CSV de recorrido por ciclo con posición y desplazamiento acumulado, CSV de solicitudes con espera y tiempos, CSV de procesos y log para las ejecuciones completas, y tres gráficas SVG (`P1-P4_comparacion.svg`, `Anexo_comparacion.svg`, `Completo_comparacion.svg`). Las gráficas usan ciclos observados, incluyendo el estado inicial real en ciclo 0, y cada serie termina en el ciclo final indicado en su leyenda. Los archivos SVG se pueden abrir con el navegador. Para el informe, conservar la captura del Output con BUILD SUCCESS y las gráficas, y al menos una captura del simulador con ciclo y métricas visibles.

En la interfaz, cargar los dos JSON originales, seleccionar la política antes de iniciar, fijar velocidad 1 para comparar con este experimento y pausar después de terminar los ocho procesos. No cargar dos veces el archivo de procesos en la misma ejecución. Las métricas de espera y movimiento permanecen estables cuando la cola queda vacía; el ciclo visible puede ser posterior al último ciclo de trabajo.
