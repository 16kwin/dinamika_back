package com.example.dinamika_back.features;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт реестра функций: ключи, родители и зависимости, состав пресетов (таблица 4.2), служебные пути,
 * классы-маркеры, списки загрузок и колбэков, соответствие правил ворот реальным маршрутам.
 */
class FeatureCatalogContractPatchCheckTest {

    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(\\.[a-z0-9]+)*");
    /** Зарезервированные сегменты: служебные ключи потолка и атрибуты функций */
    private static final Set<String> RESERVED = Set.of("preset", "ticket", "admin", "file", "enabled", "roles");

    /** Служебные пути: ворота не закрывают их никогда (раздел 6.3, п. 6) */
    private static final List<String> SERVICE_PATHS = List.of("/api/auth/login", "/api/auth/refresh_token",
            "/api/auth/check_password", "/csrf", "/error", "/logout", "/api/features", "/api/features/public",
            "/api/security-profile", "/api/security-profile/events", "/api/security-profile/purge",
            "/api/admin/features");

    /** Таблица 4.2 проекта, столбец I/S/F: изменение пресета — осознанное изменение этой таблицы и проекта */
    private static final String PRESET_TABLE = """
            video               off off on
            video.live          off off on
            video.clips         off off on
            video.recording     off off on
            video.archive       off off on
            video.download      off off on
            video.cameras       off off on
            map                 off on  on
            map.plan            off on  on
            map.edit            off on  on
            model3d             off on  on
            model3d.upload      off on  on
            gpp                 off on  on
            dashboards          off on  on
            dashboards.economic off on  on
            dashboards.audit    off on  on
            dashboards.graph    off off on
            dashboards.screen   off on  on
            orders              off off on
            websocket           off off on
            export              off on  on
            uploads             off on  on
            integrations        off off on
            anonymous           off off on
            pwa                 off off on
            passwords           off off off
            drafts              off on  on
            stubs               off off on
            testsync            off off off
            legacy              off off off
            photoimport         off off off
            """;

    @Test
    void keysAreUniqueValidAndNotReserved() {
        Set<String> seen = new HashSet<>();
        for (Feature f : Feature.values()) {
            assertThat(seen.add(f.key())).as("повтор ключа " + f.key()).isTrue();
            assertThat(f.key()).as(f.name()).matches(KEY);
            for (String segment : f.key().split("\\.")) {
                assertThat(RESERVED).as(f.key()).doesNotContain(segment);
            }
            assertThat(f.title()).as(f.key()).isNotBlank();
            assertThat(Feature.byKey(f.key())).contains(f);
        }
    }

    @Test
    void keysMatchTheRegisteredList() throws IOException {
        List<String> expected = Files.readAllLines(Path.of("src/test/resources/feature-keys.txt"), StandardCharsets.UTF_8)
                .stream().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        assertThat(Arrays.stream(Feature.values()).map(Feature::key).toList())
                .as("реестр изменён: обновите src/test/resources/feature-keys.txt, шаблоны потолка и catalog.ts фронтенда")
                .isEqualTo(expected);
    }

    @Test
    void parentsAndRequiresExistWithoutCycles() {
        for (Feature f : Feature.values()) {
            if (f.parentKey() != null) {
                assertThat(Feature.byKey(f.parentKey())).as("родитель " + f.key()).isPresent();
            }
            for (String key : f.requiresKeys()) {
                assertThat(Feature.byKey(key)).as("requires " + f.key() + " → " + key).isPresent();
                assertThat(key).as("requires не повторяет родителя").isNotEqualTo(f.parentKey());
            }
            assertThat(reaches(f, f, new HashSet<>())).as("цикл зависимостей у " + f.key()).isFalse();
        }
    }

    private static boolean reaches(Feature from, Feature target, Set<Feature> visited) {
        for (Feature d : from.dependencies()) {
            if (d == target) return true;
            if (visited.add(d) && reaches(d, target, visited)) return true;
        }
        return false;
    }

    @Test
    void presetsMatchProjectTable() {
        Map<String, String[]> table = new LinkedHashMap<>();
        for (String line : PRESET_TABLE.strip().split("\n")) {
            String[] cells = line.trim().split("\\s+");
            table.put(cells[0], new String[]{cells[1], cells[2], cells[3]});
        }
        assertThat(table.keySet()).containsExactlyElementsOf(Feature.real().stream().map(Feature::key).toList());
        FeaturePreset[] order = {FeaturePreset.ISOLATED, FeaturePreset.STANDARD, FeaturePreset.FULL};
        for (Feature f : Feature.real()) {
            String[] cells = table.get(f.key());
            for (int i = 0; i < order.length; i++) {
                assertThat(f.isOnIn(order[i]) ? "on" : "off").as(f.key() + " в пресете " + order[i].key())
                        .isEqualTo(cells[i]);
            }
        }
        // Изолированный минимум — ни одной функции
        assertThat(Feature.values()).noneMatch(f -> f.isOnIn(FeaturePreset.ISOLATED));
    }

    @Test
    void presetsAreConsistentWithDependencies() {
        for (FeaturePreset preset : FeaturePreset.values()) {
            FeatureSnapshot s = FeatureResolver.fromProperties(Map.of("awms.features.preset", preset.key()));
            assertThat(s.warnings()).as("пресет " + preset.key() + " включает функцию без её зависимости").isEmpty();
            for (Feature f : Feature.values()) {
                assertThat(s.isOn(f)).as(f.key() + " в " + preset.key()).isEqualTo(f.isOnIn(preset));
            }
        }
    }

    @Test
    void absentFeaturesHaveReasonAndNoRules() {
        List<Feature> absent = Arrays.stream(Feature.values()).filter(Feature::isAbsent).toList();
        assertThat(absent).extracting(Feature::key)
                .containsExactly("ai", "telemetry", "remote", "autoupdate", "push", "cloud", "mail");
        for (Feature f : absent) {
            assertThat(f.absentReason()).as(f.key()).isNotBlank();
            assertThat(f.rules()).as(f.key()).isEmpty();
            assertThat(f.presets()).as(f.key()).isEmpty();
            assertThat(f.parentKey()).as(f.key()).isNull();
        }
    }

    @Test
    void ceilingOnlyFeaturesAsInProject() {
        assertThat(Feature.real().stream().filter(Feature::isCeilingOnly).map(Feature::key).toList())
                .containsExactlyInAnyOrder("websocket", "anonymous", "testsync", "legacy", "photoimport");
        assertThat(Feature.real().stream().filter(Feature::isPublicFlag).map(Feature::key).toList())
                .containsExactlyInAnyOrder("pwa", "passwords");
    }

    @Test
    void serviceEndpointsAreNeverClosed() {
        Set<String> methods = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        for (Feature f : Feature.values()) {
            List<GateRule> rules = new ArrayList<>(f.rules());
            for (GateRule rule : rules) {
                for (String path : SERVICE_PATHS) {
                    for (String method : methods) {
                        assertThat(rule.matches(method, path, "multipart/form-data"))
                                .as(f.key() + " " + rule + " закрывает служебный путь " + method + " " + path).isFalse();
                    }
                }
            }
        }
        // И сами ворота держат их открытыми при любом профиле
        org.springframework.util.AntPathMatcher matcher = new org.springframework.util.AntPathMatcher();
        for (String path : SERVICE_PATHS) {
            assertThat(FeatureGateFilter.ALWAYS_OPEN).as(path).anyMatch(p -> matcher.match(p, path));
        }
    }

    @Test
    void markerClassesArePresentInFullBuild() {
        for (Feature f : Feature.values()) {
            if (f.marker() != null) {
                assertThat(ClassUtils.isPresent(f.marker(), getClass().getClassLoader()))
                        .as("класс-маркер " + f.key() + ": " + f.marker()).isTrue();
            }
        }
    }

    @Test
    void rulesAreWellFormed() {
        for (Feature f : Feature.values()) {
            for (GateRule rule : f.rules()) {
                assertThat(rule.pattern()).as(f.key() + " " + rule)
                        .matches(p -> p.startsWith("/api/") || p.startsWith("/ws-stations/"), "под /api/ или /ws-stations/");
                if (rule.kind() != GateRule.Kind.GATE) {
                    assertThat(f).as("multipart и mixed — только у uploads: " + rule).isEqualTo(Feature.UPLOADS);
                }
                if (rule.audited() != null) {
                    assertThat(rule.method()).as("журнал доступа пишется по ответам GET: " + rule).isIn("GET", null);
                }
            }
            assertThat(f.callbacks().isEmpty() || f == Feature.ORDERS).as("колбэки только у orders").isTrue();
        }
        assertThat(Feature.GPP.rules()).isNotEmpty();
    }

    @Test
    void uploadAndCallbackListsAreComplete() {
        List<String> multipart = Feature.UPLOADS.rules().stream()
                .filter(r -> r.kind() == GateRule.Kind.MULTIPART).map(GateRule::spec).toList();
        assertThat(multipart).hasSize(12).contains("POST /api/nomenclature/*/models",
                "POST /api/station-plans/workshops/*/image", "POST /api/stations/*/documents");
        assertThat(Feature.UPLOADS.rules().stream().filter(r -> r.kind() == GateRule.Kind.MIXED).map(GateRule::spec))
                .containsExactly("POST /api/nomenclature/*/codes", "POST /api/nomenclature/*/supply",
                        "POST /api/suppliers/*/deliveries");
        // Каждый колбэк заказов закрыт воротами orders, когда функция выключена
        assertThat(Feature.ORDERS.callbacks()).isNotEmpty();
        for (GateRule callback : Feature.ORDERS.callbacks()) {
            assertThat(Feature.ORDERS.rules()).as(callback.toString())
                    .anyMatch(r -> r.matches(callback.method(), callback.sampleUrl(), null));
        }
    }

    @Test
    void everyRuleMatchesARealRoute() {
        List<Route> routes = routes();
        assertThat(routes).hasSizeGreaterThan(100);
        List<GateRule> all = new ArrayList<>();
        for (Feature f : Feature.values()) {
            all.addAll(f.rules());
            all.addAll(f.callbacks());
        }
        for (GateRule rule : all) {
            if (rule.pattern().startsWith("/ws-stations/")) continue; // точка SockJS регистрируется WebSocketConfig
            assertThat(routes).as("правило " + rule + " не совпадает ни с одним маршрутом контроллеров")
                    .anyMatch(r -> rule.matchesPath(r.path) && (r.methods.isEmpty()
                            ? true : r.methods.stream().anyMatch(m -> rule.matchesMethod(m.name()))));
        }
    }

    /** Маршруты контроллеров приложения: путь с {переменными} → 1 и методы */
    private static List<Route> routes() {
        // Условия @ConditionalOnFeature сканер не проверяет: в его окружении нет потолка (был бы isolated),
        // а нужны маршруты всех контроллеров, в том числе модулей, выключаемых потолком (видео, заказы, план цеха…)
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(MetadataReader reader) throws IOException {
                return new AnnotationTypeFilter(Controller.class).match(reader, getMetadataReaderFactory());
            }
        };
        List<Route> routes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.example.dinamika_back")) {
            Class<?> type = ClassUtils.resolveClassName(bd.getBeanClassName(), FeatureCatalogContractPatchCheckTest.class.getClassLoader());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] prefixes = base == null || base.path().length == 0 ? new String[]{""} : base.path();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                for (String prefix : prefixes) {
                    for (String path : paths) {
                        String full = (prefix + (path.isEmpty() || path.startsWith("/") ? path : "/" + path))
                                .replaceAll("\\{[^}]+}", "1");
                        Set<RequestMethod> methods = mapping.method().length == 0 ? EnumSet.noneOf(RequestMethod.class)
                                : EnumSet.copyOf(Arrays.asList(mapping.method()));
                        routes.add(new Route(full, methods));
                    }
                }
            }
        }
        return routes;
    }

    private record Route(String path, Set<RequestMethod> methods) {
    }
}
