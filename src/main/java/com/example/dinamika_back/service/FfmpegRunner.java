package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.VideoStreamCheckDTO;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Запуск FFmpeg и ffprobe для коротких операций: версия, кадр с камеры, проверка потока,
 * длительность записанного файла, склейка сохранённого фрагмента. Запись по событиям —
 * в {@link RecorderProcess}, живой поток — в {@link LiveChannel}; здесь только их команды.
 *
 * <p>Вход камеры для любой команды строится через {@link #input}: источник проверяется по политике установки
 * ({@link VideoSource#checkedSource}) в момент запуска, а не только при сохранении камеры. Бина нет, когда
 * потолок выключает видео: тогда процессов FFmpeg и ffprobe у службы нет вовсе.
 */
@Slf4j
@Component
@ConditionalOnFeature(Feature.VIDEO)
@RequiredArgsConstructor
public class FfmpegRunner {

    /** Пока FFmpeg не найден, версия для состояния модуля проверяется заново не чаще этого */
    static final long VERSION_RETRY_MILLIS = 60_000;

    private final VideoSettings settings;
    private final ObjectMapper objectMapper;

    // Версия для состояния модуля; под versionLock, кроме быстрого чтения уже найденной
    private final Object versionLock = new Object();
    private volatile String knownVersion;
    private long versionCheckedAt;
    private boolean versionChecked;

    /** Результат запуска процесса: код выхода, вывод и ошибки */
    record ProcessResult(int exitCode, byte[] stdout, String stderr, boolean timedOut) {
    }

    // ==================== Версия ====================

    /**
     * Версия FFmpeg для состояния модуля: страница опрашивает его каждые несколько секунд, а `ffmpeg -version`
     * запускается один раз. Пока FFmpeg не найден — повторная проверка не чаще раза в минуту, чтобы после
     * установки FFmpeg состояние ожило без перезапуска сервера.
     */
    public String cachedVersion() {
        String known = knownVersion;
        if (known != null) return known;
        synchronized (versionLock) {
            if (knownVersion != null) return knownVersion;
            long now = now();
            if (versionChecked && now - versionCheckedAt < VERSION_RETRY_MILLIS) return null;
            versionChecked = true;
            versionCheckedAt = now;
            knownVersion = version();
            return knownVersion;
        }
    }

    /** Текущее время; в тестах подменяется */
    long now() {
        return System.currentTimeMillis();
    }

    /** Первая строка `ffmpeg -version` или null, если FFmpeg не найден. Запускает процесс на каждый вызов */
    public String version() {
        try {
            ProcessResult result = run(List.of(settings.ffmpegPath(), "-hide_banner", "-version"), 5_000);
            if (result.exitCode() != 0) return null;
            String out = new String(result.stdout(), StandardCharsets.UTF_8);
            int lineEnd = out.indexOf('\n');
            return (lineEnd < 0 ? out : out.substring(0, lineEnd)).trim();
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== Кадр для живого просмотра ====================

    /** Один кадр JPEG с камеры. Бросает {@link VideoException} 503, если камера не ответила */
    public byte[] snapshot(String url) {
        List<String> command = new ArrayList<>(List.of(settings.ffmpegPath(), "-hide_banner", "-loglevel", "error"));
        command.addAll(input(url, false));
        command.addAll(List.of("-frames:v", "1", "-q:v", "4", "-f", "image2", "-c:v", "mjpeg", "pipe:1"));

        ProcessResult result = runOrUnavailable(command, (settings.connectTimeoutSeconds() + 4) * 1000L);
        if (result.timedOut()) {
            throw VideoException.unavailable("Камера не ответила за " + settings.connectTimeoutSeconds() + " с");
        }
        if (result.exitCode() != 0 || result.stdout().length == 0) {
            throw VideoException.unavailable(explain(result.stderr()));
        }
        return result.stdout();
    }

    // ==================== Проверка потока ====================

    /** Подключиться к потоку и прочитать его параметры: кодек, размер кадра, частоту */
    public VideoStreamCheckDTO check(Long cameraId, String url) {
        long started = System.currentTimeMillis();
        List<String> command = new ArrayList<>(List.of(settings.ffprobePath(), "-v", "error"));
        try {
            command.addAll(input(url, false));
        } catch (VideoException e) {
            // Источник не прошёл проверку политики — ffprobe не запускается
            return new VideoStreamCheckDTO(cameraId, false, e.getMessage(), null, null, null, null, elapsed(started));
        }
        command.addAll(List.of("-select_streams", "v:0",
                "-show_entries", "stream=codec_name,width,height,avg_frame_rate", "-of", "json"));

        ProcessResult result;
        try {
            result = run(command, (settings.connectTimeoutSeconds() + 4) * 1000L);
        } catch (IOException e) {
            return new VideoStreamCheckDTO(cameraId, false,
                    "Не найден ffprobe: " + settings.ffprobePath(), null, null, null, null, elapsed(started));
        }
        if (result.timedOut()) {
            return new VideoStreamCheckDTO(cameraId, false,
                    "Камера не ответила за " + settings.connectTimeoutSeconds() + " с", null, null, null, null, elapsed(started));
        }
        if (result.exitCode() != 0) {
            return new VideoStreamCheckDTO(cameraId, false, explain(result.stderr()), null, null, null, null, elapsed(started));
        }
        try {
            JsonNode streams = objectMapper.readTree(result.stdout()).path("streams");
            if (!streams.isArray() || streams.isEmpty()) {
                return new VideoStreamCheckDTO(cameraId, false, "В потоке нет видео", null, null, null, null, elapsed(started));
            }
            JsonNode video = streams.get(0);
            String codec = video.path("codec_name").asText(null);
            Integer width = video.has("width") ? video.get("width").asInt() : null;
            Integer height = video.has("height") ? video.get("height").asInt() : null;
            Double fps = parseRate(video.path("avg_frame_rate").asText(null));
            String message = "Поток доступен" + (codec != null ? ": " + codec.toUpperCase() : "")
                    + (width != null && height != null ? ", " + width + "×" + height : "")
                    + (fps != null ? ", " + Math.round(fps) + " к/с" : "");
            return new VideoStreamCheckDTO(cameraId, true, message, codec, width, height, fps, elapsed(started));
        } catch (IOException e) {
            return new VideoStreamCheckDTO(cameraId, false, "Не удалось разобрать ответ ffprobe", null, null, null, null, elapsed(started));
        }
    }

    // ==================== Длительность файла ====================

    /** Длительность MP4 в секундах по ffprobe или null, если файл не читается */
    public BigDecimal durationOf(Path file) {
        try {
            ProcessResult result = run(List.of(settings.ffprobePath(), "-v", "error",
                    "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
                    file.toString()), 15_000);
            if (result.exitCode() != 0) return null;
            String text = new String(result.stdout(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty() || "N/A".equalsIgnoreCase(text)) return null;
            return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    // ==================== Команда записи ====================

    /**
     * Команда записи одной камеры в MP4. Поток камеры пишется без перекодирования (-c copy),
     * только видео: звук камер HiWatch бывает в G.711, а его MP4 не принимает.
     * Остановка — символ «q» в stdin, тогда FFmpeg корректно закрывает файл.
     */
    public List<String> recordCommand(String url, Path target) {
        List<String> command = new ArrayList<>(List.of(settings.ffmpegPath(),
                "-hide_banner", "-nostats", "-loglevel", "error", "-y"));
        command.addAll(input(url, true));
        command.addAll(List.of("-map", "0:v:0", "-an"));
        if (VideoSource.needsEncoding(url)) {
            command.addAll(List.of("-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency",
                    "-pix_fmt", "yuv420p", "-g", "50"));
        } else {
            command.addAll(List.of("-c:v", "copy"));
        }
        // Страховка от забытого события «окончание»; faststart — чтобы браузер начинал воспроизведение сразу
        command.addAll(List.of("-t", String.valueOf(settings.maxRecordSeconds()), "-movflags", "+faststart"));
        command.add(target.toString());
        return command;
    }

    // ==================== Живой поток ====================

    /**
     * Живой поток для браузера: видео камеры без перекодирования, фрагментированный MP4 в stdout.
     * По фрагменту на кадр — зритель получает каждый кадр сразу, а не пачкой, поэтому задержка
     * определяется сетью и камерой, а не размером фрагмента.
     */
    public List<String> liveCommand(String url) {
        List<String> command = new ArrayList<>(List.of(settings.ffmpegPath(),
                "-hide_banner", "-nostats", "-loglevel", "error",
                // Не копить входной буфер: кадр уходит дальше, как только пришёл
                "-fflags", "nobuffer"));
        command.addAll(input(url, true));
        command.addAll(List.of("-map", "0:v:0", "-an"));
        if (VideoSource.needsEncoding(url)) {
            command.addAll(List.of("-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency",
                    "-pix_fmt", "yuv420p", "-g", "50"));
        } else {
            command.addAll(List.of("-c:v", "copy"));
        }
        command.addAll(List.of("-f", "mp4", "-movflags", "empty_moov+default_base_moof+frag_every_frame",
                "-flush_packets", "1", "pipe:1"));
        return command;
    }

    /**
     * Сохранённый с живого потока фрагмент → обычный MP4: время с нуля, индекс в начале файла,
     * чтобы файл сразу открывался в любом плеере. Видео не перекодируется.
     */
    public void remux(Path source, Path target) {
        ProcessResult result = runOrUnavailable(List.of(settings.ffmpegPath(), "-hide_banner", "-loglevel", "error", "-y",
                "-i", source.toString(), "-map", "0:v:0", "-c", "copy", "-movflags", "+faststart", target.toString()), 120_000);
        if (result.timedOut() || result.exitCode() != 0) {
            throw VideoException.unavailable("Не удалось сохранить запись: " + explain(result.stderr()));
        }
    }

    // ==================== Источник камеры ====================

    /**
     * Вход камеры для команды FFmpeg/ffprobe. Источник проверяется по политике установки прямо перед запуском:
     * file:/test: — по awms.video.allow-file-source, адрес камеры — по awms.video.allowed-networks, причём FFmpeg
     * получает уже проверенный IP, а не имя. Отказ — {@link VideoException} до запуска процесса
     */
    List<String> input(String url, boolean realtime) {
        return VideoSource.inputArgs(url, settings.connectTimeoutSeconds(), realtime, settings.sourcePolicy(),
                this::resolveHost);
    }

    /**
     * Проверка строки подключения при сохранении камеры: то же правило, что при запуске FFmpeg, но имя, которое
     * сейчас не разрешается (камера ещё не в сети), сохранить можно — адрес проверится при подключении.
     * Отказ — 400 с причиной
     */
    public void verifySource(String url) {
        try {
            VideoSource.checkedSource(url, settings.sourcePolicy(), this::resolveHost);
        } catch (VideoException e) {
            if (e.status() == HttpStatus.SERVICE_UNAVAILABLE) return;
            throw VideoException.badRequest(e.getMessage());
        }
    }

    /** Адреса камеры по имени (IP-адрес возвращается без обращения к DNS); в тестах подменяется */
    protected InetAddress[] resolveHost(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    // ==================== Служебное ====================

    private ProcessResult runOrUnavailable(List<String> command, long timeoutMs) {
        try {
            return run(command, timeoutMs);
        } catch (IOException e) {
            throw VideoException.unavailable("Не найден FFmpeg: " + settings.ffmpegPath());
        }
    }

    /** Запуск процесса с чтением stdout и stderr в отдельных потоках, чтобы буфер не заблокировал FFmpeg */
    ProcessResult run(List<String> command, long timeoutMs) throws IOException {
        Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();
        CompletableFuture<byte[]> out = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        CompletableFuture<byte[]> err = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            process.destroyForcibly();
        }
        byte[] stdout = out.join();
        String stderr = new String(err.join(), StandardCharsets.UTF_8).trim();
        return new ProcessResult(finished ? process.exitValue() : -1, stdout, stderr, !finished);
    }

    private static byte[] readAll(InputStream stream) {
        try (InputStream in = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** Понятная причина по тексту ошибки FFmpeg */
    static String explain(String stderr) {
        String text = stderr == null ? "" : stderr;
        String lower = text.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized")) {
            return "Камера отклонила логин или пароль. Проверьте строку подключения";
        }
        if (lower.contains("404") || lower.contains("not found")) {
            return "Камера не знает такой путь потока. Проверьте конец ссылки, например /Channels/101";
        }
        if (lower.contains("connection refused")) {
            return "Камера отказала в подключении. Проверьте порт RTSP (обычно 554)";
        }
        // -10054 — WSAECONNRESET: камера сама разорвала TCP-сессию посреди потока
        if (lower.contains("error number -10054") || lower.contains("connection reset")) {
            return "Камера оборвала соединение. Если повторяется — допишите в конец строки подключения ?transport=udp";
        }
        if (lower.contains("timed out") || lower.contains("timeout") || lower.contains("no route")
                || lower.contains("network is unreachable") || lower.contains("error number -138")) {
            return "Камера недоступна по сети. Проверьте, что компьютер в одной сети с камерой";
        }
        // FFmpeg печатает ссылку входа вместе с паролем — в сообщение она попадает без него
        // (в том числе с пустым логином и схемой в верхнем регистре)
        String lastLine = text.lines().reduce((first, second) -> second).orElse("").trim()
                .replaceAll("(?i)(rtsp://[^:/@\\s]*:)[^@\\s]+@", "$1***@");
        return lastLine.isEmpty() ? "FFmpeg завершился с ошибкой" : "Ошибка FFmpeg: " + lastLine;
    }

    private static Double parseRate(String rate) {
        if (rate == null || rate.isBlank()) return null;
        try {
            int slash = rate.indexOf('/');
            if (slash < 0) return Double.parseDouble(rate);
            double num = Double.parseDouble(rate.substring(0, slash));
            double den = Double.parseDouble(rate.substring(slash + 1));
            return den == 0 ? null : num / den;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }
}
