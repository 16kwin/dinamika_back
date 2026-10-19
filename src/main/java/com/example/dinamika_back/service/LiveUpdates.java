package com.example.dinamika_back.service;

/**
 * Живые обновления для фронтенда (функция {@code websocket}, раздел 6.5 проекта «Дискретное отключение функций»).
 * Сервисы публикуют изменения через этот интерфейс, а не через {@code SimpMessagingTemplate}: при выключенном
 * WebSocket брокера нет, и вместо {@link StompLiveUpdates} работает {@link NoopLiveUpdates}. Заказы и ТКП при этом
 * создаются как обычно — фронтенд перечитывает списки сам.
 *
 * <p>Адресаты, на которые подписан фронтенд (менять их нельзя): {@code /topic/stations/static},
 * {@code /topic/stations/dynamic}, {@code /topic/stations/{static|dynamic}/{uid}}, {@code /topic/orders/refresh},
 * {@code /topic/tkp/new}, {@code /topic/tkp/status}.
 */
public interface LiveUpdates {

    /**
     * Разослать подписчикам адресата.
     *
     * @param destination адресат STOMP ({@code /topic/…})
     * @param payload     тело сообщения (сериализуется в JSON)
     */
    void publish(String destination, Object payload);
}
