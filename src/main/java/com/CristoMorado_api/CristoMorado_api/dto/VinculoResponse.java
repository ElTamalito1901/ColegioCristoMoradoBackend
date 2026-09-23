package com.CristoMorado_api.CristoMorado_api.dto;

import com.CristoMorado_api.CristoMorado_api.entity.Estudiante;
import com.CristoMorado_api.CristoMorado_api.entity.EstudianteFamiliar;
import com.CristoMorado_api.CristoMorado_api.entity.PadreFamilia;

public record VinculoResponse(
        Long id,
        String parentesco,
        // Padre / apoderado
        Long padreId,
        String padreNombre,
        String padreTipoDocumento,
        String padreDocumento,
        String padreTelefono,
        String padreEstado,
        // Alumno
        Long estudianteId,
        String estudianteNombre,
        String estudianteTipoDocumento,
        String estudianteDocumento,
        String grado,
        String seccion,
        String estudianteEstado
) {
    public static VinculoResponse fromEntity(EstudianteFamiliar v) {
        PadreFamilia p = v.getPadreFamilia();
        Estudiante e = v.getEstudiante();
        return new VinculoResponse(
                v.getId(), v.getParentesco(),
                p.getId(), p.getNombreCompleto(), p.getTipoDocumento(), p.getDni(), p.getTelefono(), p.getEstado(),
                e.getId(), e.getNombreCompleto(), e.getTipoDocumento(), e.getDni(), e.getGrado(), e.getSeccion(), e.getEstado());
    }
}
