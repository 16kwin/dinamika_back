package com.example.dinamika_back.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Ссылки на внешние файлы в glTF/GLB: принимаются только встроенные данные (data:) и блок BIN. */
class NomenclatureModelGltfTest {

    private static final int JSON = 0x4E4F534A;
    private static final int BIN = 0x004E4942;

    @Test
    void acceptsEmbeddedData() throws IOException {
        // Так сохраняет GLTFExporter во фронтенде: буфер — блок BIN, картинки — через bufferView
        assertThat(problem("{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":12}],"
                + "\"images\":[{\"bufferView\":1,\"mimeType\":\"image/png\"}]}")).isNull();
        assertThat(problem("{\"buffers\":[{\"uri\":\"data:application/octet-stream;base64,AAAA\",\"byteLength\":3}],"
                + "\"images\":[{\"uri\":\"DATA:image/png;base64,iVBORw0KGgo=\"}]}")).isNull();
        // Ссылки в других местах (extras, вложенные объекты) просмотрщик не загружает
        assertThat(problem("{\"extras\":{\"uri\":\"http://example.com\",\"images\":[{\"uri\":\"x.png\"}]},"
                + "\"nodes\":[{\"extras\":{\"buffers\":[{\"uri\":\"a.bin\"}]}}]}")).isNull();
        assertThat(problem("{}")).isNull();
    }

    @Test
    void rejectsExternalAndRelativeUris() throws IOException {
        String image = problem("{\"images\":[{\"uri\":\"data:image/png;base64,AA==\"},"
                + "{\"uri\":\"http://192.168.1.50/plc/cmd?stop=1\"}]}");
        assertThat(image).contains("внешний файл").contains("http://192.168.1.50/plc/cmd?stop=1").contains("images[1].uri");

        String buffer = problem("{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"uri\":\"model.bin\",\"byteLength\":10}]}");
        assertThat(buffer).contains("«model.bin»").contains("buffers[0].uri");

        assertThat(problem("{\"images\":[{\"uri\":\"//attacker.example/beacon\"}]}")).contains("attacker");
        assertThat(problem("{\"images\":[{\"uri\":\"blob:http://host/1\"}]}")).contains("внешний файл");
        assertThat(problem("{\"images\":[{\"uri\":\"\"}]}")).contains("внешний файл");
    }

    @Test
    void cannotBeBypassedByJsonTricks() throws IOException {
        // Имя поля через \\u, повтор поля (браузер берёт последний), пробелы и управляющие символы перед адресом
        assertThat(problem("{\"images\":[{\"\\u0075ri\":\"https://evil\"}]}")).contains("https://evil");
        assertThat(problem("{\"images\":[{\"uri\":\"data:,\"}],\"images\":[{\"uri\":\"https://evil\"}]}")).contains("https://evil");
        assertThat(problem("{\"images\":[{\"uri\":\"data:,\",\"uri\":\"https://evil\"}]}")).contains("https://evil");
        assertThat(problem("{\"images\":[{\"uri\":\"" + " ".repeat(500) + "\\u0001https://evil\"}]}")).contains("https://evil");
        assertThat(problem("{\"images\":[{\"uri\":\"  data:image/png;base64,AA==\"}]}")).isNull();
        // Не строка: three.js превратил бы её в относительный адрес
        assertThat(problem("{\"buffers\":[{\"uri\":123}]}")).contains("ожидалась строка").contains("buffers[0].uri");
        assertThat(problem("{\"buffers\":[{\"uri\":null}]}")).contains("ожидалась строка");
        // BOM в начале браузер отбрасывает
        assertThat(problem("\uFEFF{\"images\":[{\"uri\":\"http://x\"}]}")).contains("http://x");
    }

    @Test
    void rejectsBuffersAndImagesWrittenAsObject(@TempDir Path dir) throws IOException {
        // three.js индексирует объект {"0": {...}} так же, как массив, и загрузил бы адрес из uri
        assertThat(problem("{\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":{\"0\":{\"uri\":\"https://attacker.example/beacon\",\"byteLength\":36}}}"))
                .contains("повреждено").contains("buffers").contains("массивом");
        assertThat(problem("{\"images\":{\"0\":{\"uri\":\"http://192.168.1.50/plc/cmd?stop=1\"}},"
                + "\"textures\":[{\"source\":0}]}")).contains("images").contains("массивом");
        // Даже без ссылок и с data: — не массив по схеме glTF 2.0 не принимается
        assertThat(problem("{\"buffers\":{\"0\":{\"uri\":\"data:,\"}}}")).contains("массивом");
        assertThat(problem("{\"images\": null}")).contains("массивом");
        assertThat(problem("{\"buffers\":\"x\"}")).contains("массивом");
        // Повтор поля: второе значение-объект тоже проверяется
        assertThat(problem("{\"images\":[],\"images\":{\"0\":{\"uri\":\"http://x\"}}}")).contains("массивом");
        // Пустой массив и пробелы перед ним — в порядке
        assertThat(problem("{\"buffers\" :  [ ],\"images\":\n[]}")).isNull();

        Path text = dir.resolve("object.gltf");
        Files.writeString(text, "{\"asset\":{\"version\":\"2.0\"},\"images\":{\"0\":{\"uri\":\"http://192.168.1.50/plc\"}}}");
        assertThat(NomenclatureModelGltf.gltfProblem(text)).contains("images").contains("массивом");

        Path binary = dir.resolve("object.glb");
        Files.write(binary, glbWithJson("{\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":{\"0\":{\"uri\":\"https://attacker.example/beacon\",\"byteLength\":4}}}"));
        assertThat(NomenclatureModelGltf.glbProblem(binary)).contains("buffers").contains("массивом");
    }

    @Test
    void rejectsBrokenJson() throws IOException {
        assertThat(problem("{\"images\":[{\"uri\":\"http://x")).contains("повреждено");
        assertThat(problem("{\"images\" [1]}")).contains("повреждено");
        assertThat(problem("{\"a\":01x}")).contains("повреждено");
        assertThat(problem("{\"a\":\"\\q\"}")).contains("повреждено");
        assertThat(problem("{\"a\":\"line\nbreak\"}")).contains("повреждено");
        assertThat(problem("glTF")).contains("повреждено");
        assertThat(problem("[".repeat(10_000))).contains("вложенность");
    }

    @Test
    void readsNumbersLiteralsAndEscapes() throws IOException {
        assertThat(problem("{\"a\":[-0.5e+10,1E-3,0,true,false,null,\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u00e9\"],"
                + "\"images\":[{\"name\":\"\\u0422\\u0435\\u0441\\u0442\",\"uri\":\"data:,x\"}]}")).isNull();
    }

    @Test
    void hugeEmbeddedBufferIsStreamed() throws IOException {
        // .gltf со встроенным буфером: строка data: длиннее предела Jackson (20 млн символов) читается потоком
        StringBuilder json = new StringBuilder(25_000_100);
        json.append("{\"buffers\":[{\"uri\":\"data:application/octet-stream;base64,");
        json.append("A".repeat(25_000_000));
        json.append("\"}],\"images\":[{\"uri\":\"https://evil\"}]}");
        assertThat(problem(json.toString())).contains("https://evil");
    }

    @Test
    void checksEveryJsonChunkOfGlb(@TempDir Path dir) throws IOException {
        Path good = dir.resolve("good.glb");
        Files.write(good, glb(chunk(JSON, "{\"buffers\":[{\"byteLength\":4}]}"), chunk(BIN, new byte[4])));
        assertThat(NomenclatureModelGltf.glbProblem(good)).isNull();
        assertThat(NomenclatureModelFormats.isGlb(good)).isTrue();

        Path external = dir.resolve("external.glb");
        Files.write(external, glb(chunk(JSON, "{\"images\":[{\"uri\":\"https://attacker/beacon\"}]}")));
        assertThat(NomenclatureModelGltf.glbProblem(external)).contains("https://attacker/beacon");

        // three.js берёт последний блок JSON — второй блок тоже проверяется
        Path second = dir.resolve("second.glb");
        Files.write(second, glb(chunk(JSON, "{}"), chunk(BIN, new byte[8]),
                chunk(JSON, "{\"images\":[{\"uri\":\"http://10.0.0.1/\"}]}")));
        assertThat(NomenclatureModelFormats.isGlb(second)).isTrue();
        assertThat(NomenclatureModelGltf.glbProblem(second)).contains("http://10.0.0.1/");

        // Блок JSON читается строго в своих границах: ссылка в BIN не мешает
        Path binText = dir.resolve("bin.glb");
        Files.write(binText, glb(chunk(JSON, "{}"),
                chunk(BIN, "{\"images\":[{\"uri\":\"http://x\"}]}".getBytes(StandardCharsets.UTF_8))));
        assertThat(NomenclatureModelGltf.glbProblem(binText)).isNull();
    }

    @Test
    void rejectsTruncatedGlbJsonChunk(@TempDir Path dir) throws IOException {
        byte[] bytes = glb(chunk(JSON, "{\"asset\":{}}"));
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(12, 1000);
        Path file = dir.resolve("cut.glb");
        Files.write(file, bytes);
        assertThat(NomenclatureModelGltf.glbProblem(file)).contains("обрезан");
    }

    @Test
    void checksTextGltfFile(@TempDir Path dir) throws IOException {
        Path ok = dir.resolve("ok.gltf");
        Files.writeString(ok, "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"uri\":\"data:application/octet-stream;base64,AAAA\"}]}");
        assertThat(NomenclatureModelGltf.gltfProblem(ok)).isNull();

        Path separateBin = dir.resolve("scene.gltf");
        Files.writeString(separateBin, "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"uri\":\"scene.bin\",\"byteLength\":100}]}");
        assertThat(NomenclatureModelGltf.gltfProblem(separateBin)).contains("scene.bin");
    }

    private static String problem(String json) throws IOException {
        return NomenclatureModelGltf.jsonProblem(new StringReader(json), 4096);
    }

    private static byte[] chunk(int type, String json) {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        int padded = (data.length + 3) / 4 * 4;
        byte[] content = new byte[padded];
        System.arraycopy(data, 0, content, 0, data.length);
        for (int i = data.length; i < padded; i++) content[i] = ' ';
        return chunk(type, content);
    }

    private static byte[] chunk(int type, byte[] content) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + content.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(content.length);
        buffer.putInt(type);
        buffer.put(content);
        return buffer.array();
    }

    /** GLB 2.0: заголовок 12 байт (длина — весь файл) и блоки */
    static byte[] glb(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) body.writeBytes(chunk);
        ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'g', 'l', 'T', 'F'});
        header.putInt(2);
        header.putInt(12 + body.size());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header.array());
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    static byte[] glbWithJson(String json) {
        return glb(chunk(JSON, json));
    }
}
