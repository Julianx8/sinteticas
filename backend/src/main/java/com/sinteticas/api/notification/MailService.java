package com.sinteticas.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * Envía correos de texto en segundo plano. Un fallo del servidor de correo se registra en el log
 * pero nunca rompe la operación que lo originó (la reserva ya quedó hecha aunque el correo no salga).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final Executor executor;

    public MailService(JavaMailSender mailSender, MailProperties properties,
                       @Qualifier(MailConfig.MAIL_EXECUTOR) Executor executor) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.executor = executor;
    }

    public void send(String to, String subject, String body) {
        if (!properties.enabled()) {
            log.info("Correo deshabilitado (MAIL_ENABLED=false). No se envía \"{}\" a {}", subject, to);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        executor.execute(() -> {
            try {
                mailSender.send(message);
                log.debug("Correo \"{}\" enviado a {}", subject, to);
            } catch (MailException ex) {
                log.warn("No se pudo enviar el correo \"{}\" a {}: {}", subject, to, ex.getMessage());
            }
        });
    }

}
