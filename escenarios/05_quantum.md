# Escenario 5: Round Robin con quantum 1, 3 y 10

**Archivos:** `05_quantum_q1.json`, `05_quantum_q3.json` y `05_quantum_q10.json`

## Qué prueba

La misma carga corre con Round Robin en los dos computadores, cambiando solo el quantum. Como el quantum es un parámetro global, hay un archivo por cada valor. C1 tiene solo procesos CPU bound y C2 mezcla CPU bound con I/O bound.

| Computador | Proceso | Tipo | Instrucciones | Deadline | E/S |
|---|---|---|---|---|---|
| C1 | Largo | CPU bound | 30 | 120 | — |
| C1 | Medio | CPU bound | 15 | 60 | — |
| C1 | Corto | CPU bound | 5 | 30 | — |
| C1 | Urgente | CPU bound | 10 | 25 | — |
| C2 | Calculo | CPU bound | 25 | 120 | — |
| C2 | Interactivo | I/O bound | 10 | 60 | cada 2 ciclos, dura 3 |
| C2 | Consulta | I/O bound | 10 | 60 | cada 2 ciclos, dura 3 |
| C2 | Script | CPU bound | 8 | 40 | — |

Todos tienen 16 de memoria y prioridad 3, en una RAM de 128.

## Cómo correrlo

Abrir el simulador, usar **Cargar configuración** con uno de los tres archivos e **Iniciar**. Repetir con los otros dos. Las simulaciones terminan en el ciclo 57 (quantum 1 y 3) y 56 (quantum 10).

También se puede cargar el de quantum 3 y cambiar el quantum en tiempo de ejecución desde los controles, pero así los resultados no coinciden con las tablas.

## Resultados

Orden en que terminan los procesos (ciclo de fin):

| Quantum | C1 | C2 |
|---|---|---|
| 1 | Corto (20), Urgente (26, **vence**), Medio (42), Largo (57) | Script (29), Interactivo (40), Consulta (43), Calculo (54) |
| 3 | Corto (21), Urgente (26, **vence**), Medio (42), Largo (57) | Script (30), Interactivo (42), Consulta (44), Calculo (54) |
| 10 | Corto (26), Urgente (26, **vence**), Medio (41), Largo (51) | Script (23), Calculo (42), Interactivo (54), Consulta (56) |

| Quantum | Computador | Throughput (por 100 ciclos) | CPU | Respuesta prom. | Cumplimiento | Equidad (Jain) |
|---|---|---|---|---|---|---|
| 1 | C1 | 5,26 | 98,2 % | 2,5 | 75,0 % | 0,89 |
| 1 | C2 | 7,02 | 93,0 % | 2,5 | 100,0 % | 0,92 |
| 1 | Sistema | 12,28 | 95,6 % | 2,5 | 87,5 % | 0,90 |
| 3 | C1 | 5,26 | 98,2 % | 5,5 | 75,0 % | 0,89 |
| 3 | C2 | 7,02 | 93,0 % | 4,8 | 100,0 % | 0,91 |
| 3 | Sistema | 12,28 | 95,6 % | 5,1 | 87,5 % | 0,89 |
| 10 | C1 | 5,36 | 89,3 % | 11,0 | 75,0 % | 0,64 |
| 10 | C2 | 7,14 | 94,6 % | 10,0 | 100,0 % | 0,79 |
| 10 | Sistema | 12,50 | 92,0 % | 10,4 | 87,5 % | 0,71 |

## Conclusión

- Cuanto más chico el quantum, mejor el tiempo de respuesta (2,5 ciclos con quantum 1 contra 10,4 con quantum 10) y la equidad (0,90 contra 0,71): todos los procesos reciben la CPU pronto y avanzan a un ritmo parecido.
- Con quantum 10, Round Robin se parece a FCFS: Largo corre 10 ciclos seguidos, Urgente no llega a ejecutarse antes de vencer, y en C2 los I/O bound quedan detrás de Calculo y terminan últimos.
- El throughput y el cumplimiento casi no cambian porque ÁvilaOS no cobra el cambio de contexto. En un sistema real, un quantum de 1 gastaría buena parte de la CPU en cambios de contexto, así que lo razonable es un valor intermedio como 3.
