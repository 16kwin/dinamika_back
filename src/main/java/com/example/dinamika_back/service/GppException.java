package com.example.dinamika_back.service;

import org.springframework.http.HttpStatus;

/**
 * Ошибка документа ГПП с HTTP-статусом для ответа API: 400 — запрос не прошёл проверку, 403 — не хватает
 * прав роли, 404 — документа нет, 409 — документ уже изменил другой пользователь.
 */
public class GppException extends RuntimeException {

    private final HttpStatus status;

    public GppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static GppException badRequest(String message) {
        return new GppException(HttpStatus.BAD_REQUEST, message);
    }

    public static GppException forbidden(String message) {
        return new GppException(HttpStatus.FORBIDDEN, message);
    }

    public static GppException notFound(String message) {
        return new GppException(HttpStatus.NOT_FOUND, message);
    }

    public static GppException conflict(String message) {
        return new GppException(HttpStatus.CONFLICT, message);
    }
}
