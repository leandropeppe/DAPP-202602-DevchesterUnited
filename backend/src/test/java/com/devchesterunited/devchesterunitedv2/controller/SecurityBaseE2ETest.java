package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityErrorHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.assertThat;
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
    @ValueSource(strings = {"/api/v1/auth/apikey", "/login", "/api/v1/auth/register"})
    void otherRequestsAreClosedWithoutSessionOrRedirect(String path) throws Exception {
        var result = mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"status\":\"error\",\"message\":\"No autorizado.\"}", true))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().doesNotExist("Location")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @RestController
    static class Probe {
        @PostMapping({"/api/v1/auth/register", "/api/v1/auth/login"})
        String publicEndpoint() { return "Prueba de acceso"; }
    }
}
