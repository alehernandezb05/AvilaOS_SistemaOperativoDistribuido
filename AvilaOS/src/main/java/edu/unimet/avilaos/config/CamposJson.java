package edu.unimet.avilaos.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class CamposJson {

    private CamposJson() { }

    static JsonObject objeto(JsonElement valor, String campo) {
        if (valor == null || !valor.isJsonObject()) {
            throw new IllegalArgumentException("Se esperaba un objeto JSON para " + campo + ".");
        }
        return valor.getAsJsonObject();
    }

    static String texto(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isString()
                && !valor.getAsString().isBlank()) return valor.getAsString();
        throw new IllegalArgumentException(campo + " debe ser un texto no vacío.");
    }

    static int entero(JsonElement valor, String campo) {
        if (valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isNumber()) {
            try {
                return valor.getAsBigDecimal().intValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                throw new IllegalArgumentException(campo + " debe ser un entero dentro del rango permitido.", e);
            }
        }
        throw new IllegalArgumentException(campo + " debe ser un entero.");
    }
}
