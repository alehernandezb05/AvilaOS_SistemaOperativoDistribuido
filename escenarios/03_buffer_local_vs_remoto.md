# Escenario 3: Productor-Consumidor Local vs. Remoto (Variación de Latencia)

**Archivo:** `03_buffer_local_vs_remoto.json`

## Qué prueba
Compara la ejecución de un par Productor-Consumidor local (ambos en C1 ejecutándose en la misma CPU) contra un par remoto (Productor en C2 y Consumidor en C3 mediante acceso a semáforos remotos). Se evalúa el comportamiento variando la latencia de red en 0, 3 y 10 ciclos.

| Par | Productor | Consumidor | Ubicación |
|---|---|---|---|
| Local | P1 (10 inst) | P2 (10 inst) | C1 (Buffer B1 local) |
| Remoto | P3 (10 inst) | P4 (10 inst) | C2 y C3 (Buffer B2 remoto) |

## Resultados por Latencia

| Latencia Red | Fin Par Local (C1) | Fin Par Remoto (C2/C3) | Impacto vs Local |
|---|---|---|---|
| **0 ciclos** | Ciclo 151 | Ciclo 87 | **42,4 % más rápido** (Paralelismo puro) |
| **3 ciclos** | Ciclo 151 | Ciclo 127 | **15,9 % más rápido** |
| **10 ciclos** | Ciclo 151 | Ciclo 197 | **30,5 % más lento** (Dominado por latencia) |

## Conclusión

- **Ventaja del paralelismo remoto:** Con latencia baja (0 a 3 ciclos), la ejecución remota supera a la local porque distribuye el cómputo en dos CPUs independientes (C2 y C3) en lugar de competir por una sola (C1).
- **Punto de quiebre por latencia:** Cada ciclo adicional de latencia suma un costo fijo por cada acceso remoto al semáforo y buffer. A partir de aproximadamente 5 a 6 ciclos de latencia, el costo de comunicación en red supera el beneficio de la paralelización, haciendo que la ejecución local sea más eficiente.
