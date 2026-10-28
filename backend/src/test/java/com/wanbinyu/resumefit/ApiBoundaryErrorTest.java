package com.wanbinyu.resumefit;

import com.wanbinyu.resumefit.common.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiBoundaryErrorTest {
    private MockMvc mvc;
    @RestController static class BoundaryController {
        record Body(@NotBlank String name) {}
        @PostMapping("/boundary") String body(@Valid @RequestBody Body body) { return "ok"; }
        @GetMapping("/number") String number(@RequestParam long id) { return "ok"; }
        @GetMapping("/conflict") String conflict() { throw new DuplicateKeyException("SQL private_schema secret@example.com"); }
        @GetMapping("/locked") String locked() { throw new CannotAcquireLockException("SQL private_schema"); }
    }
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new BoundaryController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void malformedJsonIsBadRequestWithoutEchoingInput() throws Exception {
        mvc.perform(post("/boundary").contentType(MediaType.APPLICATION_JSON).content("{\"name\":secret@example.com"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(not(containsString("secret@example.com"))));
    }
    @Test void invalidAndMissingTypedParametersAreBadRequest() throws Exception {
        mvc.perform(get("/number").param("id", "NaN")).andExpect(status().isBadRequest());
        mvc.perform(get("/number")).andExpect(status().isBadRequest());
    }
    @Test void beanValidationRemainsBadRequest() throws Exception {
        mvc.perform(post("/boundary").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void frameworkMethodAndMediaErrorsRetainTheirClientStatus() throws Exception {
        mvc.perform(get("/boundary")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/boundary").contentType(MediaType.TEXT_PLAIN).content("plain"))
                .andExpect(status().isUnsupportedMediaType());
    }
    @Test void databaseConflictsAre409WithoutImplementationDetails() throws Exception {
        for (String path : new String[]{"/conflict", "/locked"}) mvc.perform(get(path))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(not(containsString("SQL"))))
                .andExpect(content().string(not(containsString("private_schema"))));
    }
}
