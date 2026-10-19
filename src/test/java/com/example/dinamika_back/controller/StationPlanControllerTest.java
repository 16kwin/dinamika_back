package com.example.dinamika_back.controller;

import com.example.dinamika_back.service.StationPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Картинка плана: браузер хранит копию, но сверяет её с сервером (no-cache + ETag), неизменная — 304 без тела. */
class StationPlanControllerTest {

    private static final String TAG = "0b1c2d3e-0000-4000-8000-000000000001";

    @TempDir
    Path dir;

    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        Path file = Files.write(dir.resolve(TAG + ".png"), new byte[]{1, 2, 3});
        StationPlanService service = mock(StationPlanService.class);
        when(service.image(7L)).thenReturn(new StationPlanService.ImageFile(file, "image/png", TAG));
        mvc = MockMvcBuilders.standaloneSetup(new StationPlanController(service)).build();
    }

    @Test
    void imageIsRevalidatedOnEveryRequest() throws Exception {
        mvc.perform(get("/api/station-plans/workshops/7/image"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, private"))
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void unchangedImageIsNotModified() throws Exception {
        mvc.perform(get("/api/station-plans/workshops/7/image").header("If-None-Match", "\"" + TAG + "\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"" + TAG + "\""))
                .andExpect(content().bytes(new byte[0]));
        mvc.perform(get("/api/station-plans/workshops/7/image").header("If-None-Match", "\"other-image\""))
                .andExpect(status().isOk());
    }
}
