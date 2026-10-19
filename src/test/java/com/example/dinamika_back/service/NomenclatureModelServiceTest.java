package com.example.dinamika_back.service;

import com.example.dinamika_back.model.SprMaterial;
import com.example.dinamika_back.model.User;
import com.example.dinamika_back.repository.RegEventLogRepository;
import com.example.dinamika_back.repository.SprMaterialModelRepository;
import com.example.dinamika_back.repository.SprMaterialRepository;
import com.example.dinamika_back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Загрузка 3D-моделей: отказ для GLB/glTF со ссылками наружу и автор событий из входа. */
class NomenclatureModelServiceTest {

    @TempDir
    Path root;

    private final SprMaterialModelRepository modelRepository = mock(SprMaterialModelRepository.class);
    private final SprMaterialRepository materialRepository = mock(SprMaterialRepository.class);
    private final RegEventLogRepository eventLogRepository = mock(RegEventLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UUID materialUid = UUID.randomUUID();
    private NomenclatureModelService service;

    @BeforeEach
    void setUp() {
        service = new NomenclatureModelService(modelRepository, materialRepository, eventLogRepository, userRepository,
                root.toString());
        SprMaterial material = new SprMaterial();
        material.setUid(materialUid);
        when(materialRepository.findById(materialUid)).thenReturn(Optional.of(material));
    }

    @Test
    void acceptsSelfContainedGlb() {
        byte[] glb = NomenclatureModelGltfTest.glbWithJson("{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":0}]}");
        service.upload(materialUid, file("part.glb", glb), file("part.view.glb", glb), 12L, "Иванов Иван");
        verify(modelRepository).save(any());
    }

    @Test
    void rejectsGlbSourceWithExternalImage() throws IOException {
        byte[] glb = NomenclatureModelGltfTest.glbWithJson("{\"images\":[{\"uri\":\"http://192.168.1.50/plc/cmd?stop=1\"}]}");
        assertThatThrownBy(() -> service.upload(materialUid, file("part.glb", glb), null, null, "Иванов Иван"))
                .isInstanceOf(NomenclatureModelException.class)
                .hasMessageStartingWith("Модель не принята")
                .hasMessageContaining("http://192.168.1.50/plc/cmd?stop=1")
                .satisfies(e -> assertThat(((NomenclatureModelException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(modelRepository, never()).save(any());
        assertNoFilesLeft();
    }

    @Test
    void rejectsViewCopyWithExternalBuffer() throws IOException {
        byte[] source = "solid cube\nendsolid cube\n".getBytes(StandardCharsets.US_ASCII);
        byte[] view = NomenclatureModelGltfTest.glbWithJson("{\"buffers\":[{\"uri\":\"https://attacker/beacon.bin\"}]}");
        assertThatThrownBy(() -> service.upload(materialUid, file("cube.stl", source), file("cube.view.glb", view), null, "x"))
                .isInstanceOf(NomenclatureModelException.class)
                .hasMessageStartingWith("Копия для просмотра не принята")
                .hasMessageContaining("attacker");
        assertNoFilesLeft();
    }

    @Test
    void rejectsBuffersOrImagesWrittenAsObject() throws IOException {
        // Объект вместо массива: three.js всё равно загрузил бы ссылку из uri
        byte[] source = "{\"asset\":{\"version\":\"2.0\"},\"images\":{\"0\":{\"uri\":\"http://192.168.1.50/plc/cmd?stop=1\"}}}"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.upload(materialUid, file("scene.gltf", source), null, null, "x"))
                .isInstanceOf(NomenclatureModelException.class)
                .hasMessageStartingWith("Модель не принята")
                .hasMessageContaining("images")
                .satisfies(e -> assertThat(((NomenclatureModelException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST));

        byte[] stl = "solid cube\nendsolid cube\n".getBytes(StandardCharsets.US_ASCII);
        byte[] view = NomenclatureModelGltfTest.glbWithJson(
                "{\"buffers\":{\"0\":{\"uri\":\"https://attacker.example/beacon\",\"byteLength\":36}}}");
        assertThatThrownBy(() -> service.upload(materialUid, file("cube.stl", stl), file("cube.view.glb", view), null, "x"))
                .isInstanceOf(NomenclatureModelException.class)
                .hasMessageStartingWith("Копия для просмотра не принята")
                .hasMessageContaining("buffers")
                .satisfies(e -> assertThat(((NomenclatureModelException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(modelRepository, never()).save(any());
        assertNoFilesLeft();
    }

    @Test
    void checksTextGltfAndGlbRenamedToGltf() {
        byte[] text = "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"uri\":\"scene.bin\",\"byteLength\":4}]}"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.upload(materialUid, file("scene.gltf", text), null, null, "x"))
                .isInstanceOf(NomenclatureModelException.class)
                .hasMessageContaining("scene.bin");

        byte[] binary = NomenclatureModelGltfTest.glbWithJson("{\"images\":[{\"uri\":\"https://evil\"}]}");
        assertThatThrownBy(() -> service.upload(materialUid, file("renamed.gltf", binary), null, null, "x"))
                .isInstanceOf(NomenclatureModelException.class)
                .hasMessageContaining("https://evil");

        byte[] embedded = "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"uri\":\"data:application/octet-stream;base64,AAAA\",\"byteLength\":3}]}"
                .getBytes(StandardCharsets.UTF_8);
        service.upload(materialUid, file("embedded.gltf", embedded), null, null, "x");
        verify(modelRepository).save(any());
    }

    @Test
    void authorComesFromUserCard() {
        User user = new User();
        user.setUsername("ivanov");
        user.setLastName(" Иванов ");
        user.setFirstName("Иван");
        user.setMiddleName("Иванович");
        when(userRepository.findTop1ByUsername("ivanov")).thenReturn(Optional.of(user));
        assertThat(service.authorName("ivanov")).isEqualTo("Иванов Иван");

        User noName = new User();
        noName.setUsername("operator1");
        when(userRepository.findTop1ByUsername("operator1")).thenReturn(Optional.of(noName));
        assertThat(service.authorName("operator1")).isEqualTo("operator1");

        when(userRepository.findTop1ByUsername("ghost")).thenReturn(Optional.empty());
        assertThat(service.authorName("ghost")).isEqualTo("ghost");
        assertThat(service.authorName(null)).isEqualTo("Система");
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private void assertNoFilesLeft() throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }
}
