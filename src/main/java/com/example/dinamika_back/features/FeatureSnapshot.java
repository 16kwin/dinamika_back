package com.example.dinamika_back.features;

import com.example.dinamika_back.config.security.AppRoles;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Неизменяемый снимок профиля функций. Снимок потолка строит {@link FeatureResolver}; на этапе 2 переключатели
 * администратора сужают его методом {@link #withSwitches}. Ворота, сторож и {@code /api/features} читают текущий
 * снимок из {@link FeatureService}.
 *
 * <p>{@code revision} = первые 12 знаков отпечатка + версия переключателей: перезапуск с другим потолком меняет
 * ревизию всегда, даже при той же версии переключателей.
 */
public final class FeatureSnapshot {

    /** Правило выключенной функции, которое ворота закрывают */
    public record ClosedRule(Feature feature, GateRule rule) {
    }

    private final Map<Feature, FeatureState> states;
    private final FeaturePreset preset;
    private final String presetSource;
    private final String ticket;
    private final Map<CeilingParam, String> params;
    private final Map<CeilingParam, String> paramOrigins;
    private final String fingerprint;
    private final long switchVersion;
    private final String revision;
    private final Path ceilingFile;
    private final boolean ceilingFileExists;
    private final String ceilingFileSha256;
    private final List<String> warnings;
    private final List<ClosedRule> closedRules;
    /** Снимок потолка, от которого считаются переключатели (у самого потолка — он сам) */
    private final FeatureSnapshot ceiling;

    private FeatureSnapshot(Map<Feature, FeatureState> states, FeaturePreset preset, String presetSource,
                            String ticket, Map<CeilingParam, String> params, Map<CeilingParam, String> paramOrigins,
                            String fingerprint, long switchVersion, Path ceilingFile, boolean ceilingFileExists,
                            String ceilingFileSha256, List<String> warnings, FeatureSnapshot ceiling) {
        this.states = Collections.unmodifiableMap(new EnumMap<>(states));
        this.preset = preset;
        this.presetSource = presetSource;
        this.ticket = ticket;
        this.params = Collections.unmodifiableMap(new EnumMap<>(params));
        this.paramOrigins = Collections.unmodifiableMap(new EnumMap<>(paramOrigins));
        this.fingerprint = fingerprint;
        this.switchVersion = switchVersion;
        this.revision = fingerprint.substring(0, 12) + "-" + switchVersion;
        this.ceilingFile = ceilingFile;
        this.ceilingFileExists = ceilingFileExists;
        this.ceilingFileSha256 = ceilingFileSha256;
        this.warnings = List.copyOf(warnings);
        this.closedRules = collectClosedRules(this.states);
        this.ceiling = ceiling == null ? this : ceiling;
    }

    /** Снимок потолка (строит {@link FeatureResolver}) */
    static FeatureSnapshot ceiling(Map<Feature, FeatureState> states, FeaturePreset preset, String presetSource,
                                   String ticket, Map<CeilingParam, String> params,
                                   Map<CeilingParam, String> paramOrigins, String fingerprint, Path ceilingFile,
                                   boolean ceilingFileExists, String ceilingFileSha256, List<String> warnings) {
        return new FeatureSnapshot(states, preset, presetSource, ticket, params, paramOrigins, fingerprint, 0,
                ceilingFile, ceilingFileExists, ceilingFileSha256, warnings, null);
    }

    /**
     * Этап 2 (B7): снимок «потолок ∧ переключатели». Считается всегда от снимка потолка. Переключатель может только
     * выключить функцию, которую разрешил потолок, и не действует на функции {@code ceilingOnly}.
     *
     * @param switchedOff   функции, выключенные администратором
     * @param tampered      функции, чей переключатель расходится с журналом (TAMPER)
     * @param switchVersion версия переключателей — вторая часть ревизии
     */
    public FeatureSnapshot withSwitches(Set<Feature> switchedOff, Set<Feature> tampered, long switchVersion) {
        Map<Feature, FeatureState> own = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            FeatureState c = ceiling.states.get(f);
            FeatureState s = c;
            if (c.enabled() && !f.isCeilingOnly()) {
                if (tampered.contains(f)) s = c.with(false, FeatureState.Reason.TAMPER, null);
                else if (switchedOff.contains(f)) s = c.with(false, FeatureState.Reason.SWITCH, null);
            }
            own.put(f, s);
        }
        return new FeatureSnapshot(cascade(own, null), ceiling.preset, ceiling.presetSource, ceiling.ticket,
                ceiling.params, ceiling.paramOrigins, ceiling.fingerprint, switchVersion, ceiling.ceilingFile,
                ceiling.ceilingFileExists, ceiling.ceilingFileSha256, List.of(), ceiling);
    }

    /**
     * Каскад зависимостей: функция включена, только если включены её родитель и все requires. Выключенная
     * зависимостью получает причину {@code DEPENDENCY:<ключ>}; если сама она была включена — предупреждение.
     */
    static Map<Feature, FeatureState> cascade(Map<Feature, FeatureState> own, List<String> warnings) {
        Map<Feature, FeatureState> result = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            resolve(f, own, result, warnings, new ArrayDeque<>());
        }
        return result;
    }

    private static FeatureState resolve(Feature f, Map<Feature, FeatureState> own, Map<Feature, FeatureState> result,
                                        List<String> warnings, Deque<Feature> path) {
        FeatureState done = result.get(f);
        if (done != null) return done;
        if (path.contains(f)) {
            throw new FeatureConfigurationException("Цикл зависимостей функций: " + path.stream()
                    .map(Feature::key).collect(Collectors.joining(" → ")) + " → " + f.key());
        }
        path.push(f);
        FeatureState s = own.get(f);
        if (s.enabled()) {
            for (Feature dependency : f.dependencies()) {
                if (!resolve(dependency, own, result, warnings, path).enabled()) {
                    s = s.with(false, FeatureState.Reason.DEPENDENCY, dependency.key());
                    if (warnings != null) {
                        warnings.add("Функция " + f.key() + " выключена: выключена функция " + dependency.key()
                                + ", от которой она зависит");
                    }
                    break;
                }
            }
        }
        path.pop();
        result.put(f, s);
        return s;
    }

    private static List<ClosedRule> collectClosedRules(Map<Feature, FeatureState> states) {
        List<ClosedRule> result = new ArrayList<>();
        for (Feature f : Feature.values()) {
            if (states.get(f).enabled()) continue;
            for (GateRule rule : f.rules()) {
                // mixed-пути ворота не закрывают до пакета B4b: файл на них отклоняет сторож обработчика
                if (rule.kind() != GateRule.Kind.MIXED) result.add(new ClosedRule(f, rule));
            }
        }
        return List.copyOf(result);
    }

    /** Правило выключенной функции, с которым совпал запрос, или null — запрос проходит */
    public ClosedRule closedRuleFor(String method, String path, String contentType) {
        for (ClosedRule closed : closedRules) {
            if (closed.rule().matches(method, path, contentType)) return closed;
        }
        return null;
    }

    public FeatureState state(Feature feature) {
        return states.get(feature);
    }

    public Map<Feature, FeatureState> states() {
        return states;
    }

    /** Итоговое состояние функции (без учёта ролей пользователя) */
    public boolean isOn(Feature feature) {
        return states.get(feature).enabled();
    }

    /**
     * Доступна ли функция пользователю: включена, роль подходит к её {@code roles}, к ролям родителей (их пути
     * включают пути детей) и к функциям из requires. Роли из токена до пакета B10, после — из БД.
     */
    public boolean isOnFor(Feature feature, Authentication authentication) {
        if (!isOn(feature) || !rolesAllow(feature, authentication)) return false;
        Feature parent = feature.parent();
        if (parent != null && !isOnFor(parent, authentication)) return false;
        for (Feature r : feature.requires()) {
            if (!isOnFor(r, authentication)) return false;
        }
        return true;
    }

    private boolean rolesAllow(Feature feature, Authentication authentication) {
        Set<String> roles = states.get(feature).roles();
        return roles.isEmpty() || AppRoles.hasAny(authentication, roles);
    }

    /** Роли функции из потолка (пусто — все вошедшие) */
    public Set<String> roles(Feature feature) {
        return states.get(feature).roles();
    }

    /** Функции с ролями в потолке — для правил access() в SecurityConfig (B5) */
    public List<Feature> featuresWithRoles() {
        return states.values().stream().filter(s -> !s.roles().isEmpty()).map(FeatureState::feature).toList();
    }

    public List<ClosedRule> closedRules() {
        return closedRules;
    }

    public FeaturePreset preset() {
        return preset;
    }

    /** Откуда взят пресет: file, env (AWMS_FEATURES_PRESET без файла), default (нет ключа), test, jar */
    public String presetSource() {
        return presetSource;
    }

    /** Основание изменения потолка ({@code awms.features.ticket}) или null */
    public String ticket() {
        return ticket;
    }

    /** Каноническое значение параметра безопасности (раздел 4.5) */
    public String param(CeilingParam param) {
        return params.get(param);
    }

    public int intParam(CeilingParam param) {
        return Integer.parseInt(params.get(param));
    }

    public boolean boolParam(CeilingParam param) {
        return Boolean.parseBoolean(params.get(param));
    }

    public List<String> listParam(CeilingParam param) {
        return CeilingParam.splitList(params.get(param));
    }

    /** Все параметры по порядку перечисления с каноническими значениями */
    public Map<String, String> params() {
        Map<String, String> result = new LinkedHashMap<>();
        params.forEach((p, v) -> result.put(p.key(), v));
        return Collections.unmodifiableMap(result);
    }

    /** Источник значения параметра: «файл …:строка», «jar: умолчание», «умолчание» */
    public String paramOrigin(CeilingParam param) {
        return paramOrigins.get(param);
    }

    /** SHA-256 канонической строки белого списка (64 знака): одинаков у службы и CLI */
    public String fingerprint() {
        return fingerprint;
    }

    public long switchVersion() {
        return switchVersion;
    }

    /** Ревизия для заголовка {@code X-AWMS-Features} */
    public String revision() {
        return revision;
    }

    /** Абсолютный путь, по которому искался файл потолка */
    public Path ceilingFile() {
        return ceilingFile;
    }

    public boolean ceilingFileExists() {
        return ceilingFileExists;
    }

    /** SHA-256 содержимого файла потолка или null, если файла нет */
    public String ceilingFileSha256() {
        return ceilingFileSha256;
    }

    /** Предупреждения разбора: нет файла, пресет из окружения, функции, выключенные зависимостью */
    public List<String> warnings() {
        return warnings;
    }

    /** Снимок потолка, от которого построен этот снимок */
    public FeatureSnapshot ceiling() {
        return ceiling;
    }
}
