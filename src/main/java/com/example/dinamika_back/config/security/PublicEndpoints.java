package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.GateRule;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Списки правил доступа (раздел 6.6 проекта) и матрица ролей для отчёта «Профиль безопасности» (раздел 9.2, п. 12).
 * Списки — данные, а не код: их печатает отчёт (анонимные адреса, CSRF-исключения), по ним же слой правил потолка
 * ({@link CeilingAccessFilter}) сужает доступ, а SecurityConfig собирает CSRF-исключения. Прежние правила доступа
 * SecurityConfig остаются как в main и здесь только описаны ({@link #anonymous}). Каждое правило слоя — метод
 * и шаблон AntPathMatcher: строковый {@code requestMatchers} при Spring MVC разбирается PathPatternParser, а тот
 * не допускает {@code **} в середине пути ({@code /api/**}{@code /export-pdf}).
 *
 * <p>Состав зависит от потолка: разрешающее правило функции есть, только когда функция разрешена потолком.
 * Переключатели администратора (этап 2) правила не меняют — выключенную ими функцию закрывают ворота (404 до Security).
 * Список колбэков внешней системы здесь не дублируется: он один, в реестре {@code Feature.ORDERS.callbacks()}.
 */
public final class PublicEndpoints {

    /**
     * Правило доступа.
     *
     * @param method  метод или null — любой
     * @param pattern шаблон AntPathMatcher
     * @param feature функция, без которой правила нет; null — ядро
     */
    public record Endpoint(String method, String pattern, Feature feature) {

        public Endpoint {
            Objects.requireNonNull(pattern, "pattern");
            method = method == null ? null : method.toUpperCase(Locale.ROOT);
        }

        /** Запись как в реестре: {@code "GET /api/locations/hierarchy"} */
        public String spec() {
            return method == null ? pattern : method + " " + pattern;
        }

        /** Матчер Spring Security: ровно этот метод (GET не включает HEAD — правило разрешающее) */
        public RequestMatcher matcher() {
            return method == null ? AntPathRequestMatcher.antMatcher(pattern)
                    : AntPathRequestMatcher.antMatcher(HttpMethod.valueOf(method), pattern);
        }

        @Override
        public String toString() {
            return feature == null ? spec() : spec() + " [" + feature.key() + "]";
        }
    }

    /**
     * Правило ролей функции из потолка: путь функции (или её детей), доступный только ролям функции, её родителей
     * и функций из requires.
     *
     * @param feature функция, чьё правило совпало
     * @param rule    правило ворот функции
     * @param matcher матчер: правило GET — это GET и HEAD, правило multipart — только запросы multipart/*
     */
    public record RoleRule(Feature feature, GateRule rule, RequestMatcher matcher) {
    }

    /**
     * Строка раздела «Входящие» отчёта об анонимном доступе: факт, значение и оценка (true — в порядке,
     * false — риск, null — без оценки) с пояснением
     */
    public record AccessFact(String title, String value, Boolean ok, String note) {
    }

    /**
     * Строка матрицы ролей «роль → что может» при этом потолке.
     *
     * @param role            роль без префикса ROLE_
     * @param title           название роли
     * @param features        функции, разрешённые потолком, в порядке реестра: доступна ли роли (с учётом ролей
     *                        функции, её родителей и requires)
     * @param readOnly        вне профиля безопасности и переключателей только читает ({@link SecurityRoleReadOnlyFilter})
     * @param manager         права руководителя: видео станций, настройки камер, правка карты и ГПП
     * @param securityProfile видит страницу «Безопасность» и отчёт
     * @param switchesDisable выключает функции переключателями (этап 2: {@code awms.features.admin.*})
     * @param switchesEnable  включает обратно (этап 2)
     */
    public record RoleRights(String role, String title, Map<String, Boolean> features, boolean readOnly,
                             boolean manager, boolean securityProfile, boolean switchesDisable,
                             boolean switchesEnable) {
    }

    /**
     * Справочники: читать (GET) без входа можно только в режиме табло, изменять — после входа и с CSRF-токеном.
     * Тот же список, что REFERENCE_BOOKS в SecurityConfig (сверяет PublicEndpointsTest)
     */
    static final List<String> REFERENCE_BOOKS = List.of(
            "/api/nomenclature/**",
            "/api/templates/**",
            "/api/enterprises/**",
            "/api/workshops/**",
            "/api/sections/**",
            "/api/employees/**",
            "/api/station-types/**",
            "/api/station-manufacturers/**",
            "/api/station-models/**",
            "/api/station-configurations/**",
            "/api/stations/crud/**");

    /** Всегда без входа: вход, страница ошибки, выход, CSRF-токен, флаги функций до входа (pwa, passwords) */
    private static final List<Endpoint> ALWAYS = List.of(
            new Endpoint(null, "/error", null),
            new Endpoint(null, "/api/auth/*", null),
            new Endpoint(null, "/logout", null),
            new Endpoint(null, "/csrf", null),
            new Endpoint("GET", "/api/features/public", null));

    /**
     * Только после входа, даже в режиме табло. Стоят выше анонимных правил: иначе 3D-модели (файлы
     * конструкторской документации) перехватил бы GET справочника номенклатуры. {@code /api/nomenclature/models}
     * без {@code /3d} — справочник моделей брендов, он остаётся открытым вместе со справочником
     */
    private static final List<Endpoint> SIGNED_IN = List.of(
            new Endpoint(null, "/api/nomenclature/*/models", Feature.MODEL3D),
            new Endpoint(null, "/api/nomenclature/models/3d/**", Feature.MODEL3D),
            // План цеха и расположение станций: смотреть — после входа, править — руководителю (проверка в контроллере)
            new Endpoint(null, "/api/station-plans/**", Feature.MAP_PLAN),
            // ГПП: смотреть — после входа, сохранять — руководителю (проверка в контроллере)
            new Endpoint(null, "/api/gpp/**", Feature.GPP));

    /**
     * Режим табло (функция {@code anonymous}): разрешающие правила SecurityConfig в том виде, в каком они в main, —
     * в том числе правила без маршрута (иерархия /first, фото локаций, {@code POST /api/stations}): правила
     * сервера не переписываются, а отчёт показывает то, что открыто на самом деле. Станции-шкафы и табло ходят сюда
     * без входа (решение пользователя: по умолчанию как сейчас), в том числе {@code /api/orders/**} и
     * {@code /api/tkp/**} целиком. Без {@code anonymous} слой потолка закрывает всё это анониму (401), без входа
     * остаются только служебные адреса и колбэки внешней системы из реестра
     */
    private static final List<Endpoint> LEGACY_ANONYMOUS = buildLegacyAnonymous();

    /**
     * Пути личных настроек вида (только PATCH): столбцы, фильтры и сортировка таблиц, набор графиков панели.
     * Объявлены раньше SECURITY_WRITABLE: статические поля заполняются по порядку
     */
    static final List<String> PERSONAL_SETTINGS = List.of(
            "/api/**/columns-settings",
            "/api/**/filters-settings",
            "/api/**/sort-settings",
            "/api/**/settings");

    /**
     * Роль SECURITY пишет только сюда: профиль безопасности, переключатели (этап 2), вход и выход. Остальное здесь
     * общих данных не меняет: выгрузки ({@code Feature.EXPORT}: сервер лишь рендерит присланную таблицу), чтение
     * методом POST, личные настройки вида и SockJS-транспорты живых обновлений
     */
    private static final List<Endpoint> SECURITY_WRITABLE = buildSecurityWritable();
    /**
     * С телефона и планшета: как у роли SECURITY, но без профиля безопасности и переключателей. Исключение —
     * события просмотра и печати отчёта ({@code /events}): это запись аудита о том, кто смотрел, а не изменение данных
     */
    private static final List<Endpoint> VIEW_ONLY_WRITABLE = java.util.stream.Stream.concat(
            SECURITY_WRITABLE.stream()
                    .filter(e -> !e.pattern().startsWith("/api/security-profile") && !e.pattern().startsWith("/api/admin/features")),
            java.util.stream.Stream.of(new Endpoint("POST", "/api/security-profile/events", null)))
            .toList();

    private PublicEndpoints() {
    }

    private static List<Endpoint> buildLegacyAnonymous() {
        List<Endpoint> list = new ArrayList<>();
        list.add(new Endpoint("GET", "/api/locations/hierarchy", null));
        list.add(new Endpoint("GET", "/api/locations/hierarchy/first", null));      // маршрута нет
        list.add(new Endpoint("POST", "/api/stations/static/filtered", null));
        list.add(new Endpoint("POST", "/api/stations/dynamic/filtered", null));
        list.add(new Endpoint("GET", "/api/locations/*/photo", null));             // маршрута нет
        list.add(new Endpoint(null, "/uploads/**", null));
        list.add(new Endpoint("GET", "/api/stations/static", null));
        list.add(new Endpoint("GET", "/api/stations/static/**", null));
        list.add(new Endpoint("GET", "/api/stations/dynamic", null));
        list.add(new Endpoint("GET", "/api/stations/dynamic/**", null));
        list.add(new Endpoint("POST", "/api/stations", null));                     // маршрута нет
        list.add(new Endpoint(null, "/ws-stations/**", Feature.WEBSOCKET));
        for (String book : REFERENCE_BOOKS) list.add(new Endpoint("GET", book, null));
        list.add(new Endpoint(null, "/api/orders/**", Feature.ORDERS));
        list.add(new Endpoint(null, "/api/tkp/**", Feature.ORDERS));
        list.add(new Endpoint(null, "/api/dashboard/**", Feature.DASHBOARDS));
        return List.copyOf(list);
    }

    private static List<Endpoint> buildSecurityWritable() {
        List<Endpoint> list = new ArrayList<>();
        list.add(new Endpoint(null, "/api/security-profile", null));
        list.add(new Endpoint(null, "/api/security-profile/**", null));
        list.add(new Endpoint(null, "/api/admin/features", null));
        list.add(new Endpoint(null, "/api/admin/features/**", null));
        list.add(new Endpoint(null, "/api/auth/*", null));
        list.add(new Endpoint(null, "/logout", null));
        for (GateRule rule : Feature.EXPORT.rules()) {
            list.add(new Endpoint(rule.method(), rule.pattern(), Feature.EXPORT));
        }
        // Личные настройки вида и сохранённые фильтры: пишутся для пользователя, данные установки не меняют. Пути
        // «…settings» заняты только ими (проверяет PublicEndpointsTest по всем маршрутам контроллеров). userId
        // контроллеры берут из запроса — эта слабость общая для всех ролей, роль SECURITY её не расширяет
        for (String pattern : PERSONAL_SETTINGS) list.add(new Endpoint("PATCH", pattern, null));
        list.add(new Endpoint("POST", "/api/user/filters", null));
        list.add(new Endpoint("DELETE", "/api/user/filters", null));
        // Чтение методом POST: фильтр станций и расчёт совместимости ничего не сохраняют
        list.add(new Endpoint("POST", "/api/stations/static/filtered", null));
        list.add(new Endpoint("POST", "/api/stations/dynamic/filtered", null));
        list.add(new Endpoint("POST", "/api/nomenclature/calculate-compatibility", null));
        // SockJS-транспорты xhr, xhr_send, xhr_streaming: без них за прокси без WebSocket нет живых обновлений.
        // Записи через них нет: кадры SEND отбрасывает перехватчик WebSocketConfig, @MessageMapping в коде нет
        list.add(new Endpoint("POST", "/ws-stations/**", Feature.WEBSOCKET));
        return List.copyOf(list);
    }

    // ===================== Списки при данном потолке =====================

    /** Всегда без входа (служебные адреса) */
    public static List<Endpoint> always() {
        return ALWAYS;
    }

    /**
     * Без входа для внешних систем: колбэки SaaS из реестра (при {@code orders}; ключ проверяет
     * OrdersCallbackKeyFilter) и приём тестовой синхронизации (при {@code testsync})
     */
    public static List<Endpoint> machine(FeatureSnapshot ceiling) {
        List<Endpoint> list = new ArrayList<>();
        if (ceiling.isOn(Feature.ORDERS)) {
            for (GateRule rule : Feature.ORDERS.callbacks()) {
                list.add(new Endpoint(rule.method(), rule.pattern(), Feature.ORDERS));
            }
        }
        if (ceiling.isOn(Feature.TESTSYNC)) list.add(new Endpoint("POST", "/api/sync/**", Feature.TESTSYNC));
        return List.copyOf(list);
    }

    /**
     * Только после входа даже в режиме табло. Не зависит от потолка: у выключенной функции путь закрывают ворота
     * (404), а правило остаётся вторым рубежом — например, у ГПП до пакета B8 бины есть и при выключенной функции
     */
    public static List<Endpoint> signedIn() {
        return SIGNED_IN;
    }

    /** Режим табло: пусто, если {@code anonymous} выключен потолком */
    public static List<Endpoint> anonymous(FeatureSnapshot ceiling) {
        return ceiling.isOn(Feature.ANONYMOUS) ? enabledOnly(LEGACY_ANONYMOUS, ceiling) : List.of();
    }

    /** Всё, что открыто без входа при этом потолке: служебные адреса, внешние системы, режим табло (для отчёта) */
    public static List<Endpoint> withoutSignIn(FeatureSnapshot ceiling) {
        List<Endpoint> list = new ArrayList<>(ALWAYS);
        list.addAll(machine(ceiling));
        list.addAll(anonymous(ceiling));
        return List.copyOf(list);
    }

    /**
     * Адреса без проверки CSRF-токена — прежний список SecurityConfig, суженный по функциям: всегда вход,
     * обновление токена и выдача CSRF; при {@code websocket} — SockJS; при {@code testsync} — приём синхронизации;
     * в режиме табло — как раньше заказы, ТКП и панели целиком; без него — только колбэки заказов из реестра.
     * Шире прежнего списка не бывает. При full (и без файла потолка) список совпадает с прежним, кроме
     * {@code /api/sync/**}: testsync выключен, и эти пути отвечают 404 воротами раньше проверки CSRF
     */
    public static List<Endpoint> csrfIgnored(FeatureSnapshot ceiling) {
        List<Endpoint> list = new ArrayList<>();
        list.add(new Endpoint(null, "/api/auth/login", null));
        list.add(new Endpoint(null, "/api/auth/refresh_token", null));
        list.add(new Endpoint(null, "/csrf", null));
        if (ceiling.isOn(Feature.WEBSOCKET)) list.add(new Endpoint(null, "/ws-stations/**", Feature.WEBSOCKET));
        if (ceiling.isOn(Feature.TESTSYNC)) list.add(new Endpoint(null, "/api/sync/**", Feature.TESTSYNC));
        if (ceiling.isOn(Feature.ANONYMOUS)) {
            // Справочники здесь не перечислены: их изменения идут только с CSRF-токеном,
            // фронтенд добавляет X-XSRF-TOKEN ко всем не-GET запросам (AxiosService).
            // Колбэки заказов отдельно не нужны: они внутри /api/orders/** и /api/tkp/**
            if (ceiling.isOn(Feature.ORDERS)) {
                list.add(new Endpoint(null, "/api/orders/**", Feature.ORDERS));
                list.add(new Endpoint(null, "/api/tkp/**", Feature.ORDERS));
            }
            if (ceiling.isOn(Feature.DASHBOARDS)) list.add(new Endpoint(null, "/api/dashboard/**", Feature.DASHBOARDS));
        } else if (ceiling.isOn(Feature.ORDERS)) {
            for (GateRule rule : Feature.ORDERS.callbacks()) {
                list.add(new Endpoint(rule.method(), rule.pattern(), Feature.ORDERS));
            }
        }
        return List.copyOf(list);
    }

    /** Куда пишет роль SECURITY (все прочие методы, кроме GET, HEAD и OPTIONS, ей запрещены) */
    public static List<Endpoint> securityWritable() {
        return SECURITY_WRITABLE;
    }

    /**
     * Что меняющими методами можно с телефона, ТСД и планшета ({@link MobileReadOnlyFilter}): вход и выход,
     * выгрузки, чтение методом POST, личные настройки вида, транспорты живых обновлений. Управлять установкой
     * (профиль безопасности, переключатели) с телефона нельзя
     */
    public static List<Endpoint> viewOnlyWritable() {
        return VIEW_ONLY_WRITABLE;
    }

    private static List<Endpoint> enabledOnly(List<Endpoint> endpoints, FeatureSnapshot ceiling) {
        return endpoints.stream().filter(e -> e.feature() == null || ceiling.isOn(e.feature())).toList();
    }

    /**
     * Анонимный доступ при этом потолке для отчёта: режим табло, с каких адресов, доверенные прокси и что открыто
     * с любого адреса. Те же параметры, по которым работает {@link CeilingAccessFilter}
     */
    public static List<AccessFact> anonymousAccess(FeatureSnapshot ceiling) {
        boolean board = ceiling.isOn(Feature.ANONYMOUS);
        boolean external = !machine(ceiling).isEmpty();
        List<String> networks = ceiling.listParam(CeilingParam.ANONYMOUS_ALLOWED_NETWORKS);
        List<String> proxies = ceiling.listParam(CeilingParam.TRUSTED_PROXIES);
        String networksKey = CeilingParam.ANONYMOUS_ALLOWED_NETWORKS.key();
        List<AccessFact> facts = new ArrayList<>();
        facts.add(new AccessFact("Анонимный доступ (режим табло, функция anonymous)", board ? "включён" : "выключен",
                board ? null : true, board ? "станции-шкафы и табло читают API без входа: иерархия, станции, файлы "
                + "справочников, справочники, панели, живые обновления — список «Доступно без входа»"
                : "без входа — только служебные адреса" + (external ? " и колбэки внешних систем" : "")));
        String scope = board ? "станции, табло" + (external ? " и внешние системы" : "")
                : external ? "колбэки внешних систем" : null;
        if (scope == null) {
            facts.add(new AccessFact("Анонимно — с адресов (" + networksKey + ")", "не применяется", null,
                    "анонимных запросов, кроме служебных адресов, нет"));
        } else if (networks.isEmpty()) {
            facts.add(new AccessFact("Анонимно — с адресов (" + networksKey + ")", "с любых", false,
                    scope + " — с любого адреса сети. Ограничить адресами устройств цеха: строка " + networksKey
                            + " в файле потолка (как узнать адреса — deploy/INSTALL.md, раздел 11)"));
        } else {
            facts.add(new AccessFact("Анонимно — с адресов (" + networksKey + ")", String.join(", ", networks), true,
                    scope + " — только с этих адресов, с остальных без входа — 401"));
        }
        facts.add(new AccessFact("Доверенные прокси (" + CeilingParam.TRUSTED_PROXIES.key() + ")",
                proxies.isEmpty() ? "нет" : String.join(", ", proxies), null, proxies.isEmpty()
                ? "адрес клиента — всегда адрес соединения, X-Forwarded-For и X-Real-IP не читаются"
                : "адрес клиента из X-Forwarded-For и X-Real-IP — только от этих адресов, от остальных — адрес "
                + "соединения; подложный заголовок клиента не учитывается"));
        facts.add(new AccessFact("С любого адреса без входа",
                ALWAYS.stream().map(Endpoint::spec).collect(Collectors.joining(", ")), null,
                "вход, выход, CSRF-токен, флаги функций до входа и страница ошибки"));
        return List.copyOf(facts);
    }

    // ===================== Роли функций из потолка =====================

    /**
     * Правила ролей: для каждой разрешённой потолком функции, у которой роли заданы ей самой, родителю или функции
     * из requires, — матчеры её правил ворот. Правило GET — это GET и HEAD (как в воротах); правило multipart
     * функции uploads — только запросы multipart/*. Пути mixed (штрихкоды, поставки) здесь не закрываются: файл
     * на них при чужой роли отклоняет сторож обработчика (403), запрос без файла проходит
     */
    public static List<RoleRule> roleRules(FeatureSnapshot ceiling) {
        List<RoleRule> result = new ArrayList<>();
        for (Feature f : Feature.real()) {
            if (!ceiling.isOn(f) || !hasRoleLimit(ceiling, f)) continue;
            for (GateRule rule : f.rules()) {
                if (rule.kind() == GateRule.Kind.MIXED) continue;
                result.add(new RoleRule(f, rule, matcher(rule)));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Пускают ли роли пользователя к функции: роли самой функции, её родителей и функций из requires (пусто — все
     * вошедшие). Аноним с ролями функции не совпадает никогда. Включённость функции здесь не проверяется — её
     * проверяют ворота
     */
    public static boolean rolesAllow(FeatureSnapshot ceiling, Feature feature, Authentication authentication) {
        Set<String> roles = ceiling.roles(feature);
        if (!roles.isEmpty() && !AppRoles.hasAny(authentication, roles)) return false;
        Feature parent = feature.parent();
        if (parent != null && !rolesAllow(ceiling, parent, authentication)) return false;
        for (Feature r : feature.requires()) {
            if (!rolesAllow(ceiling, r, authentication)) return false;
        }
        return true;
    }

    private static boolean hasRoleLimit(FeatureSnapshot ceiling, Feature feature) {
        if (!ceiling.roles(feature).isEmpty()) return true;
        for (Feature dependency : feature.dependencies()) {
            if (hasRoleLimit(ceiling, dependency)) return true;
        }
        return false;
    }

    private static RequestMatcher matcher(GateRule rule) {
        List<RequestMatcher> methods = new ArrayList<>();
        if (rule.method() == null) {
            methods.add(AntPathRequestMatcher.antMatcher(rule.pattern()));
        } else {
            methods.add(AntPathRequestMatcher.antMatcher(HttpMethod.valueOf(rule.method()), rule.pattern()));
            // Spring MVC обслуживает HEAD обработчиком @GetMapping: правило GET закрывает и HEAD
            if ("GET".equals(rule.method())) methods.add(AntPathRequestMatcher.antMatcher(HttpMethod.HEAD, rule.pattern()));
        }
        boolean multipartOnly = rule.kind() == GateRule.Kind.MULTIPART;
        return request -> {
            if (multipartOnly && !isMultipart(request)) return false;
            for (RequestMatcher m : methods) {
                if (m.matches(request)) return true;
            }
            return false;
        };
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.trim().toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    // ===================== Матрица ролей =====================

    /**
     * Матрица «роль → что может» при этом потолке (отчёт, раздел 9.2, п. 12): известные коду роли
     * ({@link AppRoles#KNOWN}) и все роли, названные в потолке. Строится из реестра, ролей потолка, правил этого
     * класса и {@link AppRoles}; число пользователей и источник роли (токен или БД) добавляет отчёт
     */
    public static List<RoleRights> roleMatrix(FeatureSnapshot ceiling) {
        Set<String> roles = new LinkedHashSet<>(AppRoles.KNOWN.keySet());
        for (Feature f : Feature.real()) roles.addAll(ceiling.roles(f));
        roles.addAll(ceiling.listParam(CeilingParam.ADMIN_DISABLE_ROLES));
        roles.addAll(ceiling.listParam(CeilingParam.ADMIN_ROLES));

        String mode = ceiling.param(CeilingParam.ADMIN_MODE);
        Set<String> disablers = Set.copyOf(ceiling.listParam(CeilingParam.ADMIN_DISABLE_ROLES));
        Set<String> enablers = new LinkedHashSet<>(AppRoles.FEATURE_ENABLERS);
        if ("full".equals(mode)) enablers.addAll(ceiling.listParam(CeilingParam.ADMIN_ROLES));

        List<RoleRights> result = new ArrayList<>();
        for (String role : roles) {
            Authentication as = UsernamePasswordAuthenticationToken.authenticated(role, null,
                    List.of(new SimpleGrantedAuthority(role)));
            Map<String, Boolean> features = new LinkedHashMap<>();
            for (Feature f : Feature.real()) {
                if (ceiling.isOn(f)) features.put(f.key(), rolesAllow(ceiling, f, as));
            }
            result.add(new RoleRights(role, AppRoles.KNOWN.getOrDefault(role, role),
                    Collections.unmodifiableMap(features),
                    AppRoles.isSecurity(as),
                    AppRoles.isManager(as),
                    AppRoles.hasAny(as, AppRoles.SECURITY_VIEWERS),
                    !"off".equals(mode) && disablers.contains(role),
                    !"off".equals(mode) && enablers.contains(role)));
        }
        return List.copyOf(result);
    }
}
