package com.CristoMorado_api.CristoMorado_api.dto;

/**
 * Respuesta al pedir un código. `codigoPrueba` solo viene lleno cuando
 * app.correo.modo-prueba=true (para probar sin configurar Gmail).
 */
public record CodigoEnviadoResponse(
        String correoEnmascarado,   // p.ej. "lu*****23@gmail.com"
        int minutosValidez,
        int segundosParaReenviar,
        String codigoPrueba
) {}
