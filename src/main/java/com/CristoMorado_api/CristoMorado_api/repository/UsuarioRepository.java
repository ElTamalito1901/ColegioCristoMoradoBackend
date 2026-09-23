package com.CristoMorado_api.CristoMorado_api.repository;

import com.CristoMorado_api.CristoMorado_api.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    Optional<Usuario> findByUsuarioIgnoreCase(String usuario);

    boolean existsByUsuarioIgnoreCase(String usuario);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    boolean existsByUsuario(String usuario);

    long countByEstadoTrue();

    java.util.List<Usuario> findByEstadoTrueOrderByNombreAsc();

    long countByRolIgnoreCase(String rol);
}
