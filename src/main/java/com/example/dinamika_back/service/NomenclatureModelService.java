package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.MaterialModelDTO;
import com.example.dinamika_back.model.RegEventLog;
import com.example.dinamika_back.model.SprMaterial;
import com.example.dinamika_back.model.SprMaterialModel;
import com.example.dinamika_back.repository.RegEventLogRepository;
import com.example.dinamika_back.repository.SprMaterialModelRepository;
import com.example.dinamika_back.repository.SprMaterialRepository;
import com.example.dinamika_back.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 3D-модели номенклатуры: исходный файл из САПР и копия GLB для просмотра, которую готовит браузер при загрузке.
 *
 * <p>Файлы лежат в каталоге nomenclature.models-dir (не в публичном uploads/): {materialUid}/{uid}.{расширение}
 * и {materialUid}/{uid}.view.glb. Пути в БД — относительно этого каталога; выход за его пределы запрещён.
 */
@Slf4j
@Service
public class NomenclatureModelService {

    private static final String EVENT_FIELD = "3D-модель";

    private final SprMaterialModelRepository modelRepository;
    private final SprMaterialRepository materialRepository;
    private final RegEventLogRepository eventLogRepository;
    private final UserRepository userRepository;
    private final Path root;

    public NomenclatureModelService(
            SprMaterialModelRepository modelRepository,
            SprMaterialRepository materialRepository,
            RegEventLogRepository eventLogRepository,
            UserRepository userRepository,
            @Value("${nomenclature.models-dir:data/nomenclature-models}") String modelsDir) {
        this.modelRepository = modelRepository;
        this.materialRepository = materialRepository;
        this.eventLogRepository = eventLogRepository;
        this.userRepository = userRepository;
        this.root = StorageDirs.resolve(modelsDir, "3D-моделей номенклатуры");
    }

    public List<MaterialModelDTO> list(UUID materialUid) {
        return modelRepository.findByMaterialUidOrderByCreatedAtAsc(materialUid).stream().map(this::toDto).toList();
    }

    /**
     * Сохранить модель: file — исходник из САПР, view — копия GLB для просмотра (необязательна),
     * triangles — число треугольников, посчитанное браузером при подготовке копии.
     */
    @Transactional
    public MaterialModelDTO upload(UUID materialUid, MultipartFile file, MultipartFile view, Long triangles, String author) {
        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> NomenclatureModelException.notFound(
                        "Номенклатура не найдена — сначала запишите её, затем добавляйте 3D-модель"));
        if (file == null || file.isEmpty()) throw NomenclatureModelException.badRequest("Файл модели не передан или пуст");

        String originalName = NomenclatureModelFormats.safeFileName(file.getOriginalFilename());
        String format = NomenclatureModelFormats.formatOf(originalName);
        if (format == null) throw NomenclatureModelException.unsupported(NomenclatureModelFormats.rejectionMessage(originalName));
        checkSize(file, "Файл модели");
        boolean hasView = view != null && !view.isEmpty();
        if (hasView) checkSize(view, "Копия для просмотра");
        if (triangles != null && triangles < 0) throw NomenclatureModelException.badRequest("Число треугольников не может быть отрицательным");

        UUID uid = UUID.randomUUID();
        String sourceRel = materialUid + "/" + uid + "." + NomenclatureModelFormats.extension(originalName);
        String viewRel = materialUid + "/" + uid + ".view.glb";
        Path source = resolveInside(root, sourceRel);
        Path viewPath = resolveInside(root, viewRel);
        List<Path> written = new ArrayList<>();
        try {
            store(file, source);
            written.add(source);
            checkContent(format, originalName, source);
            if (hasView) {
                store(view, viewPath);
                written.add(viewPath);
                if (!NomenclatureModelFormats.isGlb(viewPath)) {
                    throw NomenclatureModelException.badRequest("Копия для просмотра повреждена: это не файл GLB 2.0");
                }
                String problem = NomenclatureModelGltf.glbProblem(viewPath);
                if (problem != null) throw NomenclatureModelException.badRequest("Копия для просмотра не принята: " + problem);
            }

            SprMaterialModel model = new SprMaterialModel();
            model.setUid(uid);
            model.setMaterialUid(materialUid);
            model.setFilePath(sourceRel);
            model.setOriginalName(originalName);
            model.setFormat(format);
            model.setFileSize(file.getSize());
            model.setViewPath(hasView ? viewRel : null);
            model.setViewSize(hasView ? view.getSize() : null);
            model.setTriangles(triangles);
            model.setAuthor(author);
            model.setCreatedAt(LocalDateTime.now());
            modelRepository.save(model);
            logEvent(material, "ADD", "Добавлена 3D-модель '" + originalName + "'", null, originalName, author);
            // Откат транзакции после выхода из метода тоже не должен оставлять файлы-сироты
            onRollback(() -> written.forEach(NomenclatureModelService::deleteQuietly));
            return toDto(model);
        } catch (IOException e) {
            written.forEach(NomenclatureModelService::deleteQuietly);
            log.error("Не удалось сохранить 3D-модель {} номенклатуры {}", originalName, materialUid, e);
            throw NomenclatureModelException.storage("Не удалось сохранить файл модели на сервере");
        } catch (RuntimeException e) {
            written.forEach(NomenclatureModelService::deleteQuietly);
            throw e;
        }
    }

    @Transactional
    public void delete(UUID uid, String author) {
        SprMaterialModel model = find(uid);
        modelRepository.delete(model);
        materialRepository.findById(model.getMaterialUid()).ifPresent(material -> logEvent(material, "DELETE",
                "Удалена 3D-модель '" + model.getOriginalName() + "'", model.getOriginalName(), null, author));
        // Файлы удаляются после фиксации: если удаление строки откатится, модель останется рабочей
        afterCommit(() -> {
            deleteQuietly(resolveQuietly(model.getFilePath()));
            deleteQuietly(resolveQuietly(model.getViewPath()));
        });
    }

    /** Все модели материала — при удалении номенклатуры (NomenclatureService.deleteAllMaterialMedia) */
    @Transactional
    public void deleteAllForMaterial(UUID materialUid) {
        modelRepository.deleteByMaterialUid(materialUid);
        afterCommit(() -> deleteDirectory(resolveQuietly(materialUid.toString())));
    }

    /** Файл для просмотра: копия GLB, а если её нет — исходник */
    public ModelFile viewFile(UUID uid) {
        SprMaterialModel model = find(uid);
        if (model.getViewPath() != null) {
            Path view = existing(model.getViewPath());
            if (view != null) return new ModelFile(view, baseName(model.getOriginalName()) + ".glb", true);
            log.warn("Копия GLB 3D-модели {} отсутствует на диске, отдаётся исходник", uid);
        }
        return sourceFile(model);
    }

    /** Исходный файл модели для скачивания под исходным именем */
    public ModelFile sourceFile(UUID uid) {
        return sourceFile(find(uid));
    }

    private ModelFile sourceFile(SprMaterialModel model) {
        Path source = existing(model.getFilePath());
        if (source == null) throw NomenclatureModelException.notFound("Файл 3D-модели отсутствует на сервере");
        return new ModelFile(source, model.getOriginalName(), false);
    }

    /** Найденный на диске файл модели, имя для скачивания и признак копии GLB */
    public record ModelFile(Path path, String fileName, boolean glbView) {
    }

    /**
     * Путь внутри каталога моделей. Относительный путь из БД не должен выводить за пределы каталога
     * («..», абсолютные пути) — иначе через API можно было бы читать или удалять чужие файлы.
     */
    static Path resolveInside(Path root, String relative) {
        if (relative == null || relative.isBlank()) throw NomenclatureModelException.badRequest("Пустой путь файла модели");
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate = Paths.get(relative);
        if (candidate.isAbsolute()) throw NomenclatureModelException.badRequest("Недопустимый путь файла модели");
        Path resolved = normalizedRoot.resolve(candidate).normalize();
        if (!resolved.startsWith(normalizedRoot) || resolved.equals(normalizedRoot)) {
            throw NomenclatureModelException.badRequest("Недопустимый путь файла модели");
        }
        return resolved;
    }

    /**
     * Автор события — по входу, а не по данным клиента: «Фамилия Имя» из карточки пользователя (как раньше
     * подписывал фронтенд), если она пуста — логин
     */
    public String authorName(String username) {
        if (username == null || username.isBlank()) return "Система";
        String name = userRepository.findTop1ByUsername(username)
                .map(user -> (nullToEmpty(user.getLastName()) + " " + nullToEmpty(user.getFirstName())).trim())
                .filter(fullName -> !fullName.isEmpty())
                .orElse(username);
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    // ==================== Вспомогательное ====================

    private SprMaterialModel find(UUID uid) {
        return modelRepository.findById(uid)
                .orElseThrow(() -> NomenclatureModelException.notFound("3D-модель не найдена — возможно, её уже удалили"));
    }

    private void checkSize(MultipartFile file, String what) {
        if (file.getSize() > NomenclatureModelFormats.MAX_FILE_SIZE) {
            throw NomenclatureModelException.tooLarge(what + " больше 300 МБ — выгрузите из САПР облегчённую модель");
        }
    }

    /**
     * Проверки содержимого там, где расширение легко подделать: GLB по заголовку, HTML — по наличию сцены X3D.
     * В GLB и glTF не должно быть ссылок на внешние файлы — их загружал бы браузер каждого, кто откроет модель.
     */
    private static void checkContent(String format, String originalName, Path source) throws IOException {
        String ext = NomenclatureModelFormats.extension(originalName);
        if ("glb".equals(ext) && !NomenclatureModelFormats.isGlb(source)) {
            throw NomenclatureModelException.badRequest("Файл повреждён или это не GLB 2.0: нет заголовка glTF");
        }
        if ("glb".equals(format) || "gltf".equals(format)) {
            // three.js читает файл как GLB по заголовку, а не по расширению: .gltf с заголовком GLB — тоже GLB
            String problem = NomenclatureModelFormats.isGlb(source)
                    ? NomenclatureModelGltf.glbProblem(source)
                    : NomenclatureModelGltf.gltfProblem(source);
            if (problem != null) throw NomenclatureModelException.badRequest("Модель не принята: " + problem);
        }
        if ("x3d".equals(format) && !"x3d".equals(ext) && !NomenclatureModelFormats.containsX3dScene(source)) {
            throw NomenclatureModelException.unsupported("В HTML-файле нет сцены X3D — загрузите саму модель (STEP, GLB, X3D…)");
        }
    }

    /** Запись через временный файл: при обрыве загрузки на диске не остаётся половины модели */
    private static void store(MultipartFile file, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            file.transferTo(part);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private Path existing(String relative) {
        Path path = resolveQuietly(relative);
        return path != null && Files.isRegularFile(path) ? path : null;
    }

    private Path resolveQuietly(String relative) {
        if (relative == null) return null;
        try {
            return resolveInside(root, relative);
        } catch (NomenclatureModelException e) {
            log.warn("Путь 3D-модели '{}' вне каталога моделей — пропущен", relative);
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Не удалось удалить файл 3D-модели {}: {}", path, e.getMessage());
        }
    }

    private static void deleteDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(NomenclatureModelService::deleteQuietly);
        } catch (IOException e) {
            log.warn("Не удалось удалить каталог 3D-моделей {}: {}", dir, e.getMessage());
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) action.run();
            }
        });
    }

    private void logEvent(SprMaterial material, String type, String description, String oldValue, String newValue, String author) {
        eventLogRepository.save(RegEventLog.builder()
                .uid(UUID.randomUUID())
                .material(material)
                .eventType(type)
                .eventDescription(description)
                .fieldName(EVENT_FIELD)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private MaterialModelDTO toDto(SprMaterialModel model) {
        return new MaterialModelDTO(model.getUid(), model.getMaterialUid(), model.getOriginalName(), model.getFormat(),
                model.getFileSize(), model.getViewPath() != null, model.getViewSize(), model.getTriangles(),
                model.getAuthor(), model.getCreatedAt());
    }
}
