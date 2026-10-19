package com.example.dinamika_back.features;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Функция выключена: стандартный ответ 404 из сервисов и сторожей, без отдельного обработчика и без текста
 * для клиента (закрытая функция отвечает так же, как отсутствующий маршрут).
 */
public class FeatureDisabledException extends ResponseStatusException {

    private final transient Feature feature;

    public FeatureDisabledException(Feature feature) {
        super(HttpStatus.NOT_FOUND);
        this.feature = feature;
    }

    public Feature feature() {
        return feature;
    }

    @Override
    public String getMessage() {
        return "404 NOT_FOUND: функция " + feature.key() + " выключена";
    }
}
