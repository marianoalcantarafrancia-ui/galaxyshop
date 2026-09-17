package com.galaxyshop.api.exception;

import com.galaxyshop.api.dto.Dtos.ErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Traduce las excepciones a respuestas JSON uniformes. */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(ApiExceptions.NoEncontrado.class)
    public ResponseEntity<ErrorDto> noEncontrado(ApiExceptions.NoEncontrado e, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(ApiExceptions.ReglaDeNegocio.class)
    public ResponseEntity<ErrorDto> reglaDeNegocio(ApiExceptions.ReglaDeNegocio e, HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, e.getMessage(), req);
    }

    /**
     * Se dispara cuando falla un @PreAuthorize o @PostAuthorize, o cuando la
     * decision granular de Keycloak es negativa.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> accesoDenegado(AccessDeniedException e, HttpServletRequest req) {
        return construir(HttpStatus.FORBIDDEN,
                "No cuenta con los permisos necesarios para esta operacion", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> validacion(MethodArgumentNotValidException e, HttpServletRequest req) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return construir(HttpStatus.BAD_REQUEST, detalle, req);
    }

    private ResponseEntity<ErrorDto> construir(HttpStatus estado, String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(estado).body(
                new ErrorDto(estado.value(), estado.getReasonPhrase(), mensaje, req.getRequestURI()));
    }
}
