package com.example.dinamika_back.service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Живой поток одной камеры: один процесс FFmpeg и одно подключение к камере, сколько бы зрителей
 * ни смотрели. Кадры приходят фрагментированным MP4 ({@link Fmp4}) и раздаются всем подписчикам.
 *
 * <p>Новый зритель сразу получает заголовок потока и кадры от последнего опорного — браузеру есть
 * с чего начать декодирование, ждать следующего опорного кадра (до 2 секунд у камер стенда) не нужно.
 * Медленный зритель не тормозит остальных: если его очередь переполнилась, она сбрасывается
 * и он продолжает со следующего опорного кадра.
 */
final class LiveChannel {

    /** Сколько кадров группы от опорного держать для новых зрителей. Больше — значит, у камеры редкие опорные кадры */
    private static final int MAX_GOP_FRAMES = 300;
    private static final int ERROR_TAIL_LINES = 10;
    /** Окно счётчика кадров в секунду */
    private static final int STATS_SECONDS = 5;

    private final long cameraId;
    private final String cameraName;
    private final Process process;
    private final int queueLimit;
    private final Consumer<LiveChannel> onClosed;
    private final long startedAt = System.currentTimeMillis();

    private final CountDownLatch ready = new CountDownLatch(1);
    private final Deque<String> errorTail = new ArrayDeque<>();
    private final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();

    // Группа кадров от последнего опорного; под блокировкой канала
    private final List<byte[]> gop = new ArrayList<>();
    private boolean gopValid;

    // Счётчики за последние секунды: кадры и байты по номеру секунды; под блокировкой канала
    private final long[] statSecond = new long[STATS_SECONDS + 2];
    private final long[] statFrames = new long[STATS_SECONDS + 2];
    private final long[] statBytes = new long[STATS_SECONDS + 2];

    private volatile byte[] init;
    private volatile String codec;
    private volatile boolean closed;
    private volatile String closeReason;

    private LiveChannel(long cameraId, String cameraName, Process process, int queueLimit, Consumer<LiveChannel> onClosed) {
        this.cameraId = cameraId;
        this.cameraName = cameraName;
        this.process = process;
        this.queueLimit = queueLimit;
        this.onClosed = onClosed;
    }

    static LiveChannel start(long cameraId, String cameraName, List<String> command, int queueLimit,
                             Consumer<LiveChannel> onClosed) throws IOException {
        Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();
        LiveChannel channel = new LiveChannel(cameraId, cameraName, process, queueLimit, onClosed);
        channel.startThreads();
        return channel;
    }

    private void startThreads() {
        Thread reader = new Thread(this::readLoop, "live-" + cameraName + "-" + cameraId);
        reader.setDaemon(true);
        reader.start();

        Thread errors = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    synchronized (errorTail) {
                        errorTail.addLast(line);
                        while (errorTail.size() > ERROR_TAIL_LINES) errorTail.removeFirst();
                    }
                }
            } catch (IOException ignored) {
                // процесс завершён — поток закрыт
            }
        }, "live-stderr-" + cameraId);
        errors.setDaemon(true);
        errors.start();
    }

    /** Чтение stdout FFmpeg: заголовок потока, затем пары moof + mdat — по кадру */
    private void readLoop() {
        int trexFlags = 0;
        byte[] pendingMoof = null;
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        try (InputStream in = new BufferedInputStream(process.getInputStream(), 1 << 16)) {
            Fmp4.Box box;
            while ((box = Fmp4.readBox(in)) != null) {
                switch (box.type()) {
                    case "ftyp" -> header.writeBytes(box.bytes());
                    case "moov" -> {
                        header.writeBytes(box.bytes());
                        byte[] head = header.toByteArray();
                        trexFlags = Fmp4.trexDefaultFlags(head);
                        codec = Fmp4.codecString(head);
                        init = head;
                        ready.countDown();
                    }
                    case "moof" -> pendingMoof = box.bytes();
                    case "mdat" -> {
                        if (pendingMoof != null && init != null) {
                            publish(new Fmp4.Fragment(Fmp4.concat(pendingMoof, box.bytes()),
                                    Fmp4.isKeyframe(pendingMoof, trexFlags), Fmp4.sampleCount(pendingMoof)));
                        }
                        pendingMoof = null;
                    }
                    default -> {
                        // служебные боксы (free, mfra в конце) зрителям не нужны
                    }
                }
            }
            close(null);
        } catch (IOException e) {
            close(closed ? null : "Поток камеры прервался: " + e.getMessage());
        }
    }

    private synchronized void publish(Fmp4.Fragment fragment) {
        countStats(fragment);
        if (fragment.keyframe()) {
            gop.clear();
            gopValid = true;
        }
        if (gopValid) {
            if (gop.size() < MAX_GOP_FRAMES) {
                gop.add(fragment.bytes());
            } else {
                // Опорные кадры слишком редки — новые зрители подождут следующий, чем получат пол-группы
                gop.clear();
                gopValid = false;
            }
        }
        for (Subscriber subscriber : subscribers) subscriber.offer(fragment);
    }

    /** Новый зритель: кадры от последнего опорного уже в его очереди */
    synchronized Subscriber subscribe() {
        Subscriber subscriber = new Subscriber(queueLimit);
        if (gopValid && !gop.isEmpty()) {
            gop.forEach(subscriber.queue::offer);
        } else {
            subscriber.waitingKeyframe = true;
        }
        subscribers.add(subscriber);
        if (closed) subscriber.end();
        return subscriber;
    }

    void unsubscribe(Subscriber subscriber) {
        subscribers.remove(subscriber);
    }

    /** Дождаться заголовка потока: true — камера ответила и видео пошло */
    boolean awaitReady(long seconds) {
        try {
            ready.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return init != null && !closed;
    }

    /** Остановить FFmpeg и завершить поток у всех зрителей. reason — причина для зрителей, если это сбой */
    void close(String reason) {
        synchronized (this) {
            if (closed) return;
            closed = true;
            closeReason = reason;
        }
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        ready.countDown();
        subscribers.forEach(Subscriber::end);
        onClosed.accept(this);
    }

    /** Причина, по которой поток не пошёл или прервался — для ответа API */
    String failureMessage() {
        String tail;
        synchronized (errorTail) {
            tail = String.join("\n", errorTail);
        }
        if (!tail.isBlank()) return FfmpegRunner.explain(tail);
        if (closeReason != null) return closeReason;
        return "Камера не передала видео";
    }

    private void countStats(Fmp4.Fragment fragment) {
        long second = System.currentTimeMillis() / 1000;
        int i = (int) (second % statSecond.length);
        if (statSecond[i] != second) {
            statSecond[i] = second;
            statFrames[i] = 0;
            statBytes[i] = 0;
        }
        statFrames[i] += Math.max(1, fragment.samples());
        statBytes[i] += fragment.bytes().length;
    }

    /** Кадров в секунду за последние полные секунды */
    synchronized double fps() {
        return sumLastSeconds(statFrames) / (double) STATS_SECONDS;
    }

    /** Битрейт за последние полные секунды, Мбит/с */
    synchronized double bitrateMbps() {
        return sumLastSeconds(statBytes) * 8 / (double) STATS_SECONDS / 1_000_000;
    }

    private long sumLastSeconds(long[] values) {
        long now = System.currentTimeMillis() / 1000;
        long sum = 0;
        for (int i = 0; i < statSecond.length; i++) {
            long age = now - statSecond[i];
            if (age >= 1 && age <= STATS_SECONDS) sum += values[i];
        }
        return sum;
    }

    long cameraId() {
        return cameraId;
    }

    String cameraName() {
        return cameraName;
    }

    byte[] init() {
        return init;
    }

    String codec() {
        return codec;
    }

    boolean isClosed() {
        return closed;
    }

    long startedAt() {
        return startedAt;
    }

    int viewerCount() {
        return subscribers.size();
    }

    long droppedFrames() {
        return subscribers.stream().mapToLong(subscriber -> subscriber.dropped).sum();
    }

    /** Очередь кадров одного зрителя */
    static final class Subscriber {

        /** Метка конца потока в очереди */
        static final byte[] END = new byte[0];

        private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        private final int limit;
        private volatile boolean waitingKeyframe;
        private volatile long dropped;

        private Subscriber(int limit) {
            this.limit = limit;
        }

        private void offer(Fmp4.Fragment fragment) {
            if (waitingKeyframe) {
                if (!fragment.keyframe()) {
                    dropped++;
                    return;
                }
                waitingKeyframe = false;
            }
            if (queue.size() >= limit) {
                // Зритель не успевает — старые кадры ему уже не нужны, продолжит со следующего опорного
                dropped += queue.size();
                queue.clear();
                if (!fragment.keyframe()) {
                    waitingKeyframe = true;
                    dropped++;
                    return;
                }
            }
            queue.offer(fragment.bytes());
        }

        private void end() {
            queue.offer(END);
        }

        /** Следующий фрагмент; null — за время ожидания кадров не было; {@link #END} — поток закончился */
        byte[] next(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }
}
