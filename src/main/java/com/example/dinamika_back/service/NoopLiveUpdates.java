package com.example.dinamika_back.service;

import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import org.springframework.stereotype.Component;

/**
 * Заглушка живых обновлений, когда функция {@code websocket} выключена потолком: брокера, пулов каналов и SockJS
 * нет, сообщения никуда не уходят. Фронтенд в этом режиме не подключается к /ws-stations и перечитывает данные сам.
 */
@Component
@ConditionalOnFeature(value = Feature.WEBSOCKET, enabled = false)
public class NoopLiveUpdates implements LiveUpdates {

    @Override
    public void publish(String destination, Object payload) {
        // WebSocket выключен: подписчиков нет
    }
}
