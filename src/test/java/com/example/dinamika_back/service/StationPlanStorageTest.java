package com.example.dinamika_back.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Файлы планов: путь {workshopId}/{uuid}.{ext} внутри каталога, выход за каталог невозможен, перечень для чистки сирот. */
class StationPlanStorageTest {

    @TempDir
    Path dir;

    @Test
    void savesInsideWorkshopFolderAndDeletes() throws Exception {
        StationPlanStorage storage = new StationPlanStorage(dir.toString());
        String relative = storage.save(7, new byte[]{1, 2, 3}, "png");
        assertThat(relative).matches("7/[0-9a-f-]{36}\\.png");
        Path file = storage.resolve(relative);
        assertThat(file).startsWith(dir.toAbsolutePath().normalize());
        assertThat(Files.readAllBytes(file)).containsExactly(1, 2, 3);
        storage.delete(relative);
        assertThat(file).doesNotExist();
    }

    @Test
    void rejectsPathsOutsideStorage() {
        StationPlanStorage storage = new StationPlanStorage(dir.toString());
        String[] bad = {"../secret.png", "7/../../x.png", "/etc/passwd", "7\\..\\x.png", "C:/Windows/win.ini",
                "7/00000000-0000-0000-0000-000000000000.svg", "", null};
        for (String path : bad) {
            assertThatThrownBy(() -> storage.resolve(path)).isInstanceOf(StationPlanException.class);
            assertThat(storage.exists(path)).isFalse();
        }
    }

    @Test
    void tagIsUuidFromFileName() {
        assertThat(StationPlanStorage.tag("7/0b1c2d3e-0000-4000-8000-000000000001.webp")).isEqualTo("0b1c2d3e-0000-4000-8000-000000000001");
    }

    @Test
    void listsOnlyPlanFilesAndKeepsNonEmptyFolders() throws Exception {
        StationPlanStorage storage = new StationPlanStorage(dir.toString());
        assertThat(storage.list()).isEmpty();
        String a = storage.save(7, new byte[]{1}, "png");
        String b = storage.save(12, new byte[]{2}, "jpg");
        Files.writeString(dir.resolve("7").resolve("notes.txt"), "чужой файл");
        Files.createDirectories(dir.resolve("other"));

        assertThat(storage.list()).extracting(StationPlanStorage.StoredFile::path).containsExactlyInAnyOrder(a, b);
        assertThat(storage.list()).allSatisfy(file -> assertThat(file.modifiedAt()).isNotNull());
        assertThat(storage.workshopFolders()).containsExactlyInAnyOrder(7L, 12L);

        storage.deleteFolderIfEmpty(12);
        assertThat(dir.resolve("12")).exists();
        assertThat(storage.delete(b)).isTrue();
        assertThat(storage.delete(b)).isFalse();
        storage.deleteFolderIfEmpty(12);
        assertThat(dir.resolve("12")).doesNotExist();
    }

    @Test
    void relativeDirIsResolvedFromWorkingDirectory() {
        StationPlanStorage storage = new StationPlanStorage(" data/station-plans ");
        assertThat(storage.root()).isAbsolute().isEqualTo(Path.of("data/station-plans").toAbsolutePath().normalize());
    }
}
