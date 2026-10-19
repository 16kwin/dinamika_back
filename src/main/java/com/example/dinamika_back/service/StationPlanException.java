package com.example.dinamika_back.service;

import org.springframework.http.HttpStatus;

/**
 * Ошибка карты станций с HTTP-статусом для ответа API: 400 — неверный запрос или файл, 403 — не хватает
 * прав роли, 404 — не найдено, 409 — план уже изменил другой пользователь, 413 — файл слишком большой.
 */
public class StationPlanException extends RuntimeException {

    private final HttpStatus status;

    public StationPlanException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static StationPlanException badRequest(String message) {
        return new StationPlanException(HttpStatus.BAD_REQUEST, message);
    }

    public static StationPlanException forbidden(String message) {
        return new StationPlanException(HttpStatus.FORBIDDEN, message);
    }

    public static StationPlanException notFound(String message) {
        return new StationPlanException(HttpStatus.NOT_FOUND, message);
    }

    public static StationPlanException conflict(String message) {
        return new StationPlanException(HttpStatus.CONFLICT, message);
    }

    public static StationPlanException tooLarge(String message) {
        return new StationPlanException(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }
}
