package com.CristoMorado_api.CristoMorado_api.services;

import com.CristoMorado_api.CristoMorado_api.dto.*;
import com.CristoMorado_api.CristoMorado_api.entity.Docente;
import com.CristoMorado_api.CristoMorado_api.entity.Estudiante;
import com.CristoMorado_api.CristoMorado_api.entity.PadreFamilia;
import com.CristoMorado_api.CristoMorado_api.entity.Usuario;
import com.CristoMorado_api.CristoMorado_api.exception.AccesoDenegadoException;
import com.CristoMorado_api.CristoMorado_api.exception.RecursoNoEncontradoException;
import com.CristoMorado_api.CristoMorado_api.repository.DocenteRepository;
import com.CristoMorado_api.CristoMorado_api.repository.EstudianteRepository;
import com.CristoMorado_api.CristoMorado_api.repository.PadreFamiliaRepository;
import com.CristoMorado_api.CristoMorado_api.repository.PersonalRepository;
import com.CristoMorado_api.CristoMorado_api.repository.UsuarioRepository;
import com.CristoMorado_api.CristoMorado_api.util.RolUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final DocenteRepository docenteRepository;
    private final EstudianteRepository estudianteRepository;
    private final PadreFamiliaRepository padreFamiliaRepository;
    private final VerificacionService verificacionService;
    private final PersonalRepository personalRepository;
    private final PermisoService permisoService;

    public PerfilService(UsuarioRepository usuarioRepository, DocenteRepository docenteRepository,
                         EstudianteRepository estudianteRepository, PadreFamiliaRepository padreFamiliaRepository,
                         VerificacionService verificacionService, PersonalRepository personalRepository,
                         PermisoService permisoService) {
        this.personalRepository = personalRepository;
        this.permisoService = permisoService;
        this.usuarioRepository = usuarioRepository;
        this.docenteRepository = docenteRepository;
        this.estudianteRepository = estudianteRepository;
        this.padreFamiliaRepository = padreFamiliaRepository;
        this.verificacionService = verificacionService;
    }

    /** Perfil completo: cuenta + datos del alumno, docente o apoderado. */
    @Transactional(readOnly = true)
    public PerfilResponse obtener(Long usuarioId) {
        return construirRespuesta(buscarUsuario(usuarioId));
    }

    /**
     * Docentes, apoderados y administración pueden editar nombre, teléfono y
     * dirección. Los alumnos NO pueden cambiar sus datos (lo hace la administración).
     */
    @Transactional
    public PerfilResponse actualizar(Long usuarioId, PerfilUpdateRequest req) {
        Usuario u = buscarUsuario(usuarioId);
        if (!puedeEditar(u)) {
            throw new AccesoDenegadoException("Los alumnos no pueden modificar sus datos. "
                    + "Si algo está mal, pide a la administración que lo corrija.");
        }
        String nombre = req.nombre() == null ? "" : req.nombre().trim();
        // En la Directiva el nombre sale de nombres + apellidos (lo edita la administración).
        if (personalRepository.findByUsuarioId(usuarioId).isPresent()) nombre = "";
        if (!nombre.isEmpty()) {
            u.setNombre(nombre.length() > 100 ? nombre.substring(0, 100) : nombre);
        }
        usuarioRepository.save(u);

        Docente docente = docenteRepository.findByUsuarioId(usuarioId).orElse(null);
        if (docente != null) {
            if (!nombre.isEmpty()) docente.setNombreCompleto(nombre);
            if (req.telefono() != null) docente.setTelefono(req.telefono());
            if (req.direccion() != null) docente.setDireccion(req.direccion());
            if (req.fotoUrl() != null) docente.setFotoUrl(req.fotoUrl());
            docenteRepository.save(docente);
        }
        PadreFamilia padre = padreFamiliaRepository.findByUsuarioId(usuarioId).orElse(null);
        if (padre != null) {
            if (!nombre.isEmpty()) padre.setNombreCompleto(nombre);
            if (req.telefono() != null && !req.telefono().isBlank()) padre.setTelefono(req.telefono());
            if (req.direccion() != null) padre.setDireccion(req.direccion());
            padreFamiliaRepository.save(padre);
        }
        com.CristoMorado_api.CristoMorado_api.entity.Personal personal = personalRepository.findByUsuarioId(usuarioId).orElse(null);
        if (personal != null) {
            if (req.telefono() != null) personal.setTelefono(req.telefono().isBlank() ? null : req.telefono());
            personalRepository.save(personal);
        }
        return construirRespuesta(u);
    }

    // ---- Gmail ----

    public CodigoEnviadoResponse enviarCodigoCorreo(Long usuarioId, String correo) {
        return verificacionService.enviarCodigoVinculacion(usuarioId, correo);
    }

    /**
     * Sin @Transactional propio: VerificacionService maneja la transacción
     * (así un código incorrecto sí cuenta como intento fallido). El controlador
     * vuelve a pedir el perfil completo después.
     */
    public void verificarCorreo(Long usuarioId, String codigo) {
        verificacionService.confirmarVinculacion(usuarioId, codigo);
    }

    // ---- Contraseña (con código al Gmail) ----

    public CodigoEnviadoResponse enviarCodigoPassword(Long usuarioId) {
        return verificacionService.enviarCodigoCambioPassword(usuarioId);
    }

    public void cambiarPassword(Long usuarioId, CambiarPasswordRequest req) {
        verificacionService.cambiarPassword(usuarioId, req.passwordActual(), req.passwordNueva(), req.codigo());
    }

    // ------------------------------------------------------------------

    static boolean puedeEditar(Usuario u) {
        return RolUtil.categoria(u.getRol()) != RolUtil.Categoria.ALUMNO;
    }

    private PerfilResponse construirRespuesta(Usuario u) {
        Docente docente = docenteRepository.findByUsuarioId(u.getId()).orElse(null);
        Estudiante estudiante = estudianteRepository.findByUsuarioId(u.getId()).orElse(null);
        PadreFamilia padre = padreFamiliaRepository.findByUsuarioId(u.getId()).orElse(null);
        var personal = personalRepository.findByUsuarioId(u.getId()).orElse(null);
        return new PerfilResponse(
                u.getId(), u.getNombre(), u.getUsuario(), u.getCorreo(),
                Boolean.TRUE.equals(u.getCorreoVerificado()), u.getRol(), u.getEstado(),
                u.getFechaRegistro(), puedeEditar(u),
                docente == null ? null : DocenteResponse.fromEntity(docente, u),
                estudiante == null ? null : EstudianteResponse.fromEntity(estudiante),
                padre == null ? null : PadreFamiliaResponse.fromEntity(padre),
                personal == null ? null : PersonalResponse.fromEntity(personal),
                permisoService.permisosDe(u)
        );
    }

    private Usuario buscarUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }
}
