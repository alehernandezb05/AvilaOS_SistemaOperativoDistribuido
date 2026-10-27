# Escenario 2: demo completa del sistema

**Archivo:** `02_demo_completa.json`

## Qué prueba

Un clúster de cuatro computadores, cada uno con una política distinta, donde a la vez se ven la planificación, la admisión por memoria, la E/S, los deadlines y el productor-consumidor con acceso local y remoto. El ciclo dura 250 ms, el quantum es 3 y la latencia de red es 3 ciclos.

| Computador | Política | RAM | Procesos | Qué muestra |
|---|---|---|---|---|
| C1 | FCFS | 128 | Descargador (productor de B1), Compilador | Acceso local al buffer y que FCFS no expropia |
| C2 | Round Robin | 128 | Reproductor (consumidor de B1), Editor, Navegador | Acceso remoto con latencia y turnos de la CPU |
| C3 | EDF | 128 | Render, Videollamada, Backup | Cola ordenada por deadline y un proceso que vence |
| C4 | Prioridades | 64 | Sistema, Antivirus (I/O bound), Juego, BaseDatos | Expropiación por prioridad, bloqueo por E/S y un proceso que no cabe en RAM |

El buffer B1 tiene capacidad 3 y vive en C1. El productor y el consumidor procesan 8 elementos cada uno.

## Cómo correrlo

Abrir el simulador, usar **Cargar configuración** con este archivo e **Iniciar**. La simulación termina en el ciclo 121 (unos 30 segundos).

## Qué pasa y cuándo

| Ciclo | Evento |
|---|---|
| 1 | EDF elige a Render (deadline 30, el más cercano) y Prioridades elige a Sistema (prioridad 2) |
| 2 | El Reproductor de C2 accede a B1 de forma remota y espera 3 ciclos de red |
| 5 en adelante | Round Robin expropia cada 3 ciclos en C2 |
| 12 | El Descargador se bloquea en `B1.mutex`, que tiene el Reproductor; al desbloquearse en el 18, FCFS no le devuelve la CPU hasta que termina el Compilador (ciclo 37) |
| 14 en adelante | Antivirus se bloquea por E/S cada 3 ciclos; al volver expropia a Juego (ciclos 18, 25, 32 y 39) |
| 20 | BaseDatos vence en Nuevos sin haber sido admitido (necesita 100 de RAM y C4 tiene 64) |
| 31 | Render vence y EDF pasa a Videollamada |
| 36 | El Reproductor se bloquea en `B1.llenos` (buffer vacío) |
| 78 | El Descargador se bloquea en `B1.vacios` (buffer lleno, 3/3) |
| 121 | Termina el Reproductor, el último proceso |

## Resultados

| Computador | Terminados | Completados | Throughput (por 100 ciclos) | CPU | Respuesta prom. | Cumplimiento | Equidad (Jain) |
|---|---|---|---|---|---|---|---|
| C1 FCFS | 2 | 2 | 1,65 | 66,9 % | 6,5 | 100,0 % | 1,00 |
| C2 Round Robin | 3 | 3 | 2,48 | 79,3 % | 2,7 | 100,0 % | 0,94 |
| C3 EDF | 3 | 2 | 1,65 | 47,9 % | 23,7 | 66,7 % | 0,70 |
| C4 Prioridades | 4 | 3 | 2,48 | 45,5 % | 8,7 | 75,0 % | 0,65 |
| Sistema | 12 | 10 | 8,26 | 59,9 % | 10,7 | 83,3 % | 0,77 |

| Buffer | Producidos | Consumidos | Bloqueo promedio en semáforos |
|---|---|---|---|
| B1 | 8 | 8 | 7,0 ciclos |

Al final, los semáforos de B1 vuelven a sus valores iniciales: `vacios` = 3, `llenos` = 0 y `mutex` = 1.

## Conclusión

- El productor-consumidor funciona entre computadores: se producen y consumen los 8 elementos, nunca se inserta en un buffer lleno ni se extrae de uno vacío, y los procesos se bloquean y despiertan en los tres semáforos.
- El acceso remoto tiene un costo visible: el Reproductor (remoto, en C2) termina en el ciclo 121, mucho después que el Descargador (local, en C1) en el 87, porque paga 3 ciclos de red por cada elemento.
- EDF atiende primero a Render por tener el deadline más cercano, aunque no le alcanza el plazo para sus 40 instrucciones. Elegir siempre al más urgente no garantiza que ese proceso termine.
- La admisión por memoria es independiente de la política: BaseDatos nunca entra a Listos porque no cabe en la RAM de C4, y vence en Nuevos sin liberar memoria.
