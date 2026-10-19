package com.example.dinamika_back.service;

import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Живые обновления через брокер STOMP (функция {@code websocket} разрешена потолком). Единственное место в коде,
 * где используется {@link SimpMessagingTemplate}: остальные публикуют через {@link LiveUpdates}, поэтому без брокера
 * служба запускается и при включённых заказах. Сообщения идут в brokerChannel, мимо перехватчика клиентских кадров
 * ({@code WebSocketConfig}).
 */
@Component
@ConditionalOnFeature(Feature.WEBSOCKET)
@RequiredArgsConstructor
public class StompLiveUpdates implements LiveUpdates {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}
