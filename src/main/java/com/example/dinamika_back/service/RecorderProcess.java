package com.example.dinamika_back.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Один процесс FFmpeg, пишущий одну камеру в один файл.
 *
 * <p>Остановка — символ «q» в stdin: FFmpeg дописывает индекс MP4 и закрывает файл. На Windows
 * другого мягкого способа нет: {@link Process#destroy()} там сразу убивает процесс, и файл без индекса
 * не воспроизводится. Поэтому «убить» — только запасной вариант, если FFmpeg завис.
 */
final class RecorderProcess {

    private static final int ERROR_TAIL_LINES = 20;

    private final Path target;
    private final Process process;
    private final Deque<String> errorTail = new ArrayDeque<>();
    private volatile boolean stopRequested;

    private RecorderProcess(Path target, Process process) {
        this.target = target;
        this.process = process;
    }

    /** Запустить запись. Каталог файла создаётся при необходимости */
    static RecorderProcess start(List<String> command, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        RecorderProcess recorder = new RecorderProcess(target, builder.start());
        recorder.startErrorReader();
        return recorder;
    }

    /** Читать stderr постоянно: иначе заполнится буфер и FFmpeg остановится. Хранится хвост для причины ошибки */
    private void startErrorReader() {
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    synchronized (errorTail) {
                        errorTail.addLast(line);
                        while (errorTail.size() > ERROR_TAIL_LINES) errorTail.removeFirst();
                    }
                }
            } catch (IOException ignored) {
                // процесс завершился — поток закрыт
            }
        }, "ffmpeg-stderr-" + target.getFileName());
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Корректно остановить запись: «q» в stdin, затем ожидание до {@code graceMillis}.
     * Если FFmpeg не завершился — завершить принудительно.
     */
    void stop(long graceMillis) {
        stopRequested = true;
        if (!process.isAlive()) return;
        try {
            OutputStream stdin = process.getOutputStream();
            stdin.write('q');
            stdin.flush();
            stdin.close();
        } catch (IOException ignored) {
            // FFmpeg уже закрыл stdin — значит, завершается сам
        }
        try {
            if (!process.waitFor(graceMillis, TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor(3, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /** Завершение процесса — для наблюдения за неожиданной остановкой */
    CompletableFuture<Process> onExit() {
        return process.onExit();
    }

    boolean isAlive() {
        return process.isAlive();
    }

    boolean stopRequested() {
        return stopRequested;
    }

    /** Код выхода или -1, если процесс ещё работает */
    int exitCode() {
        return process.isAlive() ? -1 : process.exitValue();
    }

    Path target() {
        return target;
    }

    String errorText() {
        synchronized (errorTail) {
            return String.join("\n", errorTail);
        }
    }
}
