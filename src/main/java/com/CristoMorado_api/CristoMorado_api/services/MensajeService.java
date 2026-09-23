package com.CristoMorado_api.CristoMorado_api.services;

import com.CristoMorado_api.CristoMorado_api.dto.ContactosResponse;
import com.CristoMorado_api.CristoMorado_api.dto.ContactosResponse.Contacto;
import com.CristoMorado_api.CristoMorado_api.dto.ContactosResponse.Grupo;
import com.CristoMorado_api.CristoMorado_api.dto.MensajeRequest;
import com.CristoMorado_api.CristoMorado_api.dto.MensajeResponse;
import com.CristoMorado_api.CristoMorado_api.dto.MensajeResponse.Participante;
import com.CristoMorado_api.CristoMorado_api.entity.*;
import com.CristoMorado_api.CristoMorado_api.exception.AccesoDenegadoException;
import com.CristoMorado_api.CristoMorado_api.exception.DatosInvalidosException;
import com.CristoMorado_api.CristoMorado_api.exception.RecursoNoEncontradoException;
import com.CristoMorado_api.CristoMorado_api.repository.*;
import com.CristoMorado_api.CristoMorado_api.util.RolUtil;
import com.CristoMorado_api.CristoMorado_api.util.RolUtil.Categoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Mensajería interna. Reglas de quién puede escribir a quién:
 * - Administración: a cualquier usuario activo.
 * - Docente: a administración, docentes, alumnos y padres.
 * - Alumno: a los compañeros de SU salón (mismo grado y sección),
 *   a los docentes y a administración.
 * - Padre: a los docentes y a administración.
 * Además, cualquiera puede RESPONDER a quien le escribió.
 */
@Service
public class MensajeService {

    private static final int MAX_DESTINATARIOS = 500;

    private final MensajeRepository mensajeRepository;
    private final MensajeDestinatarioRepository destinatarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;
    private final DocenteRepository docenteRepository;

    public MensajeService(MensajeRepository mensajeRepository,
                          MensajeDestinatarioRepository destinatarioRepository,
                          UsuarioRepository usuarioRepository,
                          EstudianteRepository estudianteRepository,
                          DocenteRepository docenteRepository) {
        this.mensajeRepository = mensajeRepository;
        this.destinatarioRepository = destinatarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.estudianteRepository = estudianteRepository;
        this.docenteRepository = docenteRepository;
    }

    // ------------------------------------------------------------------
    // Contactos
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ContactosResponse contactos(Long usuarioId) {
        Usuario yo = buscarUsuario(usuarioId);
        Categoria miCategoria = RolUtil.categoria(yo.getRol());

        // usuarioId -> "5° A" (alumnos) y usuarioId -> especialidad (docentes)
        Map<Long, String> salonPorUsuario = new HashMap<>();
        for (Estudiante e : estudianteRepository.findAll()) {
            if (e.getUsuario() != null) {
                salonPorUsuario.put(e.getUsuario().getId(), salon(e));
            }
        }
        Map<Long, String> especialidadPorUsuario = new HashMap<>();
        for (Docente d : docenteRepository.findAll()) {
            if (d.getUsuarioId() != null && d.getEspecialidad() != null) {
                especialidadPorUsuario.put(d.getUsuarioId(), d.getEspecialidad());
            }
        }
        String miSalon = salonPorUsuario.get(yo.getId());

        List<Contacto> contactos = new ArrayList<>();
        for (Usuario u : usuarioRepository.findByEstadoTrueOrderByNombreAsc()) {
            if (u.getId().equals(yo.getId())) continue;
            Categoria cat = RolUtil.categoria(u.getRol());
            if (!puedeEscribirA(miCategoria, miSalon, cat, salonPorUsuario.get(u.getId()))) continue;

            String detalle = switch (cat) {
                case ALUMNO -> salonPorUsuario.getOrDefault(u.getId(), "");
                case DOCENTE -> especialidadPorUsuario.getOrDefault(u.getId(), "");
                default -> "";
            };
            contactos.add(new Contacto(u.getId(), u.getNombre(), RolUtil.etiqueta(u.getRol()), detalle));
        }

        return new ContactosResponse(contactos, armarGrupos(miCategoria, miSalon, contactos, salonPorUsuario));
    }

    private boolean puedeEscribirA(Categoria yo, String miSalon, Categoria otro, String salonOtro) {
        return switch (yo) {
            case ADMINISTRATIVO -> true;
            case DOCENTE -> otro != Categoria.OTRO;
            case ALUMNO -> otro == Categoria.ADMINISTRATIVO
                    || otro == Categoria.DOCENTE
                    || (otro == Categoria.ALUMNO && miSalon != null && miSalon.equals(salonOtro));
            case PADRE -> otro == Categoria.ADMINISTRATIVO || otro == Categoria.DOCENTE;
            default -> otro == Categoria.ADMINISTRATIVO;
        };
    }

    private List<Grupo> armarGrupos(Categoria yo, String miSalon, List<Contacto> contactos,
                                    Map<Long, String> salonPorUsuario) {
        List<Grupo> grupos = new ArrayList<>();

        if (yo == Categoria.ALUMNO) {
            if (miSalon != null) {
                List<Long> companeros = contactos.stream()
                        .filter(c -> miSalon.equals(salonPorUsuario.get(c.usuarioId())))
                        .map(Contacto::usuarioId).toList();
                if (!companeros.isEmpty()) {
                    grupos.add(new Grupo("SALON_" + miSalon, "Mi salón (" + miSalon + ")",
                            companeros.size() + " compañeros", companeros));
                }
            }
            return grupos;
        }

        if (yo == Categoria.ADMINISTRATIVO || yo == Categoria.DOCENTE) {
            // Un grupo por cada salón (grado + sección)
            Map<String, List<Long>> porSalon = new TreeMap<>();
            for (Contacto c : contactos) {
                String s = salonPorUsuario.get(c.usuarioId());
                if (s != null) porSalon.computeIfAbsent(s, k -> new ArrayList<>()).add(c.usuarioId());
            }
            porSalon.forEach((s, ids) -> grupos.add(new Grupo("SALON_" + s, "Salón " + s,
                    ids.size() + " alumnos", ids)));

            agregarGrupoPorRol(grupos, contactos, "Docente", "DOCENTES", "Todos los docentes");
            agregarGrupoPorRol(grupos, contactos, "Padre", "PADRES", "Todos los padres");
            if (yo == Categoria.ADMINISTRATIVO) {
                agregarGrupoPorRol(grupos, contactos, "Estudiante", "ALUMNOS", "Todos los alumnos");
            }
        }
        return grupos;
    }

    private void agregarGrupoPorRol(List<Grupo> grupos, List<Contacto> contactos,
                                    String rol, String clave, String nombre) {
        List<Long> ids = contactos.stream().filter(c -> rol.equals(c.rol())).map(Contacto::usuarioId).toList();
        if (!ids.isEmpty()) {
            grupos.add(new Grupo(clave, nombre, ids.size() + " personas", ids));
        }
    }

    private static String salon(Estudiante e) {
        return e.getGrado() + " " + e.getSeccion();
    }

    // ------------------------------------------------------------------
    // Bandejas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MensajeResponse> recibidos(Long usuarioId) {
        buscarUsuario(usuarioId);
        return destinatarioRepository.recibidosPor(usuarioId).stream()
                .map(d -> respuesta(d.getMensaje(), "RECIBIDO", d.getLeido()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MensajeResponse> enviados(Long usuarioId) {
        buscarUsuario(usuarioId);
        return mensajeRepository.enviadosPor(usuarioId).stream()
                .map(m -> respuesta(m, "ENVIADO", true))
                .toList();
    }

    @Transactional
    public MensajeResponse enviar(Long usuarioId, MensajeRequest req) {
        Usuario yo = buscarUsuario(usuarioId);

        String asunto = req.asunto() == null ? "" : req.asunto().trim();
        if (asunto.isEmpty()) asunto = "(Sin asunto)";
        if (asunto.length() > 200) {
            throw new DatosInvalidosException("El asunto no puede superar los 200 caracteres.");
        }
        String contenido = req.contenido() == null ? "" : req.contenido().trim();
        if (contenido.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").isBlank()) {
            throw new DatosInvalidosException("Escribe el mensaje antes de enviarlo.");
        }

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (req.destinatariosIds() != null) {
            for (Long id : req.destinatariosIds()) {
                if (id != null && !id.equals(yo.getId())) ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            throw new DatosInvalidosException("Elige al menos un destinatario.");
        }
        if (ids.size() > MAX_DESTINATARIOS) {
            throw new DatosInvalidosException("Demasiados destinatarios (máximo " + MAX_DESTINATARIOS + ").");
        }

        // A quién puedo escribir: mis contactos + (si respondo) quien me escribió
        Set<Long> permitidos = new HashSet<>();
        contactos(usuarioId).contactos().forEach(c -> permitidos.add(c.usuarioId()));
        if (req.respuestaAId() != null) {
            Mensaje original = mensajeRepository.findById(req.respuestaAId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("El mensaje original no existe."));
            boolean soyDestinatario = original.getDestinatarios().stream()
                    .anyMatch(d -> d.getDestinatario().getId().equals(yo.getId()));
            boolean soyRemitente = original.getRemitente() != null
                    && original.getRemitente().getId().equals(yo.getId());
            if (soyDestinatario && original.getRemitente() != null) {
                permitidos.add(original.getRemitente().getId());
            }
            if (soyRemitente) {
                original.getDestinatarios().forEach(d -> permitidos.add(d.getDestinatario().getId()));
            }
        }

        Mensaje m = Mensaje.builder()
                .remitente(yo)
                .asunto(asunto)
                .contenido(contenido)
                .respuestaAId(req.respuestaAId())
                .fechaEnvio(LocalDateTime.now())
                .build();

        for (Long id : ids) {
            Usuario destino = usuarioRepository.findById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Destinatario no encontrado: " + id));
            if (!permitidos.contains(id)) {
                throw new AccesoDenegadoException("No puedes enviar mensajes a " + destino.getNombre() + ".");
            }
            m.getDestinatarios().add(MensajeDestinatario.builder()
                    .mensaje(m)
                    .destinatario(destino)
                    .build());
        }
        return respuesta(mensajeRepository.save(m), "ENVIADO", true);
    }

    @Transactional
    public void marcarLeido(Long mensajeId, Long usuarioId) {
        MensajeDestinatario d = destinatarioRepository.findByMensajeIdAndDestinatarioId(mensajeId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado."));
        if (!Boolean.TRUE.equals(d.getLeido())) {
            d.setLeido(true);
            d.setFechaLectura(LocalDateTime.now());
            destinatarioRepository.save(d);
        }
    }

    /**
     * Quita el mensaje de la bandeja del usuario (no de la de los demás).
     * Cuando ya nadie lo tiene en su bandeja, se borra de la base de datos.
     */
    @Transactional
    public void eliminar(Long mensajeId, Long usuarioId) {
        Mensaje m = mensajeRepository.findById(mensajeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado."));
        boolean afectado = false;

        if (m.getRemitente() != null && m.getRemitente().getId().equals(usuarioId)) {
            m.setEliminadoRemitente(true);
            afectado = true;
        }
        for (MensajeDestinatario d : m.getDestinatarios()) {
            if (d.getDestinatario().getId().equals(usuarioId)) {
                d.setEliminado(true);
                afectado = true;
            }
        }
        if (!afectado) {
            throw new RecursoNoEncontradoException("Mensaje no encontrado.");
        }

        boolean remitenteLoQuito = m.getRemitente() == null || Boolean.TRUE.equals(m.getEliminadoRemitente());
        boolean todosLoQuitaron = m.getDestinatarios().stream().allMatch(d -> Boolean.TRUE.equals(d.getEliminado()));
        if (remitenteLoQuito && todosLoQuitaron) {
            mensajeRepository.delete(m);
        } else {
            mensajeRepository.save(m);
        }
    }

    @Transactional(readOnly = true)
    public long contarNoLeidos(Long usuarioId) {
        return destinatarioRepository.countByDestinatarioIdAndLeidoFalseAndEliminadoFalse(usuarioId);
    }

    // ------------------------------------------------------------------

    private MensajeResponse respuesta(Mensaje m, String bandeja, Boolean leido) {
        Usuario r = m.getRemitente();
        Participante remitente = r == null
                ? new Participante(null, "Usuario eliminado", "", true)
                : new Participante(r.getId(), r.getNombre(), RolUtil.etiqueta(r.getRol()), true);
        List<Participante> destinatarios = m.getDestinatarios().stream()
                .map(d -> new Participante(d.getDestinatario().getId(), d.getDestinatario().getNombre(),
                        RolUtil.etiqueta(d.getDestinatario().getRol()), d.getLeido()))
                .toList();
        return new MensajeResponse(m.getId(), m.getAsunto(), m.getContenido(), m.getFechaEnvio(),
                m.getRespuestaAId(), remitente, destinatarios, leido, bandeja);
    }

    private Usuario buscarUsuario(Long id) {
        if (id == null) throw new DatosInvalidosException("Falta el usuario.");
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }
}
