package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.GateRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Списки правил доступа по потолку и матрица ролей для отчёта: состав при isolated, standard и full, мёртвые
 * правила удалены, список колбэков берётся из реестра, роли функций — с HEAD и родителями. Запись роли SECURITY
 * сверяется со всеми маршрутами контроллеров.
 */
class PublicEndpointsTest {

    private static FeatureSnapshot ceiling(String... keyValues) {
        Map<String, Object> properties = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) properties.put(keyValues[i], keyValues[i + 1]);
        return FeatureResolver.fromProperties(properties);
    }

    private static List<String> specs(List<PublicEndpoints.Endpoint> endpoints) {
        return endpoints.stream().map(PublicEndpoints.Endpoint::spec).toList();
    }

    @Test
    void isolatedAndStandardHaveNoBoardSurface() {
        for (String preset : List.of("isolated", "standard")) {
            FeatureSnapshot s = ceiling("awms.features.preset", preset);
            assertThat(PublicEndpoints.anonymous(s)).isEmpty();
            assertThat(PublicEndpoints.machine(s)).isEmpty();
            assertThat(specs(PublicEndpoints.csrfIgnored(s)))
                    .containsExactly("/api/auth/login", "/api/auth/refresh_token", "/csrf");
            assertThat(specs(PublicEndpoints.withoutSignIn(s)))
                    .containsExactly("/error", "/api/auth/*", "/logout", "/csrf", "GET /api/features/public");
        }
        // Правила «только после входа» от потолка не зависят: второй рубеж за воротами
        assertThat(specs(PublicEndpoints.signedIn()))
                .contains("/api/station-plans/**", "/api/gpp/**", "/api/nomenclature/models/3d/**");
    }

    @Test
    void fullKeepsLegacySurfaceWithoutDeadRules() {
        FeatureSnapshot s = ceiling("awms.features.preset", "full");
        List<String> anonymous = specs(PublicEndpoints.anonymous(s));
        assertThat(anonymous).contains("GET /api/locations/hierarchy", "POST /api/stations/static/filtered",
                "GET /api/stations/dynamic/**", "/uploads/**", "/ws-stations/**", "GET /api/nomenclature/**",
                "GET /api/stations/crud/**", "/api/orders/**", "/api/tkp/**", "/api/dashboard/**");
        List<String> all = new ArrayList<>(specs(PublicEndpoints.withoutSignIn(s)));
        all.addAll(specs(PublicEndpoints.csrfIgnored(s)));
        assertThat(all).noneMatch(spec -> spec.contains("/api/locations/hierarchy/first")
                || spec.contains("/photo") || spec.equals("POST /api/stations") || spec.contains("/api/users")
                || spec.contains("animal_card") || spec.contains("/api/docs/") || spec.contains("/api/information")
                || spec.contains("/api/notification") || spec.contains("/api/sync"));
        assertThat(specs(PublicEndpoints.csrfIgnored(s))).contains("/ws-stations/**", "/api/orders/**",
                "/api/tkp/**", "/api/dashboard/**", "POST /api/orders/*/status");
    }

    @Test
    void callbacksComeFromRegistryOnly() {
        FeatureSnapshot full = ceiling("awms.features.preset", "full", "awms.features.anonymous.enabled", "false");
        List<String> registry = Feature.ORDERS.callbacks().stream().map(GateRule::spec).toList();
        assertThat(specs(PublicEndpoints.machine(full))).containsExactlyElementsOf(registry);
        // Без режима табло без входа и без CSRF — только колбэки, не заказы целиком
        assertThat(PublicEndpoints.anonymous(full)).isEmpty();
        assertThat(specs(PublicEndpoints.csrfIgnored(full))).containsAll(registry)
                .doesNotContain("/api/orders/**", "/api/tkp/**", "/api/dashboard/**");
        // Заказы выключены — колбэков нет
        assertThat(PublicEndpoints.machine(ceiling("awms.features.preset", "full", "awms.features.orders.enabled",
                "false"))).isEmpty();
    }

    @Test
    void roleRulesCoverHeadMultipartAndChildren() {
        assertThat(PublicEndpoints.roleRules(ceiling("awms.features.preset", "full"))).isEmpty();

        FeatureSnapshot s = ceiling("awms.features.preset", "full", "awms.features.video.roles", "ADMIN",
                "awms.features.uploads.roles", "ADMIN");
        List<PublicEndpoints.RoleRule> rules = PublicEndpoints.roleRules(s);
        // Роли родителя: правила всех детей видео тоже под ролями
        assertThat(rules).extracting(r -> r.feature().key())
                .contains("video", "video.live", "video.download", "video.clips")
                .doesNotContain("export", "orders");
        // Пути mixed ролями не закрываются (файл на них проверяет сторож обработчика)
        assertThat(rules).noneMatch(r -> r.rule().kind() == GateRule.Kind.MIXED);

        PublicEndpoints.RoleRule live = rules.stream()
                .filter(r -> r.rule().spec().equals("GET /api/video/live")).findFirst().orElseThrow();
        assertThat(live.matcher().matches(request("GET", "/api/video/live", null))).isTrue();
        assertThat(live.matcher().matches(request("HEAD", "/api/video/live", null))).isTrue();
        assertThat(live.matcher().matches(request("POST", "/api/video/live", null))).isFalse();

        PublicEndpoints.RoleRule images = rules.stream()
                .filter(r -> r.rule().spec().equals("POST /api/nomenclature/*/images")).findFirst().orElseThrow();
        assertThat(images.matcher().matches(request("POST", "/api/nomenclature/1/images", "multipart/form-data; boundary=x")))
                .isTrue();
        assertThat(images.matcher().matches(request("POST", "/api/nomenclature/1/images", "application/json"))).isFalse();
    }

    @Test
    void anonymousNeverMatchesARole() {
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(AppRoles.hasAny(anonymous, Set.of("ANONYMOUS"))).isFalse();
        FeatureSnapshot s = ceiling("awms.features.preset", "standard", "awms.features.export.roles", "ANONYMOUS");
        assertThat(PublicEndpoints.rolesAllow(s, Feature.EXPORT, anonymous)).isFalse();
        assertThat(PublicEndpoints.rolesAllow(s, Feature.UPLOADS, anonymous)).isTrue();
    }

    @Test
    void securityWritesOnlyToProfileAuthExportsAndPersonalSettings() {
        List<String> writable = specs(PublicEndpoints.securityWritable());
        assertThat(writable).contains("/api/security-profile/**", "/api/admin/features/**", "/api/auth/*", "/logout",
                "PATCH /api/**/columns-settings", "PATCH /api/**/settings", "POST /api/user/filters",
                "DELETE /api/user/filters", "POST /ws-stations/**", "POST /api/stations/static/filtered");
        assertThat(writable).containsAll(Feature.EXPORT.rules().stream().map(GateRule::spec).toList());
        // Справочники и станции целиком — нет: только отдельные адреса чтения методом POST
        assertThat(writable).noneMatch(spec -> spec.endsWith("/api/nomenclature/**") || spec.endsWith("/api/stations/**")
                || spec.endsWith("/api/**") || spec.startsWith("PUT ") || spec.equals("/ws-stations/**"));
        // Настройки — только PATCH: PUT и POST на тех же путях закрыты
        RequestMatcher matcher = new OrRequestMatcher(PublicEndpoints.securityWritable().stream()
                .map(PublicEndpoints.Endpoint::matcher).toList());
        assertThat(matcher.matches(request("PATCH", "/api/nomenclature/columns-settings", null))).isTrue();
        assertThat(matcher.matches(request("PUT", "/api/nomenclature/columns-settings", null))).isFalse();
        assertThat(matcher.matches(request("POST", "/api/stations/settings", null))).isFalse();
        assertThat(matcher.matches(request("DELETE", "/api/stations/crud/1", null))).isFalse();
    }

    /**
     * По всем маршрутам контроллеров: роль SECURITY пишет, кроме входа, профиля и выгрузок, только в личные настройки
     * вида (обработчик берёт userId из запроса или пользователя из входа) и в чтение методом POST. И наоборот, каждый
     * маршрут личных настроек ей открыт: иначе её настройки таблиц молча не сохранялись бы (фронт глотает 403)
     */
    @Test
    void securityWritesAmongRealRoutesOnlyPersonalSettingsAndReads() {
        RequestMatcher writable = new OrRequestMatcher(PublicEndpoints.securityWritable().stream()
                .map(PublicEndpoints.Endpoint::matcher).toList());
        List<String> personal = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        List<String> closedSettings = new ArrayList<>();
        for (Route route : writeRoutes()) {
            MockHttpServletRequest request = request(route.method(), route.sample(), null);
            boolean allowed = writable.matches(request);
            boolean settingsPath = route.path().endsWith("settings") || route.path().equals("/api/user/filters");
            if (settingsPath && !allowed) closedSettings.add(route.toString());
            if (!allowed || SECURITY_OWN.stream().anyMatch(route.path()::startsWith)) continue;
            boolean export = PublicEndpoints.securityWritable().stream()
                    .anyMatch(e -> e.feature() == Feature.EXPORT && e.matcher().matches(request));
            if (export || POST_READS.contains(route.handlerName())) continue;
            if (PERSONAL_HANDLERS.contains(route.handler().getName()) && personalSettings(route.handler())) {
                personal.add(route.toString());
                continue;
            }
            violations.add(route.toString());
        }
        assertThat(violations).as("запись роли SECURITY вне личных настроек и чтения").isEmpty();
        assertThat(closedSettings).as("личные настройки, закрытые для роли SECURITY").isEmpty();
        // Сейчас таких маршрутов около сотни: столбцы, фильтры, сортировка таблиц, панель, сохранённые фильтры
        assertThat(personal).hasSizeGreaterThan(90);
    }

    /** Пути, где SECURITY пишет по назначению: вход и выход, профиль безопасности, переключатели */
    private static final List<String> SECURITY_OWN = List.of("/api/auth/", "/logout", "/api/security-profile",
            "/api/admin/features");

    /** Обработчики личных настроек: столбцы, фильтры, сортировка, всё сразу, панель, сохранённые фильтры */
    private static final Set<String> PERSONAL_HANDLERS = Set.of("saveAllSettings", "saveColumnsSettings",
            "saveFiltersSettings", "saveSortSettings", "saveColumns", "saveFilters", "saveSort", "saveSettings",
            "saveUserFilters", "deleteUserFilters");

    /** Чтение методом POST */
    private static final Set<String> POST_READS = Set.of("StationController.getFilteredStaticStations",
            "StationController.getFilteredDynamicStations", "NomenclatureController.calculateCompatibility");

    /** Маршрут записи контроллера: метод, путь, пример пути с подставленными переменными, обработчик */
    private record Route(String method, String path, String sample, Method handler) {

        String handlerName() {
            return handler.getDeclaringClass().getSimpleName() + "." + handler.getName();
        }

        @Override
        public String toString() {
            return method + " " + path + " (" + handlerName() + ")";
        }
    }

    /** Настройки пользователя: userId из запроса или пользователь из входа */
    private static boolean personalSettings(Method handler) {
        for (Parameter p : handler.getParameters()) {
            if (Authentication.class.isAssignableFrom(p.getType())) return true;
            RequestParam param = p.getAnnotation(RequestParam.class);
            if (param == null) continue;
            String name = !param.name().isEmpty() ? param.name() : !param.value().isEmpty() ? param.value() : p.getName();
            if ("userId".equals(name)) return true;
        }
        return false;
    }

    private static List<Route> writeRoutes() {
        Set<String> writes = Set.of("POST", "PUT", "PATCH", "DELETE");
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        List<Route> routes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.example.dinamika_back")) {
            Class<?> type = ClassUtils.resolveClassName(bd.getBeanClassName(), PublicEndpointsTest.class.getClassLoader());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] prefixes = base == null || base.path().length == 0 ? new String[]{""} : base.path();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                List<String> methods = mapping.method().length == 0 ? List.copyOf(writes)
                        : Arrays.stream(mapping.method()).map(RequestMethod::name).filter(writes::contains).toList();
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                for (String prefix : prefixes) {
                    for (String path : paths) {
                        String full = prefix + (path.isEmpty() || path.startsWith("/") ? path : "/" + path);
                        for (String m : methods) {
                            routes.add(new Route(m, full, full.replaceAll("\\{[^}]*}", "1"), method));
                        }
                    }
                }
            }
        }
        assertThat(routes).as("маршруты записи").hasSizeGreaterThan(200);
        return routes;
    }

    @Test
    void roleMatrixFollowsCeiling() {
        FeatureSnapshot s = ceiling("awms.features.preset", "standard", "awms.features.export.roles", "ADMIN,AUDITOR",
                "awms.features.dashboards.audit.roles", "DIRECTOR");
        Map<String, PublicEndpoints.RoleRights> matrix = new HashMap<>();
        for (PublicEndpoints.RoleRights row : PublicEndpoints.roleMatrix(s)) matrix.put(row.role(), row);

        assertThat(PublicEndpoints.roleMatrix(s)).extracting(PublicEndpoints.RoleRights::role)
                .containsExactly("ADMIN", "DIRECTOR", "OPERATOR", "SECURITY", "AUDITOR");
        assertThat(matrix.get("ADMIN").features()).containsEntry("export", true).containsEntry("dashboards.audit", false)
                .doesNotContainKey("video");
        assertThat(matrix.get("DIRECTOR").features()).containsEntry("export", false).containsEntry("dashboards.audit", true);
        assertThat(matrix.get("AUDITOR").features()).containsEntry("export", true).containsEntry("map.plan", true);
        assertThat(matrix.get("AUDITOR").title()).isEqualTo("AUDITOR");

        PublicEndpoints.RoleRights security = matrix.get("SECURITY");
        assertThat(security.title()).isEqualTo("Администратор ИБ");
        assertThat(security.readOnly()).isTrue();
        assertThat(security.securityProfile()).isTrue();
        assertThat(security.manager()).isFalse();
        assertThat(security.switchesDisable()).isTrue();
        assertThat(security.switchesEnable()).isTrue();

        assertThat(matrix.get("ADMIN").manager()).isTrue();
        assertThat(matrix.get("ADMIN").readOnly()).isFalse();
        assertThat(matrix.get("ADMIN").switchesDisable()).isTrue();
        assertThat(matrix.get("ADMIN").switchesEnable()).isFalse();
        assertThat(matrix.get("OPERATOR").securityProfile()).isFalse();
        assertThat(matrix.get("OPERATOR").switchesDisable()).isFalse();

        // Режим full: включают обратно ещё роли из awms.features.admin.roles; режим off — переключателей нет
        FeatureSnapshot full = ceiling("awms.features.preset", "standard", "awms.features.admin.mode", "full",
                "awms.features.admin.roles", "ADMIN");
        assertThat(PublicEndpoints.roleMatrix(full)).filteredOn(r -> r.role().equals("ADMIN"))
                .singleElement().matches(PublicEndpoints.RoleRights::switchesEnable);
        FeatureSnapshot off = ceiling("awms.features.preset", "standard", "awms.features.admin.mode", "off");
        assertThat(PublicEndpoints.roleMatrix(off))
                .noneMatch(r -> r.switchesDisable() || r.switchesEnable());
    }

    private static MockHttpServletRequest request(String method, String path, String contentType) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (contentType != null) request.setContentType(contentType);
        return request;
    }
}
