package edu.unimet.avilaos.estructuras;

/**
 * Criterio para ordenar: negativo si a va antes que b, 0 si da igual, positivo
 * si a va después.
 */
public interface Comparador<T> {

    int comparar(T a, T b);
}
