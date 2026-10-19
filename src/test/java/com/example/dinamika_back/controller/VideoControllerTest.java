package com.example.dinamika_back.controller;

import com.example.dinamika_back.service.LiveStreamService;
import com.example.dinamika_back.service.LiveSubscription;
import com.example.dinamika_back.service.VideoArchiveService;
import com.example.dinamika_back.service.VideoCameraService;
import com.example.dinamika_back.service.VideoRecordingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.OutputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HEAD на живой поток, кадр и файлы видео — 405 без подписки, FFmpeg и чтения файла: Spring отвечает на HEAD
 * обработчиком GET, а тело HEAD отбрасывается без ошибки записи — цикл живого потока не кончался бы никогда.
 */
class VideoControllerTest {

    private VideoCameraService cameraService;
    private VideoArchiveService archiveService;
    private VideoRecordingService recordingService;
    private LiveStreamService liveService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cameraService = mock(VideoCameraService.class);
        archiveService = mock(VideoArchiveService.class);
        recordingService = mock(VideoRecordingService.class);
        liveService = mock(LiveStreamService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new VideoController(cameraService, archiveService, recordingService, liveService)).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void headOnStreamsAndFilesStartsNoWork() throws Exception {
        for (String url : List.of("/api/video/cameras/1/live", "/api/video/cameras/1/snapshot",
                "/api/video/files/5/content", "/api/video/files/5/download", "/api/video/clips/abc/download")) {
            mvc.perform(head(url))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET"));
        }
        verifyNoInteractions(liveService, cameraService, archiveService, recordingService);
    }

    @Test
    void liveGetStreamsAndReleasesSubscription() throws Exception {
        LiveSubscription subscription = mock(LiveSubscription.class);
        when(subscription.codec()).thenReturn("avc1.640028");
        when(liveService.subscribe(1L)).thenReturn(subscription);

        mvc.perform(get("/api/video/cameras/1/live"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(header().string("X-Video-Codec", "avc1.640028"));

        verify(subscription).streamTo(any(OutputStream.class));
        verify(subscription).close();
    }
}
