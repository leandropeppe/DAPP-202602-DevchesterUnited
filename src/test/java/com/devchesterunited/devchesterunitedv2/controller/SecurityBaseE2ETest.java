package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.configuration.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.SecurityErrorHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SecurityBaseE2ETest.Probe.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, SecurityBaseE2ETest.Probe.class})
class SecurityBaseE2ETest {
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/register", "/api/v1/auth/login"})
    void publicPostsNeedNeitherAuthenticationNorCsrf(String path) throws Exception {
        var result = mvc.perform(post(path)).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/players", "/api/v1/auth/apikey", "/login", "/api/v1/auth/register"})
    void otherRequestsAreClosedWithoutSessionOrRedirect(String path) throws Exception {
        var result = mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":\"error\",\"message\":\"No autorizado.\"}", true))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().doesNotExist("Location")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void authenticatedRequestIsStillDeniedAndDoesNotPersist() throws Exception {
        mvc.perform(get("/api/v1/players").with(user("ficticio")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"status\":\"error\",\"message\":\"Acceso denegado.\"}", true))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/v1/players")).andExpect(status().isUnauthorized());
    }

    @Test
    void unimplementedCredentialsAndCookiesDoNotAuthorize() throws Exception {
        mvc.perform(get("/api/v1/players").header("Authorization", "Bearer ficticio")
                        .header("X-API-KEY", "ficticia").cookie(new Cookie("JSESSIONID", "ficticia")))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @RestController
    static class Probe {
        @PostMapping({"/api/v1/auth/register", "/api/v1/auth/login"})
        String publicEndpoint() { return "Prueba de acceso"; }
    }
}
