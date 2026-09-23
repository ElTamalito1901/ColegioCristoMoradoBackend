package com.CristoMorado_api.CristoMorado_api.controller;

import com.CristoMorado_api.CristoMorado_api.dto.ContactosResponse;
import com.CristoMorado_api.CristoMorado_api.dto.MensajeRequest;
import com.CristoMorado_api.CristoMorado_api.dto.MensajeResponse;
import com.CristoMorado_api.CristoMorado_api.services.MensajeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Mensajería entre usuarios. Siempre con ?usuarioId= del usuario logeado. */
@RestController
@RequestMapping("/api/mensajes")
@CrossOrigin(origins = "http://localhost:4200")
public class MensajeController {

    private final MensajeService mensajeService;

    public MensajeController(MensajeService mensajeService) {
        this.mensajeService = mensajeService;
    }

    @GetMapping("/contactos")
    public ContactosResponse contactos(@RequestParam Long usuarioId) {
        return mensajeService.contactos(usuarioId);
    }

    @GetMapping("/recibidos")
    public List<MensajeResponse> recibidos(@RequestParam Long usuarioId) {
        return mensajeService.recibidos(usuarioId);
    }

    @GetMapping("/enviados")
    public List<MensajeResponse> enviados(@RequestParam Long usuarioId) {
        return mensajeService.enviados(usuarioId);
    }

    @PostMapping
    public ResponseEntity<MensajeResponse> enviar(@RequestParam Long usuarioId,
                                                  @RequestBody MensajeRequest request) {
        return ResponseEntity.ok(mensajeService.enviar(usuarioId, request));
    }

    @PostMapping("/{id}/leido")
    public ResponseEntity<Void> marcarLeido(@PathVariable Long id, @RequestParam Long usuarioId) {
        mensajeService.marcarLeido(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @RequestParam Long usuarioId) {
        mensajeService.eliminar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}
