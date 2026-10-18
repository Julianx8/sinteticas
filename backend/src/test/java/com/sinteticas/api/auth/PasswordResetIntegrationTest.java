package com.sinteticas.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIntegrationTest extends IntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("restablecer-contrasena\\?token=([A-Za-z0-9_-]+)");

    @Test
    void userResetsPasswordWithEmailedLink() throws Exception {
        String email = uniqueEmail();
        JsonNode auth = registerCustomer(email);

        forgot(email).andExpect(status().isAccepted());

        List<SimpleMailMessage> mails = mailSender.sentTo(email);
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).getSubject()).contains("Restablece tu contraseña");
        assertThat(mails.get(0).getText()).contains("http://localhost:5173/restablecer-contrasena?token=");

        reset(tokenFrom(mails.get(0)), "ClaveNueva123").andExpect(status().isNoContent());

        login(email, "ClaveNueva123");
        // Las sesiones anteriores quedaron cerradas
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", auth.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void linkWorksOnlyOnce() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);
        forgot(email);
        String token = tokenFrom(mailSender.sentTo(email).get(0));

        reset(token, "ClaveNueva123").andExpect(status().isNoContent());
        reset(token, "OtraClave123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void linkExpiresAfterOneHour() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);
        forgot(email);
        String token = tokenFrom(mailSender.sentTo(email).get(0));

        clock.advance(Duration.ofMinutes(61));
        reset(token, "ClaveNueva123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void newRequestInvalidatesPreviousLink() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);
        forgot(email);
        forgot(email);
        List<SimpleMailMessage> mails = mailSender.sentTo(email);

        reset(tokenFrom(mails.get(0)), "ClaveNueva123").andExpect(status().isBadRequest());
        reset(tokenFrom(mails.get(1)), "ClaveNueva123").andExpect(status().isNoContent());
    }

    @Test
    void unknownEmailGetsSameAnswerAndNoEmail() throws Exception {
        String email = uniqueEmail();
        forgot(email).andExpect(status().isAccepted());
        assertThat(mailSender.sentTo(email)).isEmpty();
    }

    @Test
    void requestsArelimitedPerHour() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);
        for (int i = 0; i < 4; i++) {
            forgot(email).andExpect(status().isAccepted());
        }
        assertThat(mailSender.sentTo(email)).hasSize(3);
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "newPassword", newPassword))));
    }

    private static String tokenFrom(SimpleMailMessage mail) {
        Matcher matcher = TOKEN.matcher(mail.getText());
        assertThat(matcher.find()).as("el correo trae el enlace con el token").isTrue();
        return matcher.group(1);
    }

}
