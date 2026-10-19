package com.example.dinamika_back.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Подписка одного зрителя на живой поток камеры: заголовок потока, строка кодека и фрагменты кадров.
 * Закрытие освобождает место зрителя; без зрителей камера через некоторое время отключается.
 */
public final class LiveSubscription implements AutoCloseable {

    /** Кадров нет дольше этого — ответ зрителю заканчивается (см. {@link #streamTo(OutputStream)}) */
    public static final long STALL_MILLIS = 15_000;
    /** Как часто проверять простой, пока кадров нет */
    private static final long POLL_MILLIS = 5_000;

    private final LiveStreamService service;
    private final LiveChannel channel;
    private final LiveChannel.Subscriber subscriber;
    private volatile boolean closed;

    LiveSubscription(LiveStreamService service, LiveChannel channel, LiveChannel.Subscriber subscriber) {
        this.service = service;
        this.channel = channel;
        this.subscriber = subscriber;
    }

    /** ftyp + moov — браузер получает их первыми */
    public byte[] init() {
        return channel.init();
    }

    /** Строка кодека для MediaSource; null, если не распознана */
    public String codec() {
        return channel.codec();
    }

    public String cameraName() {
        return channel.cameraName();
    }

    /**
     * Следующий фрагмент (moof + mdat). null — за время ожидания кадров не было;
     * {@link #isEnd(byte[])} — поток закончился: камера отключилась или подписка закрыта.
     */
    public byte[] next(long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) return LiveChannel.Subscriber.END;
        return subscriber.next(timeout, unit);
    }

    public static boolean isEnd(byte[] fragment) {
        return fragment == LiveChannel.Subscriber.END;
    }

    /**
     * Отдать поток зрителю: заголовок, затем кадры по мере поступления. Возвращается, когда поток закончился
     * или кадров не было дольше {@link #STALL_MILLIS}; IOException — зритель ушёл.
     *
     * <p>Ушедшего зрителя сервер замечает только по ошибке записи в ответ. Если камера перестала давать кадры,
     * писать нечего — без предела простоя такой ответ и его подписка (а с ней FFmpeg камеры) жили бы вечно.
     * Страница сама переподключается через 6 с без данных, так что живому зрителю этот предел не мешает.
     */
    public void streamTo(OutputStream out) throws IOException, InterruptedException {
        streamTo(out, STALL_MILLIS);
    }

    void streamTo(OutputStream out, long stallMillis) throws IOException, InterruptedException {
        byte[] head = init();
        if (head != null) out.write(head);
        out.flush();
        long lastWrite = System.currentTimeMillis();
        while (true) {
            byte[] fragment = next(Math.min(stallMillis, POLL_MILLIS), TimeUnit.MILLISECONDS);
            if (isEnd(fragment)) return;
            if (fragment == null) {
                if (System.currentTimeMillis() - lastWrite >= stallMillis) return;
                continue;
            }
            out.write(fragment);
            out.flush();
            lastWrite = System.currentTimeMillis();
        }
    }

    /** Почему поток закончился — для журнала; null, если его просто закрыли */
    public String endReason() {
        return channel.isClosed() ? channel.failureMessage() : null;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        service.unsubscribe(channel, subscriber);
    }
}
