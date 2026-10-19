package com.example.dinamika_back.service;

import org.springframework.http.HttpStatus;

/**
 * Ошибка 3D-моделей номенклатуры с HTTP-статусом для ответа API: 400 — неверный запрос или повреждённый файл,
 * 404 — не найдено, 413 — файл слишком большой, 415 — формат не поддерживается, 500 — не удалось записать файл.
 */
public class NomenclatureModelException extends RuntimeException {

    private final HttpStatus status;

    public NomenclatureModelException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static NomenclatureModelException badRequest(String message) {
        return new NomenclatureModelException(HttpStatus.BAD_REQUEST, message);
    }

    public static NomenclatureModelException notFound(String message) {
        return new NomenclatureModelException(HttpStatus.NOT_FOUND, message);
    }

    public static NomenclatureModelException tooLarge(String message) {
        return new NomenclatureModelException(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    public static NomenclatureModelException unsupported(String message) {
        return new NomenclatureModelException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }

    public static NomenclatureModelException storage(String message) {
        return new NomenclatureModelException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
