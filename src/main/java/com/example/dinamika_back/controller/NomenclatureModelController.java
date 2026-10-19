package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.MaterialModelDTO;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.service.NomenclatureModelException;
import com.example.dinamika_back.service.NomenclatureModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 3D-модели номенклатуры: окно «3D» в блоке «Изображение» вкладки «Характеристики». Только после входа.
 *
 * <p>Адреса одной модели — /models/3d/{uid}: путь DELETE /api/nomenclature/models/{uid} уже занят
 * удалением моделей брендов в {@link NomenclatureController}.
 *
 * <p>Без функции {@code model3d} в потолке контроллер не создаётся (L3). {@link NomenclatureModelService}
 * остаётся: удаление позиции номенклатуры удаляет и её 3D-файлы.
 */
@ConditionalOnFeature(Feature.MODEL3D)
@RestController
@RequestMapping("/api/nomenclature")
@RequiredArgsConstructor
public class NomenclatureModelController {

    private static final MediaType GLB = MediaType.parseMediaType("model/gltf-binary");

    private final NomenclatureModelService modelService;

    @GetMapping("/{materialUid}/models")
    public List<MaterialModelDTO> list(@PathVariable UUID materialUid) {
        return modelService.list(materialUid);
    }

    /**
     * multipart: file — исходник, view — копия GLB (необязательно), triangles.
     * Поле author от старых клиентов принимается, но не используется: автор — только из входа.
     */
    @PostMapping(value = "/{materialUid}/models", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialModelDTO upload(
            @PathVariable UUID materialUid,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "view", required = false) MultipartFile view,
            @RequestParam(value = "triangles", required = false) Long triangles,
            Authentication authentication) {
        return modelService.upload(materialUid, file, view, triangles, author(authentication));
    }

    /** Параметр ?author= от старых клиентов не используется: автор — только из входа */
    @DeleteMapping("/models/3d/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid, Authentication authentication) {
        modelService.delete(uid, author(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * Модель для просмотра: копия GLB, а если её нет — исходник. Браузер может хранить ответ, но перед каждым
     * показом сверяется с сервером (no-cache + ETag, ответ 304 без тела): после выхода на общем терминале
     * модель не отдаётся из кэша без проверки входа
     */
    @GetMapping("/models/3d/{uid}/view")
    public ResponseEntity<Resource> view(@PathVariable UUID uid) {
        NomenclatureModelService.ModelFile file = modelService.viewFile(uid);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Model-View", file.glbView() ? "glb" : "source");
        // 304 на If-None-Match Spring отвечает сам (HttpEntityMethodProcessor), Range по-прежнему поддерживается
        String etag = etag(uid, file);
        if (etag != null) response.eTag(etag);
        if (file.glbView()) {
            response.contentType(GLB);
        } else {
            // Исходник (в том числе HTML со сценой X3D) браузер не должен открывать как страницу
            response.contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString());
        }
        return response.body(new FileSystemResource(file.path()));
    }

    /** Скачать исходный файл модели под исходным именем */
    @GetMapping("/models/3d/{uid}/source")
    public ResponseEntity<Resource> source(@PathVariable UUID uid) {
        NomenclatureModelService.ModelFile file = modelService.sourceFile(uid);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }

    /** Автор события — только из входа (ФИО из карточки пользователя, иначе логин), не из параметров запроса */
    private String author(Authentication authentication) {
        return modelService.authorName(authentication != null ? authentication.getName() : null);
    }

    /** Версия отдаваемого файла: какой файл (копия или исходник), размер и время записи. null — без ETag */
    private static String etag(UUID uid, NomenclatureModelService.ModelFile file) {
        try {
            return "\"" + uid + "-" + (file.glbView() ? "glb" : "source") + "-" + Files.size(file.path())
                    + "-" + Files.getLastModifiedTime(file.path()).toMillis() + "\"";
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== Ошибки → { "error": "..." } ====================

    @ExceptionHandler(NomenclatureModelException.class)
    public ResponseEntity<Map<String, String>> handleModel(NomenclatureModelException e) {
        return ResponseEntity.status(e.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> handleMissing(Exception e) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "Файл модели не передан"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "Некорректное значение параметра " + e.getName()));
    }
}
