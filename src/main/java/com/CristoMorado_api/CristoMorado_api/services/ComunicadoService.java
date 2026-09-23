package com.CristoMorado_api.CristoMorado_api.services;

import com.CristoMorado_api.CristoMorado_api.dto.ComunicadoRequest;
import com.CristoMorado_api.CristoMorado_api.dto.ComunicadoResponse;
import com.CristoMorado_api.CristoMorado_api.entity.Comunicado;
import com.CristoMorado_api.CristoMorado_api.entity.ComunicadoLectura;
import com.CristoMorado_api.CristoMorado_api.entity.Usuario;
import com.CristoMorado_api.CristoMorado_api.exception.AccesoDenegadoException;
import com.CristoMorado_api.CristoMorado_api.exception.DatosInvalidosException;
import com.CristoMorado_api.CristoMorado_api.exception.RecursoNoEncontradoException;
import com.CristoMorado_api.CristoMorado_api.repository.ComunicadoLecturaRepository;
import com.CristoMorado_api.CristoMorado_api.repository.ComunicadoRepository;
import com.CristoMorado_api.CristoMorado_api.repository.UsuarioRepository;
import com.CristoMorado_api.CristoMorado_api.util.RolUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ComunicadoService {

    /** ~3 MB en base64 (la imagen original de hasta ~2 MB). */
    private static final int IMAGEN_MAX = 3_000_000;

    private final ComunicadoRepository comunicadoRepository;
    private final ComunicadoLecturaRepository lecturaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    public ComunicadoService(ComunicadoRepository comunicadoRepository,
                             ComunicadoLecturaRepository lecturaRepository,
                             UsuarioRepository usuarioRepository,
                             PermisoService permisoService) {
        this.comunicadoRepository = comunicadoRepository;
        this.lecturaRepository = lecturaRepository;
        this.usuarioRepository = usuarioRepository;
        this.permisoService = permisoService;
    }

    /**
     * Comunicados que le corresponden al usuario:
     * - Administración ve todos (para gestionarlos).
     * - El resto ve los dirigidos a TODOS o a su grupo (Docentes, Alumnos, Padres).
     */
    @Transactional(readOnly = true)
    public List<ComunicadoResponse> listar(Long usuarioId) {
        Usuario u = buscarUsuario(usuarioId);
        Set<Long> leidos = new HashSet<>(lecturaRepository.idsLeidosPor(usuarioId));
        Map<Long, Long> lecturas = conteoLecturas();

        return comunicadoRepository.findAllByOrderByFechaCreacionDesc().stream()
                .filter(c -> puedeVer(u, c))
                .map(c -> ComunicadoResponse.of(c, leidos.contains(c.getId()),
                        lecturas.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ComunicadoResponse obtener(Long id, Long usuarioId) {
        Usuario u = buscarUsuario(usuarioId);
        Comunicado c = buscarComunicado(id);
        if (!puedeVer(u, c)) {
            throw new AccesoDenegadoException("No tienes acceso a este comunicado.");
        }
        return respuesta(c, usuarioId);
    }

    @Transactional
    public ComunicadoResponse crear(Long usuarioId, ComunicadoRequest req) {
        Usuario autor = exigirAdministrativo(usuarioId);
        Comunicado c = new Comunicado();
        aplicarDatos(c, req);
        c.setAutor(autor);
        c.setFechaCreacion(LocalDateTime.now());
        c = comunicadoRepository.save(c);
        // El autor no necesita verlo como "no leído".
        marcarLeido(c.getId(), usuarioId);
        return respuesta(c, usuarioId);
    }

    @Transactional
    public ComunicadoResponse actualizar(Long id, Long usuarioId, ComunicadoRequest req) {
        exigirAdministrativo(usuarioId);
        Comunicado c = buscarComunicado(id);
        aplicarDatos(c, req);
        c.setFechaActualizacion(LocalDateTime.now());
        return respuesta(comunicadoRepository.save(c), usuarioId);
    }

    @Transactional
    public void eliminar(Long id, Long usuarioId) {
        exigirAdministrativo(usuarioId);
        Comunicado c = buscarComunicado(id);
        lecturaRepository.eliminarPorComunicado(c.getId());
        comunicadoRepository.delete(c);
    }

    @Transactional
    public void marcarLeido(Long comunicadoId, Long usuarioId) {
        if (!comunicadoRepository.existsById(comunicadoId)) {
            throw new RecursoNoEncontradoException("Comunicado no encontrado.");
        }
        if (!lecturaRepository.existsByComunicadoIdAndUsuarioId(comunicadoId, usuarioId)) {
            lecturaRepository.save(ComunicadoLectura.builder()
                    .comunicadoId(comunicadoId)
                    .usuarioId(usuarioId)
                    .build());
        }
    }

    /** Cantidad de comunicados que el usuario todavía no ha leído. */
    @Transactional(readOnly = true)
    public long contarNoLeidos(Long usuarioId) {
        return listar(usuarioId).stream().filter(c -> !c.leido()).count();
    }

    // ------------------------------------------------------------------

    private boolean puedeVer(Usuario u, Comunicado c) {
        if (RolUtil.esAdministrativo(u.getRol())) return true;
        if (c.getAutor() != null && c.getAutor().getId().equals(u.getId())) return true;
        Set<String> grupos = c.getDestinatarios();
        String miGrupo = RolUtil.grupoComunicado(u.getRol());
        return grupos.contains(RolUtil.GRUPO_TODOS) || (miGrupo != null && grupos.contains(miGrupo));
    }

    private void aplicarDatos(Comunicado c, ComunicadoRequest req) {
        String titulo = req.titulo() == null ? "" : req.titulo().trim();
        if (titulo.isEmpty()) {
            throw new DatosInvalidosException("El título es obligatorio.");
        }
        if (titulo.length() > 200) {
            throw new DatosInvalidosException("El título no puede superar los 200 caracteres.");
        }
        String contenido = req.contenido() == null ? "" : req.contenido().trim();
        if (contenido.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").isBlank()) {
            throw new DatosInvalidosException("El mensaje del comunicado es obligatorio.");
        }
        if (req.imagen() != null && req.imagen().length() > IMAGEN_MAX) {
            throw new DatosInvalidosException("La imagen es muy pesada (máximo 2 MB).");
        }

        Set<String> grupos = new LinkedHashSet<>();
        if (req.destinatarios() != null) {
            for (String g : req.destinatarios()) {
                String grupo = g == null ? "" : g.trim().toUpperCase();
                if (!RolUtil.esGrupoValido(grupo)) {
                    throw new DatosInvalidosException("Destinatario no válido: " + g);
                }
                grupos.add(grupo);
            }
        }
        if (grupos.isEmpty()) {
            throw new DatosInvalidosException("Selecciona al menos un grupo de destinatarios.");
        }
        if (grupos.contains(RolUtil.GRUPO_TODOS)) {
            grupos = new LinkedHashSet<>(List.of(RolUtil.GRUPO_TODOS));
        }

        c.setTitulo(titulo);
        c.setContenido(contenido);
        c.setImagen(req.imagen() == null || req.imagen().isBlank() ? null : req.imagen());
        c.setAnuncio(Boolean.TRUE.equals(req.anuncio()));
        c.getDestinatarios().clear();
        c.getDestinatarios().addAll(grupos);
    }

    private ComunicadoResponse respuesta(Comunicado c, Long usuarioId) {
        boolean leido = lecturaRepository.existsByComunicadoIdAndUsuarioId(c.getId(), usuarioId);
        return ComunicadoResponse.of(c, leido, conteoLecturas().getOrDefault(c.getId(), 0L));
    }

    private Map<Long, Long> conteoLecturas() {
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : lecturaRepository.contarPorComunicado()) {
            mapa.put((Long) fila[0], (Long) fila[1]);
        }
        return mapa;
    }

    /** Administrador, o Directiva con el permiso "Crear comunicados". */
    private Usuario exigirAdministrativo(Long usuarioId) {
        Usuario u = buscarUsuario(usuarioId);
        if (!permisoService.tiene(u, com.CristoMorado_api.CristoMorado_api.util.Permiso.COMUNICADOS)) {
            throw new AccesoDenegadoException("No tienes permiso para gestionar comunicados.");
        }
        return u;
    }

    private Usuario buscarUsuario(Long id) {
        if (id == null) throw new DatosInvalidosException("Falta el usuario.");
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    private Comunicado buscarComunicado(Long id) {
        return comunicadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Comunicado no encontrado."));
    }
}
