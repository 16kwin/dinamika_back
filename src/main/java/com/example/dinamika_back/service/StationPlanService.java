package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.StationPlanDTO;
import com.example.dinamika_back.dto.StationPlanImageDTO;
import com.example.dinamika_back.dto.StationPlanPositionDTO;
import com.example.dinamika_back.dto.StationPlanPositionsRequest;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.model.StationPlan;
import com.example.dinamika_back.model.StationPlanPosition;
import com.example.dinamika_back.repository.StationPlanPositionRepository;
import com.example.dinamika_back.repository.StationPlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Интерактивная карта станций: план цеха (картинка) и расстановка станций на нём.
 *
 * <p>Расстановка сохраняется пакетом с версией плана: если план успели изменить (другой руководитель
 * сохранил расстановку или сменил картинку), ответ 409 и клиент перезагружает план. Позиции — доли 0..1
 * от размеров плана; они относятся к картинке, а без неё — к автоматической схеме цеха. Поэтому
 * при первой загрузке картинки и при её удалении расстановка сбрасывается, а при замене — остаётся.
 *
 * <p>Функция {@code map.plan}: без неё в потолке сервис не создаётся и чистка сирот при старте не выполняется (L3).
 * {@link StationPlanStorage} условия не имеет — его внедряет WorkshopPlanFilesListener (удаление цеха).
 */
@Slf4j
@ConditionalOnFeature(Feature.MAP_PLAN)
@Service
@RequiredArgsConstructor
public class StationPlanService {

    /** Больше станций в одном цехе не бывает; защита от случайно огромного запроса */
    static final int MAX_POSITIONS = 5000;
    /** Позиции вставляются пакетами JDBC такого размера, а не по запросу на каждую */
    static final int INSERT_BATCH = 500;
    /** Файл моложе может принадлежать загрузке, транзакция которой ещё идёт, — такой не считается сиротой */
    static final Duration ORPHAN_MIN_AGE = Duration.ofHours(1);

    private final StationPlanRepository planRepository;
    private final StationPlanPositionRepository positionRepository;
    private final StationPlanStorage storage;

    @PersistenceContext
    private EntityManager entityManager;

    /** Чистка файлов-сирот при старте; по умолчанию выключена — удаление безвозвратное (см. cleanupOnStartup) */
    @Value("${station-plans.orphan-cleanup:false}")
    private boolean orphanCleanup;

    /** Файл картинки плана для ответа API; tag — метка файла для ETag */
    public record ImageFile(Path path, String mime, String tag) {
    }

    // ==================== Чтение ====================

    @Transactional(readOnly = true)
    public StationPlanDTO get(Long workshopId) {
        requireWorkshop(workshopId);
        return planRepository.findByWorkshopId(workshopId)
                .map(this::toDto)
                .orElseGet(() -> new StationPlanDTO(workshopId, 0L, null, List.of()));
    }

    @Transactional(readOnly = true)
    public ImageFile image(Long workshopId) {
        StationPlan plan = planRepository.findByWorkshopId(workshopId)
                .filter(StationPlan::hasImage)
                .orElseThrow(() -> StationPlanException.notFound("План цеха не загружен"));
        Path path = storage.resolve(plan.getImagePath());
        if (!Files.isRegularFile(path)) throw StationPlanException.notFound("Файл плана не найден на сервере");
        return new ImageFile(path, plan.getImageMime(), StationPlanStorage.tag(plan.getImagePath()));
    }

    // ==================== Расстановка ====================

    /**
     * Полная замена расстановки. Позиции станций, которые удалили или перевели в другой цех, пока шла правка,
     * пропускаются (их uid — в skippedStationUids ответа): иначе такой черновик нельзя было бы сохранить.
     */
    @Transactional
    public StationPlanDTO savePositions(Long workshopId, StationPlanPositionsRequest body, String username) {
        requireWorkshop(workshopId);
        if (body == null || body.getVersion() == null) throw StationPlanException.badRequest("Не указана версия плана");
        Map<String, double[]> positions = validatePositions(body.getPositions());

        StationPlan plan = lockOrCreate(workshopId);
        if (!plan.getVersion().equals(body.getVersion())) {
            throw StationPlanException.conflict("Карту цеха уже изменил другой пользователь. Загружена актуальная расстановка — повторите правку.");
        }

        Map<String, StationPlanPositionRepository.StationRef> stations = positions.isEmpty()
                ? Map.of()
                : positionRepository.findStations(positions.keySet()).stream()
                        .collect(Collectors.toMap(StationPlanPositionRepository.StationRef::getUid, Function.identity(), (a, b) -> a));
        List<StationPlanPosition> rows = new ArrayList<>(positions.size());
        List<String> skipped = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : positions.entrySet()) {
            StationPlanPositionRepository.StationRef station = stations.get(entry.getKey());
            if (station == null || !workshopId.equals(station.getWorkshopId())) {
                skipped.add(entry.getKey());
                continue;
            }
            double[] xy = entry.getValue();
            rows.add(new StationPlanPosition(plan.getId(), station.getId(), xy[0], xy[1]));
        }
        if (!skipped.isEmpty()) {
            log.info("Расстановка цеха {}: пропущено позиций станций, удалённых или из другого цеха: {} ({})",
                    workshopId, skipped.size(), username);
        }

        positionRepository.deleteByPlan(plan.getId());
        // Позиции новые (Persistable): вставка пакетами, без SELECT по ключу на каждую
        entityManager.unwrap(Session.class).setJdbcBatchSize(INSERT_BATCH);
        positionRepository.saveAllAndFlush(rows);
        touch(plan, username);
        StationPlanDTO result = toDto(planRepository.saveAndFlush(plan));
        result.setSkippedStationUids(skipped);
        return result;
    }

    /**
     * Проверка расстановки из запроса: uid станции, координаты — конечные числа 0..1, без повторов.
     * Возвращает uid → {x, y} в порядке запроса.
     */
    static Map<String, double[]> validatePositions(List<StationPlanPositionDTO> positions) {
        if (positions == null) return Map.of();
        if (positions.size() > MAX_POSITIONS) throw StationPlanException.badRequest("Слишком много станций в запросе");
        Map<String, double[]> result = new LinkedHashMap<>();
        for (StationPlanPositionDTO position : positions) {
            if (position == null) throw StationPlanException.badRequest("Пустая позиция станции");
            String uid = position.getStationUid() == null ? "" : position.getStationUid().trim();
            if (uid.isEmpty()) throw StationPlanException.badRequest("Не указана станция");
            if (!isFraction(position.getX()) || !isFraction(position.getY())) {
                throw StationPlanException.badRequest("Координаты станции " + uid + " должны быть от 0 до 1");
            }
            if (result.putIfAbsent(uid, new double[]{position.getX(), position.getY()}) != null) {
                throw StationPlanException.badRequest("Станция " + uid + " указана дважды");
            }
        }
        return result;
    }

    static boolean isFraction(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 1;
    }

    // ==================== Картинка ====================

    /** Загрузить или заменить картинку плана. Старый файл удаляется после успешного сохранения */
    @Transactional
    public StationPlanDTO uploadImage(Long workshopId, MultipartFile file, String username) {
        requireWorkshop(workshopId);
        if (file == null || file.isEmpty()) throw StationPlanException.badRequest("Файл не выбран");
        if (file.getSize() > StationPlanImages.MAX_BYTES) throw StationPlanException.tooLarge("Файл больше 20 МБ");
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw StationPlanException.badRequest("Не удалось прочитать файл");
        }
        StationPlanImages.Info info = StationPlanImages.inspect(data);

        StationPlan plan = lockOrCreate(workshopId);
        boolean hadImage = plan.hasImage();
        String oldPath = plan.getImagePath();
        String newPath = storage.save(workshopId, data, info.format().extension());
        // Файлы — вне транзакции базы: старый удаляем после фиксации, новый — если транзакция откатилась
        afterCompletion(committed -> storage.delete(committed ? oldPath : newPath));

        plan.setImagePath(newPath);
        plan.setImageMime(info.format().mime());
        plan.setImageWidth(info.width());
        plan.setImageHeight(info.height());
        // Расстановка на автоматической схеме к картинке не подходит
        if (!hadImage) positionRepository.deleteByPlan(plan.getId());
        touch(plan, username);
        log.info("План цеха {} загружен: {}×{} {}, {} байт ({})", workshopId, info.width(), info.height(),
                info.format().mime(), data.length, username);
        return toDto(planRepository.saveAndFlush(plan));
    }

    /** Удалить картинку: карта вернётся к автоматической схеме, расстановка на картинке сбрасывается */
    @Transactional
    public StationPlanDTO deleteImage(Long workshopId, String username) {
        requireWorkshop(workshopId);
        StationPlan plan = planRepository.lockByWorkshopId(workshopId).orElse(null);
        if (plan == null || !plan.hasImage()) return get(workshopId);
        String oldPath = plan.getImagePath();
        afterCompletion(committed -> {
            if (committed) storage.delete(oldPath);
        });
        plan.setImagePath(null);
        plan.setImageMime(null);
        plan.setImageWidth(null);
        plan.setImageHeight(null);
        positionRepository.deleteByPlan(plan.getId());
        touch(plan, username);
        log.info("План цеха {} удалён ({})", workshopId, username);
        return toDto(planRepository.saveAndFlush(plan));
    }

    // ==================== Файлы-сироты ====================

    /**
     * При старте сервера: удалить файлы, на которые не ссылается ни один план. Они остаются, когда старый файл
     * не удалился при замене или сервер остановился посреди загрузки (файл цеха удаляет WorkshopPlanFilesListener).
     * Включается свойством station-plans.orphan-cleanup=true — только там, где каталог планов принадлежит одной
     * базе (прод с абсолютным station-plans.storage-dir): «сирота» для этой базы может быть файлом другой базы
     * с тем же каталогом. Ошибки только в журнал — запуск сервера важнее.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStartup() {
        if (!orphanCleanup) return;
        try {
            removeOrphanFiles(Instant.now().minus(ORPHAN_MIN_AGE));
        } catch (RuntimeException e) {
            log.warn("Не удалось проверить файлы планов цехов в {}: {}", storage.root(), e.getMessage());
        }
    }

    /**
     * Удалить файлы-сироты, изменённые раньше olderThan; возвращает число удалённых файлов. Если каталог
     * и база не сходятся (база ссылается на отсутствующие файлы или ни на один файл), не удаляет ничего.
     */
    int removeOrphanFiles(Instant olderThan) {
        Set<String> referenced = new HashSet<>(planRepository.findImagePaths());
        long missing = referenced.stream().filter(path -> !storage.exists(path)).count();
        if (missing > 0) {
            // Обычно значит, что сервер запущен не из того каталога, а путь хранилища относительный
            log.warn("Нет файлов планов цехов: {} из {} в каталоге {} — чистка пропущена. Проверьте рабочий каталог "
                    + "сервера или задайте абсолютный station-plans.storage-dir", missing, referenced.size(), storage.root());
            return 0;
        }
        List<StationPlanStorage.StoredFile> files = storage.list();
        if (files.isEmpty()) return 0;
        // База не ссылается ни на один файл (новая или другая база, цеха в ней есть всегда — сиды V1):
        // каталог, скорее всего, не её — файлы не трогаем
        if (referenced.isEmpty()) {
            log.warn("В базе нет картинок планов, а в каталоге планов {} есть файлы ({}) — чистка пропущена", storage.root(), files.size());
            return 0;
        }
        int removed = 0;
        for (StationPlanStorage.StoredFile file : files) {
            if (referenced.contains(file.path()) || !file.modifiedAt().isBefore(olderThan)) continue;
            if (storage.delete(file.path())) removed++;
        }
        // Каталоги удалённых цехов: в каталог существующего цеха может идти загрузка
        for (Long workshopId : storage.workshopFolders()) {
            if (!planRepository.workshopExists(workshopId)) storage.deleteFolderIfEmpty(workshopId);
        }
        if (removed > 0) log.info("Удалено файлов-сирот планов цехов: {} ({})", removed, storage.root());
        return removed;
    }

    // ==================== Служебное ====================

    private void requireWorkshop(Long workshopId) {
        if (workshopId == null || !planRepository.workshopExists(workshopId)) {
            throw StationPlanException.notFound("Цех не найден");
        }
    }

    /** План цеха с блокировкой строки; если записи нет — создаётся пустой план версии 0 */
    private StationPlan lockOrCreate(Long workshopId) {
        return planRepository.lockByWorkshopId(workshopId).orElseGet(() -> {
            StationPlan plan = new StationPlan();
            plan.setWorkshopId(workshopId);
            plan.setVersion(0L);
            try {
                return planRepository.saveAndFlush(plan);
            } catch (DataIntegrityViolationException e) {
                // План этого цеха только что создал другой запрос
                throw StationPlanException.conflict("Карту цеха одновременно изменил другой пользователь. Обновите карту и повторите.");
            }
        });
    }

    private static void touch(StationPlan plan, String username) {
        plan.setVersion(plan.getVersion() + 1);
        plan.setUpdatedBy(username);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    private static void afterCompletion(Consumer<Boolean> action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.accept(true);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status == STATUS_COMMITTED);
            }
        });
    }

    private StationPlanDTO toDto(StationPlan plan) {
        // ?v= — метка файла, а не версия плана: сохранение расстановки не меняет адрес картинки
        StationPlanImageDTO image = plan.hasImage()
                ? new StationPlanImageDTO(
                        "/api/station-plans/workshops/" + plan.getWorkshopId() + "/image?v=" + StationPlanStorage.tag(plan.getImagePath()),
                        plan.getImageWidth(), plan.getImageHeight(), plan.getImageMime(), plan.getUpdatedAt())
                : null;
        List<StationPlanPositionDTO> positions = plan.getId() == null
                ? List.of()
                : positionRepository.findRows(plan.getId(), plan.getWorkshopId()).stream()
                        .map(row -> new StationPlanPositionDTO(row.getStationUid(), row.getX(), row.getY()))
                        .toList();
        return new StationPlanDTO(plan.getWorkshopId(), plan.getVersion(), image, positions);
    }
}
