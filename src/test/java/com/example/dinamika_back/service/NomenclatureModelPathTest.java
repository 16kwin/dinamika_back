package com.example.dinamika_back.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Пути файлов 3D-моделей не выходят за каталог nomenclature.models-dir. */
class NomenclatureModelPathTest {

    @TempDir
    Path root;

    @Test
    void resolvesModelPathsInsideRoot() {
        UUID material = UUID.randomUUID();
        UUID model = UUID.randomUUID();
        Path source = NomenclatureModelService.resolveInside(root, material + "/" + model + ".stp");
        Path view = NomenclatureModelService.resolveInside(root, material + "/" + model + ".view.glb");

        assertThat(source.startsWith(root.toAbsolutePath().normalize())).isTrue();
        assertThat(source.getFileName().toString()).isEqualTo(model + ".stp");
        assertThat(view.getParent()).isEqualTo(source.getParent());
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() {
        assertThatThrownBy(() -> NomenclatureModelService.resolveInside(root, "../secret.txt"))
                .isInstanceOf(NomenclatureModelException.class);
        assertThatThrownBy(() -> NomenclatureModelService.resolveInside(root, "a/../../secret.txt"))
                .isInstanceOf(NomenclatureModelException.class);
        assertThatThrownBy(() -> NomenclatureModelService.resolveInside(root, root.resolve("x.glb").toAbsolutePath().toString()))
                .isInstanceOf(NomenclatureModelException.class);
        assertThatThrownBy(() -> NomenclatureModelService.resolveInside(root, "."))
                .isInstanceOf(NomenclatureModelException.class);
        assertThatThrownBy(() -> NomenclatureModelService.resolveInside(root, " "))
                .isInstanceOf(NomenclatureModelException.class);
        assertThatThrownBy(() -> NomenclatureModelService.resolveInside(root, null))
                .isInstanceOf(NomenclatureModelException.class);
    }

    @Test
    void normalizesHarmlessDotSegments() {
        Path path = NomenclatureModelService.resolveInside(root, "a/./b/../c.glb");
        assertThat(path).isEqualTo(root.toAbsolutePath().normalize().resolve("a").resolve("c.glb"));
    }
}
