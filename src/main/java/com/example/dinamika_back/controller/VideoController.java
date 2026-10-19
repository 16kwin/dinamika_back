package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.VideoCameraDTO;
import com.example.dinamika_back.dto.VideoCameraUpdateDTO;
import com.example.dinamika_back.dto.VideoClipDTO;
import com.example.dinamika_back.dto.VideoEventRequest;
import com.example.dinamika_back.dto.VideoLiveStatusDTO;
import com.example.dinamika_back.dto.VideoRecordDTO;
import com.example.dinamika_back.dto.VideoStatusDTO;
import com.example.dinamika_back.dto.VideoStreamCheckDTO;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.service.LiveStreamService;
import com.example.dinamika_back.service.LiveSubscription;
import com.example.dinamika_back.service.VideoAccess;
import com.example.dinamika_back.service.VideoArchiveService;
import com.example.dinamika_back.service.VideoCameraService;
import com.example.dinamika_back.service.VideoException;
import com.example.dinamika_back.service.VideoRecordingService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Видео: камеры станций, живой просмотр, запись по событиям и журнал «Журнал событий: видеоархив».
 * Требует авторизации. Живой просмотр, настройки камер и запись по кнопке — только администратору
 * и директору ({@link VideoAccess}); журнал и сохранённые записи доступны всем ролям.
 *
 * <p>Функция {@code video}: без неё в потолке контроллера и сервисов видео нет (уровень L3), а маршруты
 * {@code /api/video/**} закрывают ворота. Подфункции (живой просмотр, клипы, запись, архив, скачивание, камеры)
 * закрывают ворота по правилам реестра и сторожа в сервисах.
 */
@RestController
@ConditionalOnFeature(Feature.VIDEO)
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");

    private final VideoCameraService cameraService;
    private final VideoArchiveService archiveService;
    private final VideoRecordingService recordingService;
    private final LiveStreamService liveService;

    // ==================== Камеры ====================

    /** Камеры; stationUid — только камеры одной станции. Пароли в ссылках заменены на «***» */
    @GetMapping("/cameras")
    public List<VideoCameraDTO> cameras(@RequestParam(required = false) String stationUid) {
        return cameraService.list(stationUid);
    }

    @PutMapping("/cameras/{id}")
    public VideoCameraDTO updateCamera(@PathVariable Long id, @RequestBody VideoCameraUpdateDTO body) {
        VideoAccess.requireManager();
        return cameraService.update(id, body);
    }

    /** Один кадр JPEG с камеры — запасной вариант, когда браузер не может показать живой поток */
    @GetMapping("/cameras/{id}/snapshot")
    public ResponseEntity<byte[]> snapshot(@PathVariable Long id) {
        VideoAccess.requireManager();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(cameraService.snapshot(id));
    }

    /** Проверка потока: подключение к камере и параметры видео */
    @PostMapping("/cameras/{id}/check")
    public VideoStreamCheckDTO checkCamera(@PathVariable Long id) {
        VideoAccess.requireManager();
        return cameraService.check(id);
    }

    // ==================== Живой поток ====================

    /**
     * Живой поток камеры: фрагментированный MP4 без перекодирования, по фрагменту на кадр.
     * Ответ не заканчивается, пока зритель смотрит; браузер воспроизводит его через MediaSource.
     * Строка кодека для MediaSource — в заголовке X-Video-Codec.
     */
    @GetMapping("/cameras/{id}/live")
    public void live(@PathVariable Long id, HttpServletResponse response) throws IOException {
        VideoAccess.requireManager();
        // Подписка до записи ответа: если камера недоступна, клиент получит 503 с причиной в JSON
        try (LiveSubscription subscription = liveService.subscribe(id)) {
            response.setContentType("video/mp4");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            // nginx и подобные прокси не должны копить поток — кадры нужны браузеру сразу
            response.setHeader("X-Accel-Buffering", "no");
            if (subscription.codec() != null) response.setHeader("X-Video-Codec", subscription.codec());
            // До конца потока камеры, ухода зрителя или простоя без кадров — тогда подписка освобождается
            subscription.streamTo(response.getOutputStream());
        } catch (IOException clientGone) {
            // Зритель закрыл окно — подписка уже освобождена
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * HEAD на потоковые и файловые адреса — 405 без запуска работы. Spring отвечает на HEAD обработчиком GET,
     * а тело ответа HEAD отбрасывается без ошибки записи: живой поток держал бы подписку и FFmpeg камеры вечно
     * (вместе с потоком сервера), кадр запускал бы FFmpeg, файл записи читался бы с диска целиком впустую.
     */
    @RequestMapping(method = RequestMethod.HEAD, path = {"/cameras/{id}/live", "/cameras/{id}/snapshot",
            "/files/{id}/content", "/files/{id}/download", "/clips/{clipId}/download"})
    public ResponseEntity<Void> headNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(HttpMethod.GET).build();
    }

    /** Живые потоки: зрители, частота кадров и битрейт с камеры — для проверки и диагностики */
    @GetMapping("/live")
    public List<VideoLiveStatusDTO> liveStatus() {
        VideoAccess.requireManager();
        return liveService.status();
    }

    /** Кнопка «Записать»: запись с живого потока для сохранения на компьютер зрителя */
    @PostMapping("/cameras/{id}/clips")
    public VideoClipDTO startClip(@PathVariable Long id) {
        VideoAccess.requireManager();
        return liveService.startClip(id);
    }

    @PostMapping("/clips/{clipId}/stop")
    public VideoClipDTO stopClip(@PathVariable String clipId) {
        VideoAccess.requireManager();
        return liveService.stopClip(clipId);
    }

    @GetMapping("/clips/{clipId}/download")
    public ResponseEntity<Resource> downloadClip(@PathVariable String clipId) {
        VideoAccess.requireManager();
        LiveStreamService.ClipFile file = liveService.clipFile(clipId);
        return ResponseEntity.ok()
                .contentType(VIDEO_MP4)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }

    // ==================== События и состояние ====================

    /** Событие станции: { stationId, eventType: authority|issue, phase: start|stop } */
    @PostMapping("/events")
    public VideoRecordDTO event(@RequestBody VideoEventRequest body) {
        return recordingService.handleEvent(body);
    }

    @GetMapping("/status")
    public VideoStatusDTO status() {
        return archiveService.status();
    }

    // ==================== Журнал ====================

    @GetMapping("/records")
    public List<VideoRecordDTO> records(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String eventType) {
        return archiveService.list(parseDate("from", from), parseDate("to", to), stationId, eventType);
    }

    @GetMapping("/records/{id}")
    public VideoRecordDTO record(@PathVariable Long id) {
        return archiveService.get(id);
    }

    /** Файл для видеоплеера. Spring сам отвечает на запросы Range — плеер может перематывать */
    @GetMapping("/files/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        VideoArchiveService.ResolvedFile file = archiveService.resolveFile(id);
        return ResponseEntity.ok()
                .contentType(VIDEO_MP4)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(new FileSystemResource(file.path()));
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        VideoArchiveService.ResolvedFile file = archiveService.resolveFile(id);
        return ResponseEntity.ok()
                .contentType(VIDEO_MP4)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }

    // ==================== Ошибки → { "error": "..." } ====================

    @ExceptionHandler(VideoException.class)
    public ResponseEntity<Map<String, String>> handleVideo(VideoException e) {
        return ResponseEntity.status(e.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Некорректное тело запроса: ожидается JSON"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Некорректное значение параметра " + e.getName()));
    }

    private static LocalDate parseDate(String name, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw VideoException.badRequest("Неверный формат даты " + name + ": '" + value + "' (ожидается YYYY-MM-DD)");
        }
    }
}
