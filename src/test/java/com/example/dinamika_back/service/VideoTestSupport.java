package com.example.dinamika_back.service;

import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureService;

import java.util.List;
import java.util.Map;

/**
 * Общее для тестов видео, которые создают классы напрямую, без контекста Spring: политика источников и функции
 * видео задаются явно, как их задал бы потолок установки.
 */
final class VideoTestSupport {

    /** Источники как на стенде: тестовый сигнал, эмулятор камеры из файла и камеры в любой сети */
    static final VideoSource.SourcePolicy OPEN_SOURCES = new VideoSource.SourcePolicy(true,
            List.of(VideoSource.Network.parse("0.0.0.0/0"), VideoSource.Network.parse("::/0")));

    /** Умолчания потолка: file: и test: запрещены, камеры — только в частных сетях RFC 1918 */
    static final VideoSource.SourcePolicy DEFAULT_SOURCES =
            VideoSource.SourcePolicy.of(FeatureResolver.fromProperties(Map.of("awms.features.preset", "full")));

    /** Пресет full: все функции видео включены */
    static final FeatureService FULL = new FeatureService(
            FeatureResolver.fromProperties(Map.of("awms.features.preset", "full")));

    /** Журнал доступа, который никуда не пишет */
    static final AccessAudit NO_AUDIT = new AccessAudit.Noop();

    private VideoTestSupport() {
    }
}
