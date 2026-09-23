package com.CristoMorado_api.CristoMorado_api.dto;

import com.CristoMorado_api.CristoMorado_api.entity.Comunicado;
import com.CristoMorado_api.CristoMorado_api.util.RolUtil;

import java.time.LocalDateTime;
import java.util.List;

public record ComunicadoResponse(
        Long id,
        String titulo,
        String contenido,
        String imagen,
        Boolean anuncio,
        List<String> destinatarios,
        Long autorId,
        String autorNombre,
        String autorRol,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion,
        Boolean leido,          // para el usuario que consulta
        Long totalLecturas      // cuántos usuarios lo leyeron
) {
    public static ComunicadoResponse of(Comunicado c, boolean leido, long totalLecturas) {
        return new ComunicadoResponse(
                c.getId(), c.getTitulo(), c.getContenido(), c.getImagen(),
                Boolean.TRUE.equals(c.getAnuncio()),
                List.copyOf(c.getDestinatarios()),
                c.getAutor() == null ? null : c.getAutor().getId(),
                c.getAutor() == null ? "Administración" : c.getAutor().getNombre(),
                c.getAutor() == null ? "Administración" : RolUtil.etiqueta(c.getAutor().getRol()),
                c.getFechaCreacion(), c.getFechaActualizacion(),
                leido, totalLecturas
        );
    }
}
