package com.example.dinamika_back.features.audit;

import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.GateRule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.util.UrlPathHelper;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Журнал доступа по флагу {@code .audited(тип)} у правил реестра (раздел 10.2 проекта): просмотр и скачивание
 * видео, исходники 3D, выгрузки. Ворота стоят до Spring Security и пользователя не знают, поэтому HTTP-события пишет
 * этот перехватчик MVC — после ответа ({@code afterCompletion}) и только при ответе 2xx: отказ, 404 закрытой функции
 * и ошибка не пишутся. HEAD и OPTIONS данных не отдают и не пишутся.
 *
 * <p>Номер объекта — сегменты пути под {@code *} правила (id файла, модели). У выгрузки — заголовок таблицы и число
 * строк ({@link ExportBodyAdvice}), самих данных в журнале нет. Новый модуль, отдающий данные, помечает свои правила
 * {@code .audited(тип)} — перехватчик подхватит их сам (раздел 12).
 *
 * <p>Журнал берётся лениво: в срезах {@code @WebMvcTest} без бина журнала перехватчик ничего не делает.
 */
@Component
public class AccessAuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    /** Атрибут запроса со сведениями о выгрузке: заголовок и число строк */
    static final String EXPORT_ATTRIBUTE = AccessAuditInterceptor.class.getName() + ".export";
    private static final Logger log = LoggerFactory.getLogger(AccessAuditInterceptor.class);
    private static final UrlPathHelper PATHS = new UrlPathHelper();
    /** Правила с флагом .audited() — из реестра, один раз */
    private static final List<Audited> RULES = collect();

    private final ObjectProvider<AccessAudit> audit;

    public AccessAuditInterceptor(ObjectProvider<AccessAudit> audit) {
        this.audit = audit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (ex != null || !(handler instanceof HandlerMethod)) return;
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if (method.equals("HEAD") || method.equals("OPTIONS")) return;
        int status = response.getStatus();
        if (status < 200 || status >= 300) return;
        String path = PATHS.getPathWithinApplication(request);
        Audited matched = match(method, path, request.getContentType());
        if (matched == null) return;
        AccessAudit target = audit.getIfAvailable();
        if (target == null) return;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", status);
        String objectRef = objectRef(matched.rule(), path);
        if (matched.rule().audited() == AccessAudit.Event.EXPORT) {
            details.put("path", path);
            details.put("format", exportFormat(path));
            if (request.getAttribute(EXPORT_ATTRIBUTE) instanceof ExportInfo info) {
                objectRef = info.title();
                details.put("rows", info.rows());
            }
        } else {
            String length = response.getHeader("Content-Length");
            if (length != null) details.put("bytes", length);
            if (request.getHeader("Range") != null) details.put("range", true);
        }
        try {
            target.record(matched.rule().audited(), matched.feature(), objectRef, details);
        } catch (RuntimeException e) {
            // Ответ уже отдан: отказать поздно, остаётся предупреждение (оно же — в журнале доступа в виде пропуска)
            log.warn("Журнал доступа: {} {} не записано: {}", method, path, e.getMessage());
        }
    }

    /** Первое правило с флагом .audited(), подходящее к запросу, или null */
    static Audited match(String method, String path, String contentType) {
        for (Audited a : RULES) {
            if (a.rule().matches(method, path, contentType)) return a;
        }
        return null;
    }

    /** Сегменты пути на местах {@code *} шаблона: {@code /api/video/files/*}{@code /content} → «15» */
    static String objectRef(GateRule rule, String path) {
        String[] pattern = rule.pattern().split("/");
        String[] actual = path.split("/");
        if (rule.pattern().contains("**") || pattern.length != actual.length) return null;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i].equals("*")) parts.add(actual[i]);
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    private static String exportFormat(String path) {
        if (path.endsWith("/export-pdf")) return "pdf";
        if (path.endsWith("/export-excel")) return "excel";
        if (path.endsWith("/export-word")) return "word";
        if (path.endsWith("/print")) return "print";
        return null;
    }

    private static List<Audited> collect() {
        List<Audited> result = new ArrayList<>();
        for (Feature f : Feature.real()) {
            for (GateRule rule : f.rules()) {
                if (rule.audited() != null) result.add(new Audited(f, rule));
            }
        }
        return List.copyOf(result);
    }

    /** Правило реестра с флагом .audited() и его функция */
    record Audited(Feature feature, GateRule rule) {
    }

    /** Сведения о выгрузке без данных: заголовок таблицы и число строк */
    record ExportInfo(String title, Integer rows) {
    }

    /**
     * Заголовок и число строк выгрузки из тела запроса ({@code {title, data:[…]}}): тело читает обработчик, перехватчику
     * после ответа оно уже недоступно. Срабатывает только на путях выгрузки ({@code Feature.EXPORT}); сами данные
     * не сохраняются
     */
    @ControllerAdvice
    public static class ExportBodyAdvice extends RequestBodyAdviceAdapter {

        @Override
        public boolean supports(MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
            Class<?> raw = targetType instanceof Class<?> c ? c
                    : targetType instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c2 ? c2 : null;
            if (raw == null || !Map.class.isAssignableFrom(raw)) return false;
            HttpServletRequest request = currentRequest();
            if (request == null) return false;
            Audited matched = match(request.getMethod(), PATHS.getPathWithinApplication(request), request.getContentType());
            return matched != null && matched.rule().audited() == AccessAudit.Event.EXPORT;
        }

        @Override
        public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                    Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
            HttpServletRequest request = currentRequest();
            if (request != null && body instanceof Map<?, ?> map) {
                Object title = map.get("title");
                Object data = map.get("data");
                request.setAttribute(EXPORT_ATTRIBUTE, new ExportInfo(
                        title instanceof String s && !s.isBlank() ? s.trim() : null,
                        data instanceof Collection<?> rows ? rows.size() : null));
            }
            return body;
        }

        private static HttpServletRequest currentRequest() {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
        }
    }
}
