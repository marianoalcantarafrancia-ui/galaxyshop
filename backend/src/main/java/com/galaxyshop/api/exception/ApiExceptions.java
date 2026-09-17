package com.galaxyshop.api.exception;

/** Excepciones propias de la API. */
public final class ApiExceptions {

    private ApiExceptions() { }

    public static class NoEncontrado extends RuntimeException {
        public NoEncontrado(String mensaje) { super(mensaje); }
    }

    public static class ReglaDeNegocio extends RuntimeException {
        public ReglaDeNegocio(String mensaje) { super(mensaje); }
    }
}
