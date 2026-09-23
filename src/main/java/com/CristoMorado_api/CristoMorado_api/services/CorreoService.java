package com.CristoMorado_api.CristoMorado_api.services;

import com.CristoMorado_api.CristoMorado_api.exception.DatosInvalidosException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envía los códigos de verificación por Gmail (SMTP).
 *
 * Configuración en application.properties (spring.mail.*). Mientras
 * app.correo.modo-prueba=true, el código además se muestra en la consola
 * del backend y en pantalla, para poder probar sin configurar Gmail.
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;
    private final boolean modoPrueba;

    public CorreoService(ObjectProvider<JavaMailSender> mailSender,
                         @Value("${app.correo.remitente:${spring.mail.username:}}") String remitente,
                         @Value("${app.correo.modo-prueba:true}") boolean modoPrueba) {
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.modoPrueba = modoPrueba;
    }

    public boolean isModoPrueba() {
        return modoPrueba;
    }

    public void enviarCodigo(String para, String nombre, String codigo, String motivo, int minutosValidez) {
        if (modoPrueba) {
            log.info("[MODO PRUEBA] Código para {} ({}): {}", para, motivo, codigo);
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remitente == null || remitente.isBlank()) {
            if (modoPrueba) return; // sin Gmail configurado: el código se ve en pantalla/consola
            throw new DatosInvalidosException("El envío de correos no está configurado. Avisa al administrador.");
        }
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom(remitente, "I.E. 2092 Cristo Morado");
            helper.setTo(para);
            helper.setSubject("Tu código de verificación: " + codigo);
            helper.setText(html(nombre, codigo, motivo, minutosValidez), true);
            sender.send(mensaje);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo a {}: {}", para, e.getMessage());
            if (!modoPrueba) {
                throw new DatosInvalidosException("No se pudo enviar el correo. Revisa el Gmail o intenta más tarde.");
            }
        }
    }

    private static String html(String nombre, String codigo, String motivo, int minutos) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;border:1px solid #E3D5C4;border-radius:12px;overflow:hidden">
                  <div style="background:#2E1233;color:#E4C766;padding:18px 24px;font-weight:bold;letter-spacing:.05em">
                    I.E. N.º 2092 · CRISTO MORADO
                  </div>
                  <div style="padding:24px;color:#241521">
                    <p>Hola %s,</p>
                    <p>Usa este código para <b>%s</b>:</p>
                    <p style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#4A1F52;text-align:center;margin:24px 0">%s</p>
                    <p style="color:#6B5A67;font-size:13px">El código vence en %d minutos. Si no lo pediste tú, ignora este correo.</p>
                  </div>
                </div>
                """.formatted(escapar(nombre), escapar(motivo), codigo, minutos);
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
