package com.CristoMorado_api.CristoMorado_api.services;

import com.CristoMorado_api.CristoMorado_api.repository.CodigoVerificacionRepository;
import com.CristoMorado_api.CristoMorado_api.repository.ComunicadoLecturaRepository;
import com.CristoMorado_api.CristoMorado_api.repository.ComunicadoRepository;
import com.CristoMorado_api.CristoMorado_api.repository.MensajeDestinatarioRepository;
import com.CristoMorado_api.CristoMorado_api.repository.MensajeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Antes de borrar una cuenta de `usuarios` hay que soltar las filas que
 * la referencian (comunicados, lecturas, mensajes); si no, PostgreSQL
 * rechaza el borrado por las llaves foráneas.
 */
@Service
public class LimpiezaUsuarioService {

    private final ComunicadoRepository comunicadoRepository;
    private final ComunicadoLecturaRepository lecturaRepository;
    private final MensajeRepository mensajeRepository;
    private final MensajeDestinatarioRepository destinatarioRepository;
    private final CodigoVerificacionRepository codigoRepository;

    public LimpiezaUsuarioService(ComunicadoRepository comunicadoRepository,
                                  ComunicadoLecturaRepository lecturaRepository,
                                  MensajeRepository mensajeRepository,
                                  MensajeDestinatarioRepository destinatarioRepository,
                                  CodigoVerificacionRepository codigoRepository) {
        this.comunicadoRepository = comunicadoRepository;
        this.lecturaRepository = lecturaRepository;
        this.mensajeRepository = mensajeRepository;
        this.destinatarioRepository = destinatarioRepository;
        this.codigoRepository = codigoRepository;
    }

    @Transactional
    public void liberarReferencias(Long usuarioId) {
        comunicadoRepository.desvincularAutor(usuarioId);       // el comunicado se conserva
        lecturaRepository.eliminarPorUsuario(usuarioId);
        destinatarioRepository.eliminarPorDestinatario(usuarioId);
        mensajeRepository.desvincularRemitente(usuarioId);      // queda como "Usuario eliminado"
        codigoRepository.eliminarPorUsuario(usuarioId);
    }
}
