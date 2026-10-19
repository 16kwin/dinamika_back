package com.example.dinamika_back.features;

import org.springframework.util.AntPathMatcher;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Правило ворот функции в виде {@code "[МЕТОД ]/шаблон"}. Шаблон — синтаксис AntPathMatcher: {@code *} — один
 * сегмент пути, {@code **} — любое число сегментов, в том числе в середине пути (выгрузки
 * {@code /api/**}{@code /export-pdf}); PathPattern так не умеет, поэтому ворота и правила ролей B5 используют Ant.
 *
 * <p>Метод {@code GET} совпадает и с {@code HEAD}: Spring MVC неявно обслуживает HEAD обработчиком
 * {@code @GetMapping}, и без этого HEAD обходил бы ворота. Правило без метода совпадает с любым методом.
 */
public final class GateRule {

    /** Вид правила */
    public enum Kind {
        /** Обычное правило: путь закрыт, когда функция выключена */
        GATE,
        /** Путь загрузки файла (функция uploads): закрыт только для запросов multipart/* */
        MULTIPART,
        /**
         * Файл необязателен (штрихкоды, поставки): до пакета B4b ворота путь не закрывают — присланный файл
         * отклоняет сторож обработчика; после B4b такие пути переходят в {@link #MULTIPART}
         */
        MIXED
    }

    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final String method;
    private final String pattern;
    private final Kind kind;
    private final AccessAudit.Event audited;
    /** Буквальное начало шаблона (целые сегменты до первой подстановки) — быстрый отсев в воротах */
    private final String prefix;

    private GateRule(String method, String pattern, Kind kind, AccessAudit.Event audited) {
        this.method = method;
        this.pattern = pattern;
        this.kind = kind;
        this.audited = audited;
        // Только целые сегменты до подстановки: «/**» совпадает и с нулём сегментов (/api/gpp/** → /api/gpp)
        int wildcard = firstWildcard(pattern);
        int slash = wildcard < 0 ? -1 : pattern.lastIndexOf('/', wildcard);
        this.prefix = wildcard < 0 ? pattern : pattern.substring(0, Math.max(slash, 0));
    }

    /** Разбор {@code "GET /api/video/live"} или {@code "/api/video/**"} */
    static GateRule parse(String spec, Kind kind) {
        Objects.requireNonNull(spec, "spec");
        String text = spec.trim();
        String method = null;
        String pattern = text;
        int space = text.indexOf(' ');
        if (space > 0) {
            method = text.substring(0, space).toUpperCase(Locale.ROOT);
            pattern = text.substring(space + 1).trim();
            if (!METHODS.contains(method)) {
                throw new IllegalArgumentException("Правило ворот «" + spec + "»: неизвестный метод " + method);
            }
        }
        if (!pattern.startsWith("/") || pattern.contains(" ")) {
            throw new IllegalArgumentException("Правило ворот «" + spec + "»: шаблон должен начинаться с /");
        }
        return new GateRule(method, pattern, kind, null);
    }

    GateRule audited(AccessAudit.Event event) {
        return new GateRule(method, pattern, kind, event);
    }

    /** Метод правила или null — любой метод */
    public String method() {
        return method;
    }

    public String pattern() {
        return pattern;
    }

    public Kind kind() {
        return kind;
    }

    /** Тип события журнала доступа для успешных ответов по этому правилу (B6) или null */
    public AccessAudit.Event audited() {
        return audited;
    }

    /** Подходит ли метод запроса: правило GET покрывает и HEAD */
    public boolean matchesMethod(String requestMethod) {
        if (method == null) return true;
        String m = requestMethod == null ? "" : requestMethod.toUpperCase(Locale.ROOT);
        return method.equals(m) || ("GET".equals(method) && "HEAD".equals(m));
    }

    /** Совпадает ли путь (без контекста приложения, уже декодированный) с шаблоном */
    public boolean matchesPath(String path) {
        return path != null && path.startsWith(prefix) && MATCHER.match(pattern, path);
    }

    /**
     * Совпадение запроса с правилом. Для {@link Kind#MULTIPART} учитывается и тип содержимого: тело не разбирается,
     * смотрится только заголовок Content-Type.
     */
    public boolean matches(String requestMethod, String path, String contentType) {
        if (!matchesMethod(requestMethod) || !matchesPath(path)) return false;
        return kind != Kind.MULTIPART || isMultipart(contentType);
    }

    /** Конкретный адрес для проб и тестов: {@code *} → {@code 1}, {@code **} → {@code x} */
    public String sampleUrl() {
        return pattern.replace("**", "x").replace("*", "1");
    }

    /** Запись правила как в реестре: {@code "GET /api/video/live"} */
    public String spec() {
        return method == null ? pattern : method + " " + pattern;
    }

    static boolean isMultipart(String contentType) {
        return contentType != null && contentType.trim().toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private static int firstWildcard(String pattern) {
        int result = -1;
        for (char c : new char[]{'*', '?', '{'}) {
            int i = pattern.indexOf(c);
            if (i >= 0 && (result < 0 || i < result)) result = i;
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GateRule other)) return false;
        return Objects.equals(method, other.method) && pattern.equals(other.pattern) && kind == other.kind
                && audited == other.audited;
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, pattern, kind, audited);
    }

    @Override
    public String toString() {
        String text = spec();
        if (kind == Kind.MULTIPART) text = "multipart " + text;
        if (kind == Kind.MIXED) text = "mixed " + text;
        return audited == null ? text : text + " [" + audited + "]";
    }
}
