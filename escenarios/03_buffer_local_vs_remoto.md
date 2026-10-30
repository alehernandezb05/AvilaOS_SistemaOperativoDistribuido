# Escenario 3: Productor-Consumidor Local vs. Remoto (Variación de Latencia)

**Archivos:** `03_buffer_local_vs_remoto.json` (latencia 3), `03_buffer_local_vs_remoto_lat0.json` y `03_buffer_local_vs_remoto_lat10.json`

## Qué prueba
Compara la ejecución de un par Productor-Consumidor local (ambos en C1 ejecutándose en la misma CPU) contra un par remoto (Productor en C2 y Consumidor en C3 mediante acceso a semáforos remotos). Se evalúa el comportamiento variando la latencia de red en 0, 3 y 10 ciclos. Los tres archivos son iguales salvo por la latencia, que es un parámetro global del clúster.

Los tres computadores usan FCFS y tienen 128 de RAM. Los dos buffers tienen capacidad 3 y viven en C1.

| Par | Productor | Consumidor | Ubicación |
|---|---|---|---|
| Local | ProductorLocal (10 elementos, 2 ciclos c/u) | ConsumidorLocal (10 elementos, 3 ciclos c/u) | C1 (Buffer B1 local) |
| Remoto | ProductorRemoto (10 elementos, 2 ciclos c/u) | ConsumidorRemoto (10 elementos, 3 ciclos c/u) | C2 y C3 (Buffer B2 remoto, en C1) |

Ojo: el par local comparte una sola CPU, mientras que el par remoto tiene una CPU para cada proceso. Por eso la comparación mide a la vez el costo de la red y el beneficio de repartir el trabajo en dos CPUs.

## Cómo correrlo

Abrir el simulador, usar **Cargar configuración** con uno de los tres archivos e **Iniciar**. Repetir con los otros dos. La latencia no se puede cambiar en tiempo de ejecución, por eso hay un archivo por valor.

| Archivo | Latencia | La simulación termina en el ciclo |
|---|---|---|
| `03_buffer_local_vs_remoto_lat0.json` | 0 | 151 |
| `03_buffer_local_vs_remoto.json` | 3 | 151 |
| `03_buffer_local_vs_remoto_lat10.json` | 10 | 197 |

## Resultados por Latencia

| Latencia Red | Fin Par Local (C1) | Fin Par Remoto (C2/C3) | Impacto vs Local |
|---|---|---|---|
| **0 ciclos** | Ciclo 151 | Ciclo 87 | **42,4 % más rápido** (Paralelismo puro) |
| **3 ciclos** | Ciclo 151 | Ciclo 127 | **15,9 % más rápido** |
| **10 ciclos** | Ciclo 151 | Ciclo 197 | **30,5 % más lento** (Dominado por latencia) |

Métricas al final de cada simulación (pestaña Métricas):

| Latencia | Computador | Terminados | Throughput (por 100 ciclos) | CPU | Respuesta prom. | Cumplimiento | Equidad (Jain) |
|---|---|---|---|---|---|---|---|
| 0 | C1 (par local) | 2 | 1,32 | 99,3 % | 13,0 | 100,0 % | 1,00 |
| 0 | C2 (productor remoto) | 1 | 0,66 | 46,4 % | 1,0 | 100,0 % | 1,00 |
| 0 | C3 (consumidor remoto) | 1 | 0,66 | 53,0 % | 1,0 | 100,0 % | 1,00 |
| 0 | Sistema | 4 | 2,65 | 66,2 % | 7,0 | 100,0 % | 0,93 |
| 3 | C1 (par local) | 2 | 1,32 | 99,3 % | 13,0 | 100,0 % | 1,00 |
| 3 | C2 (productor remoto) | 1 | 0,66 | 53,0 % | 1,0 | 100,0 % | 1,00 |
| 3 | C3 (consumidor remoto) | 1 | 0,66 | 59,6 % | 1,0 | 100,0 % | 1,00 |
| 3 | Sistema | 4 | 2,65 | 70,6 % | 7,0 | 100,0 % | 0,97 |
| 10 | C1 (par local) | 2 | 1,02 | 76,1 % | 13,0 | 100,0 % | 1,00 |
| 10 | C2 (productor remoto) | 1 | 0,51 | 40,6 % | 1,0 | 100,0 % | 1,00 |
| 10 | C3 (consumidor remoto) | 1 | 0,51 | 45,7 % | 1,0 | 100,0 % | 1,00 |
| 10 | Sistema | 4 | 2,03 | 54,1 % | 7,0 | 100,0 % | 1,00 |

Con latencia 10 la CPU de C1 baja a 76,1 % aunque el par local termina igual en el ciclo 151: la utilización se calcula sobre toda la simulación, que se alarga hasta el 197 esperando al par remoto.

| Buffer | Producidos | Consumidos | Bloqueo promedio en semáforos |
|---|---|---|---|
| B1 (local) | 10 | 10 | 12,5 ciclos |
| B2 (remoto) | 10 | 10 | 2,5 ciclos |

Estos valores son los mismos con las tres latencias. Además del bloqueo en semáforos, cada proceso remoto pasa 10 × latencia ciclos esperando la red (0, 30 y 100 ciclos). En todas las corridas los semáforos terminan en sus valores iniciales: `vacios` = 3, `llenos` = 0 y `mutex` = 1.

## Conclusión

- **Ventaja del paralelismo remoto:** Con latencia baja (0 a 3 ciclos), la ejecución remota supera a la local porque distribuye el cómputo en dos CPUs independientes (C2 y C3) en lugar de competir por una sola (C1).
- **Punto de quiebre por latencia:** Cada ciclo adicional de latencia suma un costo fijo por cada acceso remoto al semáforo y buffer. Interpolando entre las latencias 3 y 10 (no se midió directamente), el punto de quiebre está en unos 5 a 6 ciclos: a partir de ahí el costo de comunicación en red supera el beneficio de la paralelización y la ejecución local es más eficiente.
- **Bloqueo en semáforos:** el par local se bloquea mucho más en semáforos (12,5 ciclos de promedio contra 2,5) porque productor y consumidor se turnan la misma CPU y chocan en el buffer; el par remoto, en cambio, pierde su tiempo esperando la red.
