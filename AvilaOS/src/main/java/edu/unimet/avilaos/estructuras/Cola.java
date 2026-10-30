package edu.unimet.avilaos.estructuras;

public class Cola<T> {

    private Nodo<T> frente;
    private Nodo<T> fin;
    private int tamano;

    public Cola() {
        this.frente = null;
        this.fin = null;
        this.tamano = 0;
    }

    public boolean estaVacia() {
        return tamano == 0;
    }

    public int getTamano() {
        return tamano;
    }

    // Agrega un elemento al final de la cola
    public void encolar(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (estaVacia()) {
            frente = nuevo;
        } else {
            fin.setSiguiente(nuevo);
        }
        fin = nuevo;
        tamano++;
    }

    // Saca y devuelve el primer elemento (null si está vacía)
    public T desencolar() {
        if (estaVacia()) {
            return null;
        }
        T dato = frente.getDato();
        frente = frente.getSiguiente();
        if (frente == null) {
            fin = null;
        }
        tamano--;
        return dato;
    }

    // Devuelve el primer elemento sin sacarlo (null si está vacía)
    public T verFrente() {
        return estaVacia() ? null : frente.getDato();
    }

    // Devuelve el elemento en la posición indicada sin sacarlo (null si no existe)
    public T obtener(int indice) {
        if (indice < 0 || indice >= tamano) {
            return null;
        }
        Nodo<T> actual = frente;
        for (int i = 0; i < indice; i++) {
            actual = actual.getSiguiente();
        }
        return actual.getDato();
    }

    // Quita un elemento específico de cualquier posición. Devuelve true si lo encontró
    public boolean remover(T dato) {
        Nodo<T> anterior = null;
        Nodo<T> actual = frente;
        while (actual != null) {
            if (actual.getDato().equals(dato)) {
                if (anterior == null) {
                    frente = actual.getSiguiente();
                } else {
                    anterior.setSiguiente(actual.getSiguiente());
                }
                if (actual == fin) {
                    fin = anterior;
                }
                tamano--;
                return true;
            }
            anterior = actual;
            actual = actual.getSiguiente();
        }
        return false;
    }

    // Reordena la cola segun el criterio dado. Si dos elementos empatan, conservan su orden de llegada
    public void ordenar(Comparador<T> criterio) {
        Nodo<T> pendiente = frente;
        frente = null;
        fin = null;
        while (pendiente != null) {
            Nodo<T> nodo = pendiente;
            pendiente = pendiente.getSiguiente();
            nodo.setSiguiente(null);
            insertarNodoOrdenado(nodo, criterio);
        }
    }

    private void insertarNodoOrdenado(Nodo<T> nodo, Comparador<T> criterio) {
        if (frente == null) {
            frente = nodo;
            fin = nodo;
            return;
        }
        // Va al frente solo si es estrictamente menor (asi los empates respetan el orden de llegada)
        if (criterio.comparar(nodo.getDato(), frente.getDato()) < 0) {
            nodo.setSiguiente(frente);
            frente = nodo;
            return;
        }
        Nodo<T> actual = frente;
        while (actual.getSiguiente() != null
                && criterio.comparar(actual.getSiguiente().getDato(), nodo.getDato()) <= 0) {
            actual = actual.getSiguiente();
        }
        nodo.setSiguiente(actual.getSiguiente());
        actual.setSiguiente(nodo);
        if (nodo.getSiguiente() == null) {
            fin = nodo;
        }
    }
}
