package com.sinteticas.api;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reemplaza el servidor SMTP en los tests: guarda los correos en memoria para poder revisarlos.
 */
public class CapturingMailSender extends JavaMailSenderImpl {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage... simpleMessages) {
        sent.addAll(Arrays.asList(simpleMessages));
    }

    /** Correos enviados a esa dirección, en orden. */
    public List<SimpleMailMessage> sentTo(String email) {
        return sent.stream()
                .filter(m -> m.getTo() != null && Arrays.asList(m.getTo()).contains(email))
                .toList();
    }

}
