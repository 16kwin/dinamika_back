package com.example.dinamika_back.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * События станции, по которым пишется видео. Каждое событие — пара «начало / окончание»,
 * между ними идёт запись. Метка в имени файла — из схемы видеоархива
 * (authorityEvent, issueEvent и т.д.).
 */
public enum VideoEventType {

    /** Вход на станцию → выход из сеанса */
    AUTHORITY("authority", "Авторизация", "authorityEvent"),
    /** Команда на выдачу ТМЦ → закрытие ячейки и подтверждение выдачи */
    ISSUE("issue", "Выдача", "issueEvent");

    private final String code;
    private final String title;
    private final String fileTag;

    VideoEventType(String code, String title, String fileTag) {
        this.code = code;
        this.title = title;
        this.fileTag = fileTag;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String fileTag() {
        return fileTag;
    }

    public static Optional<VideoEventType> fromCode(String code) {
        if (code == null) return Optional.empty();
        String normalized = code.trim().toLowerCase();
        return Arrays.stream(values()).filter(type -> type.code.equals(normalized)).findFirst();
    }

    /** Название события для журнала; неизвестный код показывается как есть */
    public static String titleOf(String code) {
        return fromCode(code).map(VideoEventType::title).orElse(code);
    }
}
