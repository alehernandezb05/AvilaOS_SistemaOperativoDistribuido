# Escenario 4: procesos de E/S con FCFS y Round Robin

**Archivo:** `04_es_fcfs_vs_rr.json`

## Qué prueba

Los dos computadores reciben la misma carga, creada en el mismo orden en el ciclo 0: un proceso CPU bound largo y tres procesos I/O bound. Lo único que cambia es la política: C1 usa FCFS y C2 usa Round Robin con quantum 3. Se corre una sola vez y las dos filas de la pestaña Métricas se comparan en el mismo ciclo.

| Proceso | Tipo | Instrucciones | Deadline | E/S |
|---|---|---|---|---|
| Largo | CPU bound | 30 | 200 | no |
| ES1, ES2, ES3 | I/O bound | 8 | 45 | cada 2 ciclos, dura 4 |

Todos con prioridad 3 y 16 de memoria, en una RAM de 128. El deadline de 45 en los procesos de E/S es lo que hace que la política se note: con FCFS no les alcanza el tiempo.

## Cómo correrlo

Abrir el simulador, usar **Cargar configuración** con este archivo e **Iniciar**. La simulación termina en el ciclo 55 (unos 14 segundos).

## Qué pasa y cuándo

| Ciclo | Evento |
|---|---|
| 1 | Los dos computadores eligen a Largo |
| 4, 6 y 8 | En C2 (Round Robin), Largo se expropia y ES1, ES2 y ES3 ejecutan por primera vez |
| 31 | En C1 (FCFS) termina Largo; recién entonces ejecuta ES1 (ES2 en el 33 y ES3 en el 35) |
| 36, 38 y 43 | En C2 terminan ES1, ES2 y ES3 |
| 46 | En C1 vencen los tres procesos de E/S: ES2 estando en la CPU y ES1 y ES3 estando bloqueados por E/S |
| 55 | En C2 termina Largo, el último proceso |

## Resultados

Métricas leídas al ciclo 62 (la simulación terminó en el 55).

| Computador | Terminados | Completados | Throughput (por 100 ciclos) | CPU | Respuesta prom. | Cumplimiento | Equidad (Jain) |
|---|---|---|---|---|---|---|---|
| C1 FCFS | 4 | 1 | 1,61 | 72,6 % | 25,0 | 25,0 % | 0,43 |
| C2 Round Robin | 4 | 4 | 6,45 | 87,1 % | 4,8 | 100,0 % | 0,80 |
| Sistema | 8 | 5 | 8,06 | 79,8 % | 14,9 | 62,5 % | 0,54 |

Los porcentajes de CPU se leyeron siete ciclos después de terminar, por eso son más bajos de lo que habrían sido al final. En ciclos ocupados equivalen a 45 en C1 y 54 en C2.

| Proceso | Fin en C1 (FCFS) | Fin en C2 (Round Robin) |
|---|---|---|
| Largo | 31 | 55 |
| ES1 | 46, vence (había hecho 6 de 8 instrucciones) | 36 |
| ES2 | 46, vence (5 de 8) | 38 |
| ES3 | 46, vence (4 de 8) | 43 |

## Conclusión

- Con la misma carga, Round Robin cumplió los cuatro deadlines (100 %) y FCFS solo uno de cuatro (25 %). Ningún proceso de E/S alcanzó a terminar con FCFS.
- El tiempo de respuesta promedio fue de 4,8 ciclos con Round Robin contra 25,0 con FCFS: Largo, por llegar primero, ocupó la CPU 30 ciclos seguidos y los procesos de E/S empezaron a ejecutar recién en los ciclos 31, 33 y 35.
- Con Round Robin la E/S de unos procesos se solapa con el cómputo de otros. Con FCFS, la CPU gastó 15 ciclos en procesos de E/S que igual vencieron, trabajo perdido, mientras que las 54 instrucciones ejecutadas con Round Robin fueron todas útiles.
- La equidad también favorece a Round Robin (0,80 contra 0,43). Lo que paga es que Largo termina más tarde: en el ciclo 55 contra el 31.
