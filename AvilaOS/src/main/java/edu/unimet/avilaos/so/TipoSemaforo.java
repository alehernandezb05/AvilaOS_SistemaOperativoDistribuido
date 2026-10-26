package edu.unimet.avilaos.so;

public enum TipoSemaforo {
    CONTEO,     // cuenta recursos: espacios libres (vacios) o elementos guardados (llenos)
    BINARIO     // mutex de la region critica: empieza en 1 y nunca pasa de 1
}
