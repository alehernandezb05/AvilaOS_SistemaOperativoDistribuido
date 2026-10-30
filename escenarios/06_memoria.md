# Escenario 6: admisión por memoria con RAM chica

**Archivo:** `06_memoria.json`

## Qué prueba

Dos computadores con solo 64 de RAM y FCFS, para ver la planificación a largo plazo: procesos que esperan en la cola de nuevos, que entran cuando otro libera memoria y que vencen sin haber sido admitidos. En C2, además, un buffer ocupa RAM del anfitrión y deja sin espacio a un proceso.

| Computador | Proceso | Tipo | Memoria | Instrucciones | Deadline |
|---|---|---|---|---|---|
| C1 | A | CPU bound | 32 | 10 | 100 |
| C1 | B | CPU bound | 24 | 15 | 100 |
| C1 | C | CPU bound | 40 | 8 | 100 |
| C1 | D | CPU bound | 48 | 5 | 15 |
| C1 | E | CPU bound | 80 | 5 | 50 |
| C2 | Productor | Productor de B1 | 16 | 6 elementos, 2 ciclos c/u | 200 |
| C2 | Consumidor | Consumidor de B1 | 16 | 6 elementos, 2 ciclos c/u | 200 |
| C2 | Tarea | CPU bound | 24 | 6 | 200 |

El buffer B1 tiene capacidad 16 y vive en C2, así que ocupa 16 de su RAM desde el inicio.

## Cómo correrlo

Abrir el simulador, usar **Cargar configuración** con este archivo e **Iniciar**. La simulación termina en el ciclo 91.

## Qué pasa y cuándo

| Ciclo | Evento |
|---|---|
| 1 | C1 admite a A y B (32 + 24 = 56 de 64). C, D y E se quedan en Nuevos |
| 1 | C2 admite a Productor y Consumidor (16 + 16 + 16 del buffer = 48 de 64). Tarea (24) no cabe |
| 11 | Termina A y libera 32: C1 admite a C (40), que llegó antes que D |
| 15 | D vence en Nuevos sin haber sido admitido: con C adentro solo quedan 0 libres |
| 26 | Termina B |
| 34 | Termina C y C1 queda vacío, pero E sigue sin caber: necesita 80 y la RAM total es 64 |
| 43 | Termina Productor y libera 16: C2 admite a Tarea |
| 50 | E vence en Nuevos |
| 85 | Termina Consumidor |
| 91 | Termina Tarea, el último proceso |

## Resultados

| Computador | Terminados | Completados | Throughput (por 100 ciclos) | CPU | Respuesta prom. | Cumplimiento | Equidad (Jain) |
|---|---|---|---|---|---|---|---|
| C1 FCFS | 5 | 3 | 3,30 | 36,3 % | 12,7 | 60,0 % | 0,49 |
| C2 FCFS | 3 | 3 | 3,30 | 98,9 % | 43,0 | 100,0 % | 0,65 |
| Sistema | 8 | 6 | 6,59 | 67,6 % | 27,8 | 75,0 % | 0,55 |

| Buffer | Producidos | Consumidos | Bloqueo promedio en semáforos |
|---|---|---|---|
| B1 | 6 | 6 | 0,0 ciclos |

Nota: la utilización de C1 es baja porque queda ocioso desde el ciclo 34 (E nunca puede entrar) mientras la simulación sigue hasta el 91 por C2. B1 no bloquea a nadie porque su capacidad (16) es mayor que los 6 elementos.

## Conclusión

- La admisión depende solo de la memoria libre, no de la política: un proceso espera en Nuevos aunque la CPU esté libre, y entra en cuanto otro termina y libera suficiente RAM.
- Si un proceso pide más memoria que la RAM total (E), nunca se admite y vence en Nuevos. Al no haber reservado memoria, no libera nada al terminar.
- Los buffers compiten por la RAM con los procesos: sin B1, Tarea habría entrado en el ciclo 1 (16 + 16 + 24 = 56). Con B1 tuvo que esperar a que terminara el Productor.
