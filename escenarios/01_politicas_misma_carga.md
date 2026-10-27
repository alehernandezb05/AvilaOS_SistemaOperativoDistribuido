# Escenario 1: las cuatro políticas con la misma carga

**Archivo:** `01_politicas_misma_carga.json`

## Qué prueba

Los cuatro computadores reciben exactamente los mismos cinco procesos CPU bound, creados en el mismo orden en el ciclo 0. Lo único que cambia es la política de cada computador, así que las diferencias en las métricas se deben solo a la planificación.

| Computador | Política |
|---|---|
| C1 | FCFS |
| C2 | Round Robin (quantum 3) |
| C3 | EDF |
| C4 | Prioridades apropiativas |

Carga de cada computador (todos con 16 de memoria, en una RAM de 128):

| Proceso | Instrucciones | Prioridad | Deadline |
|---|---|---|---|
| Largo | 30 | 5 | 120 |
| Medio | 15 | 3 | 60 |
| Corto | 5 | 4 | 30 |
| Urgente | 10 | 2 | 25 |
| Importante | 12 | 1 | 100 |

La carga está pensada para que cada política elija primero a un proceso distinto: FCFS a Largo (llegó primero), EDF a Urgente (deadline más cercano), Prioridades a Importante (prioridad 1) y Round Robin los turna a todos.

## Cómo correrlo

Abrir el simulador, usar **Cargar configuración** con este archivo e **Iniciar**. La simulación termina en el ciclo 73.

## Resultados

Orden en que terminan los procesos (ciclo de fin):

| Política | Orden de terminación |
|---|---|
| FCFS | Urgente (26, **vence**), Largo (31), Corto (31, **vence**), Medio (46), Importante (58) |
| Round Robin | Corto (24), Urgente (26, **vence**), Importante (47), Medio (53), Largo (68) |
| EDF | Urgente (11), Corto (16), Medio (31), Importante (43), Largo (73) |
| Prioridades | Importante (13), Urgente (23), Corto (31, **vence**), Medio (38), Largo (68) |

Métricas al final de la simulación (pestaña Métricas, ciclo 73):

| Computador | Terminados | Completados | Throughput (por 100 ciclos) | CPU | Respuesta prom. | Cumplimiento | Equidad (Jain) |
|---|---|---|---|---|---|---|---|
| C1 FCFS | 5 | 3 | 4,11 | 78,1 % | 26,0 | 60,0 % | 0,41 |
| C2 Round Robin | 5 | 4 | 5,48 | 91,8 % | 7,0 | 80,0 % | 0,91 |
| C3 EDF | 5 | 5 | 6,85 | 98,6 % | 20,4 | 100,0 % | 0,82 |
| C4 Prioridades | 5 | 4 | 5,48 | 91,8 % | 18,8 | 80,0 % | 0,69 |
| Sistema | 20 | 16 | 21,92 | 90,1 % | 17,1 | 80,0 % | 0,65 |

## Conclusión

- **EDF** es la única política que cumple todos los deadlines (100 %), porque siempre atiende primero al proceso cuyo plazo vence antes.
- **Round Robin** tiene el mejor tiempo de respuesta (7 ciclos) y la mejor equidad (0,91): todos los procesos empiezan pronto y se reparten la CPU. A cambio, el Urgente vence porque tiene que esperar su turno como los demás.
- **Prioridades** atiende primero a los importantes, pero deja vencer a Corto (prioridad 4), que habría terminado a tiempo con otra política.
- **FCFS** es la peor en esta carga: Largo, por llegar primero, ocupa la CPU 30 ciclos seguidos y hace vencer a Urgente y a Corto. Tiene el peor cumplimiento (60 %), la peor respuesta (26 ciclos) y la peor equidad (0,41).
