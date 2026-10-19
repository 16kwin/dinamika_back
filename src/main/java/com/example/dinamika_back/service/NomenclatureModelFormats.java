package com.example.dinamika_back.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Форматы 3D-моделей номенклатуры: белый список расширений, подсказки для собственных форматов САПР
 * и проверки содержимого (заголовок GLB, сцена X3D в HTML). Без Spring — проверяется обычными тестами.
 */
public final class NomenclatureModelFormats {

    /** Предел размера одного файла — и исходника, и копии GLB */
    public static final long MAX_FILE_SIZE = 300L * 1024 * 1024;

    /** Расширение → формат. Тот же список на фронтенде: Model3D/formats.ts */
    private static final Map<String, String> FORMATS = new LinkedHashMap<>();

    /** Собственные форматы САПР, которые браузер не прочитает, → в какой формат выгрузить */
    private static final Map<String, String> CAD_HINTS = new LinkedHashMap<>();

    private static final String KOMPAS_HINT = "Это файл КОМПАС-3D: формат закрытый, браузер его не прочитает. "
            + "Сохраните модель в КОМПАС-3D как STEP AP214 (*.stp) или VRML (*.wrl) и загрузите ещё раз.";
    private static final String TFLEX_HINT = "Это файл T-FLEX CAD: формат закрытый, браузер его не прочитает. "
            + "Выгрузите модель из T-FLEX CAD в GLB (*.glb) или STEP (*.stp) и загрузите ещё раз.";
    private static final String DWG_HINT = "Чертежи DWG/DXF не открываются как 3D-модель. "
            + "nanoCAD: экспорт в GLB или STEP; AutoCAD: экспорт в STL, STEP — через AutoCAD Mechanical.";
    private static final String STEP_HINT = "Этот формат браузер не прочитает. Выгрузите модель из САПР в STEP (*.stp) "
            + "или GLB (*.glb) и загрузите ещё раз.";

    static {
        FORMATS.put("glb", "glb");
        FORMATS.put("gltf", "gltf");
        FORMATS.put("stp", "step");
        FORMATS.put("step", "step");
        FORMATS.put("igs", "iges");
        FORMATS.put("iges", "iges");
        FORMATS.put("wrl", "vrml");
        FORMATS.put("vrml", "vrml");
        FORMATS.put("wrz", "vrml");
        FORMATS.put("stl", "stl");
        FORMATS.put("obj", "obj");
        FORMATS.put("3mf", "3mf");
        FORMATS.put("ply", "ply");
        FORMATS.put("x3d", "x3d");
        // Страница со сценой X3D (экспорт FreeCAD для X3DOM): разбирается как текст, HTML не исполняется
        FORMATS.put("xhtml", "x3d");
        FORMATS.put("html", "x3d");
        FORMATS.put("htm", "x3d");

        CAD_HINTS.put("a3d", KOMPAS_HINT);
        CAD_HINTS.put("m3d", KOMPAS_HINT);
        CAD_HINTS.put("cdw", "Это чертёж КОМПАС-3D (2D), в нём нет 3D-модели. Откройте модель детали или сборки "
                + "и сохраните её как STEP AP214 (*.stp) или VRML (*.wrl).");
        CAD_HINTS.put("frw", "Это фрагмент КОМПАС-3D (2D), в нём нет 3D-модели. Откройте модель детали или сборки "
                + "и сохраните её как STEP AP214 (*.stp) или VRML (*.wrl).");
        CAD_HINTS.put("grb", TFLEX_HINT);
        CAD_HINTS.put("grs", TFLEX_HINT);
        CAD_HINTS.put("dwg", DWG_HINT);
        CAD_HINTS.put("dxf", DWG_HINT);
        for (String ext : new String[]{"sldprt", "sldasm", "ipt", "iam", "prt", "asm", "x_t", "x_b", "sat", "jt",
                "catpart", "catproduct", "f3d", "3dm"}) {
            CAD_HINTS.put(ext, STEP_HINT);
        }
        CAD_HINTS.put("bin", "Это буфер данных glTF, сам по себе он не открывается. Загрузите модель одним файлом GLB "
                + "(*.glb) или glTF со встроенными данными.");
        CAD_HINTS.put("zip", "Архивы не принимаются: распакуйте архив и загрузите сам файл модели (GLB, STEP, VRML, STL…).");
    }

    private NomenclatureModelFormats() {
    }

    /** Расширение без точки в нижнем регистре; пустая строка, если его нет. Пути в имени отбрасываются */
    public static String extension(String fileName) {
        String name = baseName(fileName);
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Формат по имени файла или null, если расширение не из белого списка */
    public static String formatOf(String fileName) {
        return FORMATS.get(extension(fileName));
    }

    /** Понятный отказ для неподдерживаемого файла: для форматов САПР — в какой формат выгрузить */
    public static String rejectionMessage(String fileName) {
        String ext = extension(fileName);
        String hint = CAD_HINTS.get(ext);
        if (hint != null) return hint;
        String supported = "GLB, glTF, STEP, IGES, VRML, STL, OBJ, 3MF, PLY, X3D";
        return ext.isEmpty()
                ? "У файла нет расширения. Поддерживаются: " + supported + "."
                : "Формат ." + ext + " не поддерживается. Поддерживаются: " + supported + ".";
    }

    /** Имя без каталогов и управляющих символов, не длиннее 200 символов — для БД и скачивания */
    public static String safeFileName(String fileName) {
        String name = baseName(fileName).replaceAll("[\\p{Cntrl}\"]", "_").trim();
        if (name.isEmpty()) name = "model";
        if (name.length() > 200) {
            String ext = extension(name);
            String tail = ext.isEmpty() ? "" : "." + ext;
            name = name.substring(0, 200 - tail.length()) + tail;
        }
        return name;
    }

    /**
     * Заголовок GLB 2.0: «glTF», версия 2, длина из заголовка равна размеру файла, первый блок — JSON.
     * header — первые 20 байт файла (или меньше, если файл короче).
     */
    public static boolean isGlb(byte[] header, long fileSize) {
        if (header == null || header.length < 20 || fileSize < 20) return false;
        boolean magic = header[0] == 'g' && header[1] == 'l' && header[2] == 'T' && header[3] == 'F';
        long version = readUint32(header, 4);
        long length = readUint32(header, 8);
        long chunkType = readUint32(header, 16);
        return magic && version == 2 && length == fileSize && chunkType == 0x4E4F534AL;
    }

    public static boolean isGlb(Path file) throws IOException {
        long size = Files.size(file);
        try (InputStream in = Files.newInputStream(file)) {
            return isGlb(in.readNBytes(20), size);
        }
    }

    /** Есть ли в HTML/XHTML сцена X3D: ищем «<x3d» без учёта регистра, файл читаем потоком */
    public static boolean containsX3dScene(Path file) throws IOException {
        byte[] needle = "<x3d".getBytes(StandardCharsets.US_ASCII);
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int matched = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                for (int i = 0; i < read; i++) {
                    byte b = buffer[i];
                    byte lower = (b >= 'A' && b <= 'Z') ? (byte) (b + 32) : b;
                    if (lower == needle[matched]) {
                        matched++;
                        if (matched == needle.length) return true;
                    } else {
                        matched = lower == needle[0] ? 1 : 0;
                    }
                }
            }
        }
        return false;
    }

    private static String baseName(String fileName) {
        if (fileName == null) return "";
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static long readUint32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | (bytes[offset + 1] & 0xFFL) << 8
                | (bytes[offset + 2] & 0xFFL) << 16
                | (bytes[offset + 3] & 0xFFL) << 24;
    }
}
