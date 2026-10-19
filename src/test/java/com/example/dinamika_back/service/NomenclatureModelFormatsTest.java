package com.example.dinamika_back.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Белый список форматов 3D-моделей, подсказки для форматов САПР, заголовок GLB и поиск сцены X3D в HTML. */
class NomenclatureModelFormatsTest {

    @Test
    void detectsSupportedFormatsCaseInsensitive() {
        assertThat(NomenclatureModelFormats.formatOf("Heat gun.wrl")).isEqualTo("vrml");
        assertThat(NomenclatureModelFormats.formatOf("ДЕТАЛЬ.STP")).isEqualTo("step");
        assertThat(NomenclatureModelFormats.formatOf("part.step")).isEqualTo("step");
        assertThat(NomenclatureModelFormats.formatOf("part.IGS")).isEqualTo("iges");
        assertThat(NomenclatureModelFormats.formatOf("model.glb")).isEqualTo("glb");
        assertThat(NomenclatureModelFormats.formatOf("model.gltf")).isEqualTo("gltf");
        assertThat(NomenclatureModelFormats.formatOf("print.3mf")).isEqualTo("3mf");
        assertThat(NomenclatureModelFormats.formatOf("scan.ply")).isEqualTo("ply");
        assertThat(NomenclatureModelFormats.formatOf("1.xhtml")).isEqualTo("x3d");
        assertThat(NomenclatureModelFormats.formatOf("scene.x3d")).isEqualTo("x3d");
    }

    @Test
    void rejectsCadNativeAndUnknownFormats() {
        assertThat(NomenclatureModelFormats.formatOf("Heat gun.a3d")).isNull();
        assertThat(NomenclatureModelFormats.formatOf("Сборка.grb")).isNull();
        assertThat(NomenclatureModelFormats.formatOf("Gate-Valve-3D.dwg")).isNull();
        assertThat(NomenclatureModelFormats.formatOf("virus.exe")).isNull();
        assertThat(NomenclatureModelFormats.formatOf("no-extension")).isNull();
        assertThat(NomenclatureModelFormats.formatOf(".glb")).isNull();
        assertThat(NomenclatureModelFormats.formatOf(null)).isNull();
    }

    @Test
    void cadFormatsGetExportHints() {
        assertThat(NomenclatureModelFormats.rejectionMessage("Heat gun.a3d")).contains("КОМПАС-3D").contains("STEP");
        assertThat(NomenclatureModelFormats.rejectionMessage("Поршень.grs")).contains("T-FLEX").contains("GLB");
        assertThat(NomenclatureModelFormats.rejectionMessage("Gate-Valve-3D.dwg")).contains("nanoCAD").contains("STL");
        assertThat(NomenclatureModelFormats.rejectionMessage("model.exe")).contains(".exe").contains("STEP");
        assertThat(NomenclatureModelFormats.rejectionMessage("model")).contains("нет расширения");
    }

    @Test
    void extensionIgnoresDirectories() {
        assertThat(NomenclatureModelFormats.extension("C:\\models\\part.v2\\gear.STL")).isEqualTo("stl");
        assertThat(NomenclatureModelFormats.extension("../../etc/passwd")).isEmpty();
        assertThat(NomenclatureModelFormats.formatOf("../../evil.glb")).isEqualTo("glb");
    }

    @Test
    void safeFileNameDropsPathsAndControlChars() {
        assertThat(NomenclatureModelFormats.safeFileName("..\\..\\Windows\\win.ini")).isEqualTo("win.ini");
        assertThat(NomenclatureModelFormats.safeFileName("a\"b\nc.stp")).isEqualTo("a_b_c.stp");
        assertThat(NomenclatureModelFormats.safeFileName("   ")).isEqualTo("model");
        String longName = "x".repeat(300) + ".step";
        assertThat(NomenclatureModelFormats.safeFileName(longName)).hasSize(200).endsWith(".step");
    }

    @Test
    void acceptsValidGlbHeader() {
        byte[] glb = glb(64, 2, 0x4E4F534A);
        assertThat(NomenclatureModelFormats.isGlb(glb, glb.length)).isTrue();
    }

    @Test
    void rejectsBrokenGlbHeaders() {
        byte[] wrongVersion = glb(64, 1, 0x4E4F534A);
        assertThat(NomenclatureModelFormats.isGlb(wrongVersion, wrongVersion.length)).isFalse();

        byte[] wrongLength = glb(64, 2, 0x4E4F534A);
        assertThat(NomenclatureModelFormats.isGlb(wrongLength, wrongLength.length + 10)).isFalse();

        byte[] binFirst = glb(64, 2, 0x004E4942);
        assertThat(NomenclatureModelFormats.isGlb(binFirst, binFirst.length)).isFalse();

        byte[] html = "<html><body>glTF</body></html>".getBytes(StandardCharsets.US_ASCII);
        assertThat(NomenclatureModelFormats.isGlb(html, html.length)).isFalse();
        assertThat(NomenclatureModelFormats.isGlb(new byte[4], 4)).isFalse();
        assertThat(NomenclatureModelFormats.isGlb(null, 0)).isFalse();
    }

    @Test
    void readsGlbHeaderFromFile(@TempDir Path dir) throws IOException {
        Path good = dir.resolve("a.glb");
        Files.write(good, glb(128, 2, 0x4E4F534A));
        assertThat(NomenclatureModelFormats.isGlb(good)).isTrue();

        Path renamed = dir.resolve("b.glb");
        Files.writeString(renamed, "solid cube\nendsolid cube\n");
        assertThat(NomenclatureModelFormats.isGlb(renamed)).isFalse();
    }

    @Test
    void findsX3dSceneInHtml(@TempDir Path dir) throws IOException {
        Path freecad = dir.resolve("1.xhtml");
        // Сцена далеко от начала и на границе буфера чтения 64 КБ
        String head = "<html><head><script>var x = '<x3';</script></head>" + " ".repeat(64 * 1024 - 60);
        Files.writeString(freecad, head + "<X3D profile=\"Immersive\"><Scene></Scene></X3D></html>");
        assertThat(NomenclatureModelFormats.containsX3dScene(freecad)).isTrue();

        Path page = dir.resolve("page.html");
        Files.writeString(page, "<html><body><x3 class=\"x\">no scene</x3></body></html>");
        assertThat(NomenclatureModelFormats.containsX3dScene(page)).isFalse();
    }

    /** Минимальный GLB: заголовок 12 байт и блок длиной payload, длина в заголовке — total */
    private static byte[] glb(int total, int version, int firstChunkType) {
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'g', 'l', 'T', 'F'});
        buffer.putInt(version);
        buffer.putInt(total);
        buffer.putInt(total - 20);
        buffer.putInt(firstChunkType);
        return buffer.array();
    }
}
