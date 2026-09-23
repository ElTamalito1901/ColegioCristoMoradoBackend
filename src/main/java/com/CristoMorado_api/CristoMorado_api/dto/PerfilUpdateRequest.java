package com.CristoMorado_api.CristoMorado_api.dto;

/**
 * Datos editables del perfil. El Gmail NO se cambia aquí: se vincula con
 * código en /api/perfil/{id}/correo/codigo y /correo/verificar.
 */
public record PerfilUpdateRequest(
        String nombre,
        // Si la cuenta es de un Docente, también se guardan en su registro.
        String telefono,
        String direccion,
        String fotoUrl
) {}
