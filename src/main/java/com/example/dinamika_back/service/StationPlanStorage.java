package com.example.dinamika_back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Файлы планов цехов. Каталог — не публичный uploads/ (он открыт без входа): картинку отдаёт
 * только API карты станций. Путь файла — {workshopId}/{uuid}.{ext} относительно каталога.
 */
@Slf4j
@Component
public class StationPlanStorage {

    /** Только пути, которые создаёт сам сервис: защита от подмены пути в базе */
    private static final Pattern RELATIVE_PATH = Pattern.compile("\\d{1,19}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)");
    /** Каталог цеха; 18 цифр — чтобы id точно поместился в long */
    private static final Pattern WORKSHOP_FOLDER = Pattern.compile("\\d{1,18}");

    private final Path root;

    /** Файл плана в каталоге: путь относительно каталога и время изменения */
    public record StoredFile(String path, Instant modifiedAt) {
    }

    public StationPlanStorage(@Value("${station-plans.storage-dir:data/station-plans}") String storageDir) {
        this.root = StorageDirs.resolve(storageDir, "планов цехов");
    }

    public Path root() {
        return root;
    }

    /**
     * Метка файла плана — uuid из его имени. Каждая загрузка пишет новый файл, а записанный не меняется,
     * поэтому метка меняется только вместе с картинкой: она идёт в ?v= адреса картинки и в ETag.
     */
    public static String tag(String relative) {
        String name = relative.substring(relative.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** Записать картинку плана цеха; возвращает путь относительно каталога планов */
    public String save(long workshopId, byte[] data, String extension) {
        String relative = workshopId + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.error("Не удалось записать план цеха {}: {}", target, e.getMessage());
            throw new StationPlanException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить файл плана на сервере");
        }
        return relative;
    }

    /** Абсолютный путь к файлу плана; путь вне каталога планов — 404 */
    public Path resolve(String relative) {
        if (relative == null || !RELATIVE_PATH.matcher(relative).matches()) {
            throw StationPlanException.notFound("Файл плана не найден");
        }
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw StationPlanException.notFound("Файл плана не найден");
        return path;
    }

    /** Есть ли файл плана в каталоге */
    public boolean exists(String relative) {
        try {
            return Files.isRegularFile(resolve(relative));
        } catch (StationPlanException e) {
            return false;
        }
    }

    /** Удалить файл плана; ошибки только в журнал — запись в базе важнее. true — файл был и удалён */
    public boolean delete(String relative) {
        if (relative == null) return false;
        try {
            return Files.deleteIfExists(resolve(relative));
        } catch (IOException | StationPlanException e) {
            log.warn("Не удалось удалить файл плана {}: {}", relative, e.getMessage());
            return false;
        }
    }

    /** Все файлы планов в каталоге. Берутся только имена, которые создаёт сам сервис: чужие файлы не трогаются */
    public List<StoredFile> list() {
        List<StoredFile> result = new ArrayList<>();
        if (!Files.isDirectory(root)) return result;
        try (Stream<Path> paths = Files.walk(root, 2)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (!RELATIVE_PATH.matcher(relative).matches() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                result.add(new StoredFile(relative, Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant()));
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("Не удалось прочитать каталог планов {}: {}", root, e.getMessage());
        }
        return result;
    }

    /** id цехов, для которых есть каталог {workshopId}/ */
    public List<Long> workshopFolders() {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> WORKSHOP_FOLDER.matcher(name).matches())
                    .map(Long::valueOf)
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            log.warn("Не удалось прочитать каталог планов {}: {}", root, e.getMessage());
            return List.of();
        }
    }

    /** Удалить каталог цеха, если он пуст; с файлами — остаётся */
    public void deleteFolderIfEmpty(long workshopId) {
        try {
            Files.deleteIfExists(root.resolve(Long.toString(workshopId)));
        } catch (DirectoryNotEmptyException e) {
            // Остались свежие файлы — уберутся при следующей проверке
        } catch (IOException e) {
            log.warn("Не удалось удалить каталог плана цеха {}: {}", workshopId, e.getMessage());
        }
    }
}
