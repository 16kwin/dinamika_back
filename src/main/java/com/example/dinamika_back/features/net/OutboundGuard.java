package com.example.dinamika_back.features.net;

import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureDisabledException;
import com.example.dinamika_back.features.FeatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Сторож исходящих HTTP (разделы 4.5, 6.1 и 6.4 проекта «Дискретное отключение функций»). Ставится перехватчиком
 * в каждый HTTP-клиент службы; клиент привязан к функции: {@code orders} — {@code RestTemplateConfig},
 * {@code testsync} — {@code SyncService}. Запрос уходит, только если:
 * <ol>
 *   <li>функция клиента включена ({@code features.require}: выключена → 404 {@code FeatureDisabledException});</li>
 *   <li>хост:порт итогового адреса есть в {@code awms.network.outbound.allowed} (потолок установки). Порт без явного
 *       указания — 80 для http и 443 для https; хост сравнивается без учёта регистра; другие схемы не допускаются.
 *       Пустой список (умолчание) — исходящих нет даже при включённых заказах.</li>
 * </ol>
 * Заблокированный запрос не уходит: WARN в лог {@code AWMS.SECURITY} — метод, хост, путь и причина, без заголовков
 * (там {@code X-API-Key}), тела и строки запроса. Запись {@code OUTBOUND_BLOCKED} в журнал профиля добавит B6.
 * Разрешённый — событие {@code OUTBOUND_SENT} в журнал доступа: хост и путь, статус и размер ответа.
 *
 * <p>Клиент без функции ({@link #unbound}) не пропускает ничего: так закрыт любой RestTemplate и RestClient,
 * собранный из построителей контекста (см. {@link DefaultDeny}), пока в нём нет сторожа своей функции.
 * Переходы по редиректам запрещены ({@link #requestFactory()}): иначе ответ 3xx увёл бы GET на хост мимо проверки.
 */
public final class OutboundGuard implements ClientHttpRequestInterceptor {

    /** Таймаут соединения исходящих запросов */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Таймаут чтения ответа */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private static final Logger SECURITY = LoggerFactory.getLogger("AWMS.SECURITY");
    /** Предел длины адреса в строке лога и журнала */
    private static final int LOG_MAX = 300;

    /** Функция клиента; null — клиент без функции, отказ всегда */
    private final Feature feature;
    private final FeatureService features;
    private final AccessAudit audit;

    public OutboundGuard(Feature feature, FeatureService features, AccessAudit audit) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.features = Objects.requireNonNull(features, "features");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    private OutboundGuard() {
        this.feature = null;
        this.features = null;
        this.audit = null;
    }

    /** Сторож клиента, не привязанного к функции: любой запрос блокируется */
    public static OutboundGuard unbound() {
        return new OutboundGuard();
    }

    /** Функция клиента или null у {@link #unbound()} */
    public Feature feature() {
        return feature;
    }

    /** Есть ли среди перехватчиков клиента сторож */
    public static boolean isGuarded(List<ClientHttpRequestInterceptor> interceptors) {
        return interceptors.stream().anyMatch(OutboundGuard.class::isInstance);
    }

    /**
     * Фабрика соединений исходящих клиентов: таймауты {@link #CONNECT_TIMEOUT} / {@link #READ_TIMEOUT}, переходы
     * по редиректам выключены для всех методов (SimpleClientHttpRequestFactory следует им для GET)
     */
    public static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new NoRedirectRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        URI uri = request.getURI();
        String method = request.getMethod().name();
        String target = describe(uri);
        if (feature == null) {
            throw blocked(method, target, null, "клиент не привязан к функции (нет сторожа функции)");
        }
        if (!features.isOn(feature)) {
            // Сторож функции: выключена → 404, как у features.require
            SECURITY.warn("Исходящий запрос заблокирован: {} {} — функция {} выключена", method, target, feature.key());
            throw new FeatureDisabledException(feature);
        }
        List<String> allowed = features.ceiling().listParam(CeilingParam.NETWORK_OUTBOUND_ALLOWED);
        String reason = check(uri, allowed);
        if (reason != null) {
            throw blocked(method, target, feature, reason);
        }
        ClientHttpResponse response = execution.execute(request, body);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", method);
        details.put("status", response.getStatusCode().value());
        details.put("size", response.getHeaders().getContentLength());
        audit.record(AccessAudit.Event.OUTBOUND_SENT, feature, target, details);
        return response;
    }

    /**
     * Проверка адреса по списку {@code awms.network.outbound.allowed}: null — разрешено, иначе причина отказа.
     * Элемент списка — {@code хост:порт}; без порта — порт схемы запроса (80/443); IPv6 — в скобках: {@code [::1]:8085}
     */
    public static String check(URI uri, List<String> allowed) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "схема «" + scheme + "» не допускается (только http и https)";
        }
        // Список не задан — исходящие не ограничены, как до выключателей; ограничение включается файлом потолка
        if (allowed.isEmpty()) return null;
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return "в адресе нет хоста";
        }
        host = host.toLowerCase(Locale.ROOT);
        int defaultPort = scheme.equals("https") ? 443 : 80;
        int port = uri.getPort() < 0 ? defaultPort : uri.getPort();
        for (String item : allowed) {
            HostPort entry = HostPort.parse(item, defaultPort);
            if (entry != null && entry.host().equals(host) && entry.port() == port) return null;
        }
        return allowed.isEmpty()
                ? "список awms.network.outbound.allowed пуст: исходящие запросы запрещены потолком"
                : "хоста " + host + ":" + port + " нет в awms.network.outbound.allowed";
    }

    /** Адрес для лога и журнала: схема, хост, порт и путь — без учётных данных, строки запроса и фрагмента */
    public static String describe(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "?" : uri.getHost().toLowerCase(Locale.ROOT);
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return forLog(scheme + "://" + host + port + path);
    }

    private static OutboundBlockedException blocked(String method, String target, Feature feature, String reason) {
        SECURITY.warn("Исходящий запрос заблокирован: {} {} (функция {}) — {}", method, target,
                feature == null ? "не указана" : feature.key(), reason);
        return new OutboundBlockedException("Исходящий запрос к " + target + " заблокирован: " + reason);
    }

    /** Управляющие символы — кодом (%XX или \\uXXXX), длина ограничена: строку лога не подделать */
    private static String forLog(String value) {
        StringBuilder out = new StringBuilder(Math.min(value.length(), LOG_MAX) + 8);
        for (int i = 0; i < value.length(); i++) {
            if (out.length() >= LOG_MAX) {
                out.append('…');
                break;
            }
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) out.append(String.format("%%%02X", (int) c));
            else if (c == 0x85 || c == 0x2028 || c == 0x2029) out.append(String.format("\\u%04X", (int) c));
            else out.append(c);
        }
        return out.toString();
    }

    /** Элемент списка разрешённых: хост в нижнем регистре и порт */
    record HostPort(String host, int port) {

        /** Разбор {@code host:port}, {@code host} (порт по умолчанию), {@code [v6]:port}; ошибка — null */
        static HostPort parse(String item, int defaultPort) {
            String text = item == null ? "" : item.trim().toLowerCase(Locale.ROOT);
            if (text.isEmpty()) return null;
            String host;
            String port = null;
            if (text.startsWith("[")) {
                int close = text.indexOf(']');
                if (close < 0) return null;
                host = text.substring(0, close + 1);
                String rest = text.substring(close + 1);
                if (!rest.isEmpty()) {
                    if (!rest.startsWith(":")) return null;
                    port = rest.substring(1);
                }
            } else {
                int colon = text.lastIndexOf(':');
                if (colon >= 0 && text.indexOf(':') != colon) return null;   // IPv6 без скобок
                host = colon < 0 ? text : text.substring(0, colon);
                if (colon >= 0) port = text.substring(colon + 1);
            }
            if (host.isEmpty()) return null;
            if (port == null) return new HostPort(host, defaultPort);
            try {
                int n = Integer.parseInt(port);
                return n > 0 && n <= 65535 ? new HostPort(host, n) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** Запрос не отправлен: адреса нет в списке разрешённых или клиент без функции */
    public static class OutboundBlockedException extends IOException {
        public OutboundBlockedException(String message) {
            super(message);
        }
    }

    /** Без переходов по редиректам: сторож проверил только исходный адрес */
    private static final class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }

    /**
     * Закрыто по умолчанию: RestTemplate и RestClient, собранные из построителей контекста, получают сторож
     * {@link #unbound()}, если сторожа своей функции в них нет. RestTemplate проверяется после добавления
     * перехватчиков ({@code additionalInterceptors} в RestTemplateConfig и SyncService проходят), RestClient — при
     * создании построителя: клиенту функции нужно заменить сторож своим. Ядро: работает при любом потолке.
     */
    @Configuration(proxyBeanMethods = false)
    static class DefaultDeny {

        @Bean
        RestTemplateCustomizer outboundGuardRestTemplateCustomizer() {
            return restTemplate -> {
                if (!isGuarded(restTemplate.getInterceptors())) restTemplate.getInterceptors().add(unbound());
            };
        }

        @Bean
        RestClientCustomizer outboundGuardRestClientCustomizer() {
            return builder -> builder.requestInterceptors(list -> {
                if (!isGuarded(list)) list.add(unbound());
            });
        }
    }
}
