package com.example.dinamika_back.config;

import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Живые обновления: брокер STOMP поверх SockJS на /ws-stations (функция {@code websocket}, раздел 6.5 проекта).
 * Без функции в потолке класса нет вместе с {@code @EnableWebSocketMessageBroker}: ни брокера, ни пулов
 * clientInbound/clientOutbound/brokerChannel, ни планировщика SockJS; публикации идут в {@code NoopLiveUpdates}.
 *
 * <ul>
 *   <li>Источники (Origin) — из {@code awms.websocket.allowed-origins}, по умолчанию тот же список, что у CORS.
 *       Пустой список — только тот же источник (без {@code "*"}).</li>
 *   <li>Клиент SockJS для iframe-транспорта — локальный {@value #SOCKJS_CLIENT_URL} (файл фронтенда
 *       public/vendor/sockjs), а не CDN, на который Spring ссылается по умолчанию. При непустом списке источников
 *       Spring iframe-транспорт не обслуживает вовсе; ссылка нужна в режиме «только тот же источник».</li>
 *   <li>Клиент только подписывается на /topic/**: кадр SEND отклоняется (иначе простой брокер переслал бы его
 *       подписчикам — подделка состояний станций и уведомлений заказов), подписка вне /topic/ — тоже.
 *       Публикации сервера идут через brokerChannel и перехватчик не проходят.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnFeature(Feature.WEBSOCKET)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Адрес клиента SockJS для iframe-транспорта: файл лежит во фронтенде (public/vendor/sockjs) */
    static final String SOCKJS_CLIENT_URL = "/vendor/sockjs/sockjs.min.js";
    /** Префикс простого брокера: всё, на что подписывается фронтенд */
    static final String TOPIC_PREFIX = "/topic/";

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final FeatureService features;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Префикса приложения (/app) нет: обработчиков @MessageMapping в коде нет, клиент ничего не отправляет
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> origins = features.ceiling().listParam(CeilingParam.WEBSOCKET_ALLOWED_ORIGINS);
        StompWebSocketEndpointRegistration endpoint = registry.addEndpoint("/ws-stations");
        if (origins.isEmpty()) {
            log.info("WebSocket /ws-stations: список awms.websocket.allowed-origins пуст — только тот же источник");
        } else {
            if (origins.contains("*")) {
                log.warn("WebSocket /ws-stations: в awms.websocket.allowed-origins указано «*» — подключение с любого "
                        + "сайта через браузер пользователя");
            }
            endpoint.setAllowedOriginPatterns(origins.toArray(String[]::new));
        }
        endpoint.withSockJS().setClientLibraryUrl(SOCKJS_CLIENT_URL);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ClientInboundInterceptor());
    }

    /**
     * Перехватчик кадров клиента (clientInboundChannel): SEND запрещён, SUBSCRIBE — только на /topic/**.
     * Отклонённый кадр отбрасывается (preSend → null): до брокера он не доходит, соединение остаётся, стек ошибки
     * в лог не пишется. WARN — не чаще раза в минуту с числом отброшенных кадров; адресат в лог не попадает.
     */
    static final class ClientInboundInterceptor implements ChannelInterceptor {

        private static final long WARN_INTERVAL_MILLIS = 60_000L;

        private final AtomicLong rejected = new AtomicLong();
        private final AtomicLong lastWarn = new AtomicLong();

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
            if (type == SimpMessageType.MESSAGE) {
                return reject(message, "SEND от клиента");
            }
            if (type == SimpMessageType.SUBSCRIBE) {
                String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
                if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
                    return reject(message, "SUBSCRIBE вне " + TOPIC_PREFIX);
                }
            }
            return message;
        }

        /** Число отброшенных кадров с запуска (для тестов и отчёта) */
        long rejected() {
            return rejected.get();
        }

        private Message<?> reject(Message<?> message, String what) {
            long count = rejected.incrementAndGet();
            long now = System.currentTimeMillis();
            long last = lastWarn.get();
            if ((last == 0 || now - last >= WARN_INTERVAL_MILLIS) && lastWarn.compareAndSet(last, now)) {
                // Номер сессии SockJS задаёт клиент (часть адреса), поэтому в лог не пишется
                log.warn("WebSocket /ws-stations: отброшен кадр клиента ({}); всего отброшено с запуска: {}", what,
                        count);
            }
            return null;
        }
    }
}
