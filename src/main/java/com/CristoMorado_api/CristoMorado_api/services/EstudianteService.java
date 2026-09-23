package com.CristoMorado_api.CristoMorado_api.services;

import com.CristoMorado_api.CristoMorado_api.dto.EstudianteRequest;
import com.CristoMorado_api.CristoMorado_api.dto.EstudianteResponse;
import com.CristoMorado_api.CristoMorado_api.entity.Estudiante;
import com.CristoMorado_api.CristoMorado_api.entity.EstudianteFamiliar;
import com.CristoMorado_api.CristoMorado_api.entity.PadreFamilia;
import com.CristoMorado_api.CristoMorado_api.entity.Usuario;
import com.CristoMorado_api.CristoMorado_api.exception.RecursoDuplicadoException;
import com.CristoMorado_api.CristoMorado_api.exception.RecursoNoEncontradoException;
import com.CristoMorado_api.CristoMorado_api.repository.EstudianteFamiliarRepository;
import com.CristoMorado_api.CristoMorado_api.repository.EstudianteRepository;
import com.CristoMorado_api.CristoMorado_api.repository.PadreFamiliaRepository;
import com.CristoMorado_api.CristoMorado_api.util.TipoDocumento;
import com.CristoMorado_api.CristoMorado_api.util.UsuarioFormato;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EstudianteService {

    /** Rol con el que se crean las cuentas de alumno en `usuarios`. */
    public static final String ROL_ALUMNO = "ALUMNO";

    private final EstudianteRepository estudianteRepository;
    private final PadreFamiliaRepository padreFamiliaRepository;
    private final EstudianteFamiliarRepository vinculoRepository;
    private final CuentaService cuentaService;

    public EstudianteService(EstudianteRepository estudianteRepository,
                              PadreFamiliaRepository padreFamiliaRepository,
                              EstudianteFamiliarRepository vinculoRepository,
                              CuentaService cuentaService) {
        this.estudianteRepository = estudianteRepository;
        this.padreFamiliaRepository = padreFamiliaRepository;
        this.vinculoRepository = vinculoRepository;
        this.cuentaService = cuentaService;
    }

    @Transactional(readOnly = true)
    public List<EstudianteResponse> listar() {
        return estudianteRepository.findAll().stream()
                .map(EstudianteResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public EstudianteResponse obtener(Long id) {
        return EstudianteResponse.fromEntity(buscarOFallar(id));
    }

    @Transactional
    public EstudianteResponse crear(EstudianteRequest req) {
        String documento = TipoDocumento.validarNumero(req.tipoDocumento(), req.dni());
        if (estudianteRepository.existsByDni(documento)) {
            throw new RecursoDuplicadoException("Ya existe un estudiante con ese número de documento.");
        }
        Estudiante e = mapearDatos(new Estudiante(), req);
        // Todo alumno nuevo nace con su cuenta de acceso: usuario ALU + DNI.
        e.setUsuario(crearCuenta(e, req.password()));
        e = estudianteRepository.save(e);
        return EstudianteResponse.fromEntity(estudianteRepository.findById(e.getId()).orElseThrow());
    }

    @Transactional
    public EstudianteResponse actualizar(Long id, EstudianteRequest req) {
        Estudiante e = buscarOFallar(id);
        String dniNuevo = TipoDocumento.validarNumero(req.tipoDocumento(), req.dni());
        if (!e.getDni().equals(dniNuevo) && estudianteRepository.existsByDni(dniNuevo)) {
            throw new RecursoDuplicadoException("Ya existe un estudiante con ese número de documento.");
        }
        mapearDatos(e, req);
        sincronizarCuenta(e, req);
        estudianteRepository.save(e);
        // Los padres se vinculan solo en "Vínculo Padre-Hijo" (/api/vinculos).
        return EstudianteResponse.fromEntity(estudianteRepository.findById(id).orElseThrow());
    }

    /**
     * Botón "Desactivar/Activar cuenta" de la tabla de Alumnos.
     * Inactivo = no puede iniciar sesión; el resto de sus datos no cambia.
     */
    @Transactional
    public EstudianteResponse cambiarEstado(Long id, boolean activo) {
        Estudiante e = buscarOFallar(id);
        e.setEstado(activo ? "Activo" : "Inactivo");
        if (e.getUsuario() == null) {
            e.setUsuario(crearCuenta(e, null));
        } else {
            cuentaService.cambiarEstado(e.getUsuario(), activo);
        }
        estudianteRepository.save(e);
        return EstudianteResponse.fromEntity(e);
    }

    @Transactional
    public void eliminar(Long id) {
        Estudiante e = buscarOFallar(id);
        Usuario cuenta = e.getUsuario();
        estudianteRepository.delete(e);
        estudianteRepository.flush();
        // Al borrar al alumno también se borra su cuenta de acceso.
        cuentaService.eliminar(cuenta);
    }

    long contarTotal() {
        return estudianteRepository.count();
    }

    List<Estudiante> ultimosRegistrados() {
        return estudianteRepository.findTop5ByOrderByFechaRegistroDesc();
    }

    // ------------------------------------------------------------------
    // Cuenta de acceso del alumno
    // ------------------------------------------------------------------

    private Usuario crearCuenta(Estudiante e, String password) {
        return cuentaService.crear(UsuarioFormato.ALUMNO, e.getDni(), e.getNombreCompleto(), ROL_ALUMNO,
                password, !"Inactivo".equalsIgnoreCase(e.getEstado()));
    }

    /**
     * Aplica sobre la cuenta del alumno los cambios de la vista de Alumnos:
     * usuario (si cambió el DNI), nombre, estado y contraseña (opcional).
     * "Inactivo" en el alumno = no puede iniciar sesión.
     */
    private void sincronizarCuenta(Estudiante e, EstudianteRequest req) {
        if (e.getUsuario() == null) {
            // Alumno antiguo sin cuenta: se le crea ahora.
            e.setUsuario(crearCuenta(e, req.password()));
            return;
        }
        cuentaService.sincronizar(e.getUsuario(), UsuarioFormato.ALUMNO, e.getDni(), e.getNombreCompleto(),
                !"Inactivo".equalsIgnoreCase(e.getEstado()), req.password());
    }

    private Estudiante mapearDatos(Estudiante e, EstudianteRequest req) {
        e.setNombreCompleto(req.nombreCompleto());
        e.setTipoDocumento(TipoDocumento.normalizar(req.tipoDocumento()));
        e.setDni(TipoDocumento.validarNumero(req.tipoDocumento(), req.dni()));
        e.setFotoUrl(req.fotoUrl());
        e.setFechaNacimiento(req.fechaNacimiento());
        e.setGenero(req.genero());
        e.setNacionalidad(req.nacionalidad());
        e.setDireccion(req.direccion());
        e.setTelefono(req.telefono());
        e.setGrado(req.grado());
        e.setSeccion(req.seccion());
        e.setAnioIngreso(req.anioIngreso());
        if (req.estado() != null) e.setEstado(req.estado());
        return e;
    }

    private Estudiante buscarOFallar(Long id) {
        return estudianteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante no encontrado."));
    }
}
