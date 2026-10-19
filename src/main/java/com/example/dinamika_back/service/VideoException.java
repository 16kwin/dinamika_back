package com.example.dinamika_back.service;

import org.springframework.http.HttpStatus;

/**
 * Ошибка видеоархива с HTTP-статусом для ответа API: 400 — неверный запрос, 403 — не хватает прав роли,
 * 404 — не найдено, 409 — конфликт состояния записи, 503 — камера или FFmpeg недоступны.
 */
public class VideoException extends RuntimeException {

    private final HttpStatus status;

    public VideoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static VideoException badRequest(String message) {
        return new VideoException(HttpStatus.BAD_REQUEST, message);
    }

    public static VideoException forbidden(String message) {
        return new VideoException(HttpStatus.FORBIDDEN, message);
    }

    public static VideoException notFound(String message) {
        return new VideoException(HttpStatus.NOT_FOUND, message);
    }

    public static VideoException conflict(String message) {
        return new VideoException(HttpStatus.CONFLICT, message);
    }

    public static VideoException unavailable(String message) {
        return new VideoException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
