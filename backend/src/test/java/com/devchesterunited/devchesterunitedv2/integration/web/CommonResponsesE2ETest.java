package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.configuration.JacksonConfig;
import com.devchesterunited.devchesterunitedv2.controller.dto.SuccessResponseDTO;
import com.devchesterunited.devchesterunitedv2.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(CommonResponsesE2ETest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({JacksonConfig.class, GlobalExceptionHandler.class, CommonResponsesE2ETest.ProbeController.class})
class CommonResponsesE2ETest {
    @Autowired MockMvc mvc;

    @Test
    void validRequestHasCommonSuccessEnvelope() throws Exception {
        mvc.perform(post("/probe").contentType("application/json").content("{\"name\":\"dato\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Solicitud procesada correctamente"))
                .andExpect(jsonPath("$.data.name").value("dato"));
    }

    @Test
    void successWithoutMessageOmitsOptionalField() throws Exception {
        mvc.perform(get("/probe/success"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").doesNotExist()).andExpect(jsonPath("$.data").value("dato"));
    }

    @ParameterizedTest
    @CsvSource({"unknown", "malformed", "invalid"})
    void rejectsInvalidJsonWithoutLeakingInput(String kind) throws Exception {
        String json = switch (kind) {
            case "unknown" -> "{\"name\":\"dato\",\"secret\":\"NO_FILTRAR\"}";
            case "invalid" -> "{\"name\":\"\"}";
            default -> "{NO_FILTRAR";
        };
        mvc.perform(post("/probe").contentType("application/json").content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("La solicitud no es válida."))
                .andExpect(content().string(not(containsString("NO_FILTRAR"))));
    }

    @ParameterizedTest
    @CsvSource({"auth,401", "conflict,409", "database,503", "unexpected,500", "explicit,400"})
    void sanitizesExceptionResponses(String failure, int code) throws Exception {
        mvc.perform(get("/probe/failure/{failure}", failure))
                .andExpect(status().is(code)).andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(content().string(not(containsString("NO_FILTRAR"))))
                .andExpect(content().string(not(containsString("SELECT"))));
    }

    @RestController
    static class ProbeController {
        record Input(@NotBlank String name) { }

        @PostMapping("/probe")
        SuccessResponseDTO<Input> success(@Valid @RequestBody Input input) {
            return new SuccessResponseDTO<>("Solicitud procesada correctamente", input);
        }

        @GetMapping("/probe/success")
        SuccessResponseDTO<String> success() { return new SuccessResponseDTO<>("dato"); }

        @GetMapping("/probe/failure/{failure}")
        void failure(@PathVariable String failure) {
            String internal = "SELECT password NO_FILTRAR";
            throw switch (failure) {
                case "auth" -> new BadCredentialsException(internal);
                case "conflict" -> new DataIntegrityViolationException(internal);
                case "database" -> new DataAccessResourceFailureException(internal);
                case "explicit" -> new ResponseStatusException(HttpStatus.BAD_REQUEST, internal);
                default -> new IllegalStateException(internal);
            };
        }
    }
}
