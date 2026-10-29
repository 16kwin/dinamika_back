package com.example.dinamika_back.features;

import com.example.dinamika_back.config.security.AppRoles;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.OriginTrackedResource;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.boot.origin.SystemEnvironmentOrigin;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * Разбор потолка установки (раздел 5.2 проекта) — чистая статическая функция {@code resolve(Environment)}
 * с кэшем по Environment. Её используют условие на бинах, {@link FeatureService}, SecurityConfig (B5) и CLI (B6).
 *
 * <p>Правила:
 * <ol>
 *   <li>Белый список потолка — {@code awms.features.*} и параметры {@link CeilingParam}. Выключатели
 *       {@code awms.features.*} принимаются только из файла потолка, умолчаний jar ({@code application.properties}) и
 *       тестового контекста: переменная окружения, системное свойство, аргумент командной строки,
 *       SPRING_APPLICATION_JSON или другой файл конфигурации с таким ключом → {@link FeatureConfigurationException},
 *       запуск отменяется. Исключения: {@code AWMS_FEATURES_FILE} (только путь) и {@code AWMS_FEATURES_PRESET} —
 *       только когда файла потолка нет. Параметры безопасности ({@code server.port} и др.) из этих источников, как до
 *       выключателей, применяются с предупреждением в лог и шапку отчёта: отпечаток службы тогда расходится с
 *       print-features.</li>
 *   <li>В файле потолка — только белый список: {@code spring.config.import}, {@code jwt.*}, {@code logging.*} и т. п.
 *       отменяют запуск. Подстановки {@code ${…}} запрещены в файле потолка, умолчаниях jar и выключателях; в параметре
 *       безопасности вне файла (например {@code --server.port=${PORT:8084}}) их разрешает окружение, как Spring.</li>
 *   <li>Нет пресета (нет файла потолка или ключа в нём) → {@code full}, как до выключателей, с предупреждением.
 *       Сузить набор (standard, isolated) можно только явным пресетом в файле потолка.</li>
 *   <li>Неизвестная функция или пресет → исключение; опечатка в имени переменной окружения тоже ловится.</li>
 *   <li>Выключенный родитель или requires выключает функцию с причиной {@code DEPENDENCY:<ключ>}.</li>
 *   <li>Нет класса-маркера в jar → {@code ABSENT_IN_BUILD}.</li>
 *   <li>Отпечаток — SHA-256 отсортированной канонической строки всех ключей белого списка с итоговыми значениями.</li>
 * </ol>
 */
public final class FeatureResolver {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FeatureResolver.class);

    /** Переменная (или системное свойство) с путём к файлу потолка */
    public static final String FEATURES_FILE_VARIABLE = "AWMS_FEATURES_FILE";
    /** Пресет без файла потолка (режим разработчика): для согласования непригоден */
    public static final String PRESET_VARIABLE = "AWMS_FEATURES_PRESET";
    /** Путь по умолчанию — относительно рабочего каталога службы (рядом с jar) */
    public static final String DEFAULT_FEATURES_FILE = "./config/awms-features.properties";

    static final String PREFIX = "awms.features";
    private static final ConfigurationPropertyName ROOT = ConfigurationPropertyName.of(PREFIX);
    private static final ConfigurationPropertyName PRESET = ConfigurationPropertyName.of(PREFIX + ".preset");
    private static final ConfigurationPropertyName TICKET = ConfigurationPropertyName.of(PREFIX + ".ticket");
    private static final ConfigurationPropertyName FILE = ConfigurationPropertyName.of(PREFIX + ".file");
    /** Источники тестового контекста: @SpringBootTest(properties), @TestPropertySource, ApplicationContextRunner */
    private static final Set<String> TEST_SOURCES = Set.of("Inlined Test Properties", "Dynamic Test Properties",
            "test");
    private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final int TICKET_MAX = 500;

    /**
     * Кэш разбора: ключ (Environment) слабый, значение сильное — снимок живёт, пока живо окружение, и не пропадает
     * при сборке мусора: условия, {@link FeatureService} и SecurityConfig получают один и тот же экземпляр.
     * Снимок на Environment не ссылается, поэтому ключ не удерживается.
     */
    private static final Map<Environment, FeatureSnapshot> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private FeatureResolver() {
    }

    /** Потолок для окружения Spring (с кэшем: условия на бинах вызывают разбор много раз) */
    public static FeatureSnapshot resolve(Environment environment) {
        // Ошибка разбора не кэшируется: следующий вызов разберёт заново и бросит то же исключение
        return CACHE.computeIfAbsent(environment,
                env -> resolve(env, ceilingPath(env), FeatureResolver.class.getClassLoader()));
    }

    /** Разбор без кэша: явный путь к файлу потолка и загрузчик классов для проверки маркеров */
    public static FeatureSnapshot resolve(Environment environment, Path ceilingFile, ClassLoader classLoader) {
        Path ceiling = ceilingFile.toAbsolutePath().normalize();
        return resolve(environment, ceiling, Files.isRegularFile(ceiling), classLoader);
    }

    /** Абсолютный путь файла потолка: {@code AWMS_FEATURES_FILE} или {@code ./config/awms-features.properties} */
    public static Path ceilingPath(Environment environment) {
        String configured = environment.getProperty(FEATURES_FILE_VARIABLE);
        String location = StringUtils.hasText(configured) ? configured.trim() : DEFAULT_FEATURES_FILE;
        try {
            return Paths.get(location).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new FeatureConfigurationException(FEATURES_FILE_VARIABLE + "=«" + location
                    + "»: некорректный путь к файлу потолка", e);
        }
    }

    /**
     * Потолок только из файла и умолчаний jar, без окружения процесса — для CLI {@code print-features --config}
     * (B6). Ключи белого списка принимаются только из этих двух мест, поэтому отпечаток совпадает со службой.
     */
    public static FeatureSnapshot resolveFile(Path ceilingFile) {
        Path ceiling = ceilingFile.toAbsolutePath().normalize();
        StandardEnvironment env = isolatedEnvironment();
        PropertiesPropertySourceLoader loader = new PropertiesPropertySourceLoader();
        ClassLoader classLoader = FeatureResolver.class.getClassLoader();
        try {
            boolean exists = Files.isRegularFile(ceiling);
            if (exists) {
                loader.load("ceiling", new FileSystemResource(ceiling)).forEach(env.getPropertySources()::addLast);
            }
            loader.load("jar", new ClassPathResource("application.properties", classLoader))
                    .forEach(env.getPropertySources()::addLast);
            return resolve(env, ceiling, exists, classLoader);
        } catch (IOException e) {
            throw new FeatureConfigurationException("Не удалось прочитать файл потолка " + ceiling + ": "
                    + e.getMessage(), e);
        }
    }

    /**
     * Потолок из набора свойств, как в тестовом контексте (источник «test»): для тестов модулей,
     * которые создают сервисы напрямую. Файл потолка не читается.
     */
    public static FeatureSnapshot fromProperties(Map<String, ?> properties) {
        StandardEnvironment env = isolatedEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", new LinkedHashMap<>(properties)));
        return resolve(env, ceilingPath(env), false, FeatureResolver.class.getClassLoader());
    }

    private static StandardEnvironment isolatedEnvironment() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        return env;
    }

    private static FeatureSnapshot resolve(Environment environment, Path ceiling, boolean exists,
                                           ClassLoader classLoader) {
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            throw new IllegalArgumentException("Нужен ConfigurableEnvironment");
        }
        Sources sources = new Sources(configurable, ceiling, exists);
        sources.verify();
        List<String> warnings = new ArrayList<>();

        // Пресет
        Found presetValue = sources.find(PRESET);
        FeaturePreset preset;
        String presetSource;
        if (presetValue == null) {
            // Без файла потолка сервер работает как до выключателей — с полным набором функций. Сузить набор
            // (standard, isolated — заказчику в закрытом контуре) можно только явно, файлом потолка
            preset = FeaturePreset.FULL;
            presetSource = "default";
            warnings.add(exists
                    ? "В файле потолка " + ceiling + " нет ключа awms.features.preset: применён пресет full "
                    + "(«Расширенный», как до выключателей)"
                    : "Файл потолка не найден: " + ceiling + ". Применён пресет full («Расширенный»): все функции, "
                    + "как до выключателей. Чтобы сузить набор, положите файл потолка или задайте путь в "
                    + FEATURES_FILE_VARIABLE);
        } else {
            preset = FeaturePreset.fromKey(presetValue.value());
            if (preset == null) {
                throw new FeatureConfigurationException("Неизвестный пресет awms.features.preset=«"
                        + presetValue.value() + "» (" + presetValue.originText() + "). Допустимо: isolated, standard, full");
            }
            presetSource = switch (presetValue.kind()) {
                case CEILING -> "file";
                case ENV -> "env";
                case TEST -> "test";
                default -> "jar";
            };
            if (presetValue.kind() == Kind.ENV) {
                warnings.add("Профиль функций задан переменной окружения " + PRESET_VARIABLE + " без файла потолка "
                        + "(режим разработчика) — для согласования непригоден");
            }
        }

        // Состояние каждой функции до каскада
        Map<Feature, FeatureState> own = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            Found enabled = sources.find(ConfigurationPropertyName.of(PREFIX + "." + f.key() + ".enabled"));
            Found roles = sources.find(ConfigurationPropertyName.of(PREFIX + "." + f.key() + ".roles"));
            Set<String> roleSet = roles == null ? Set.of() : parseRoles(f, roles);
            Boolean explicit = enabled == null ? null : parseBoolean(f, enabled);
            FeatureState state;
            if (f.isAbsent()) {
                if (Boolean.TRUE.equals(explicit)) {
                    throw new FeatureConfigurationException("Функция " + f.key() + " («" + f.title()
                            + "») отсутствует в продукте, включить её нельзя (" + enabled.originText() + ")");
                }
                state = new FeatureState(f, false, FeatureState.Reason.ABSENT, null, roleSet, "отсутствует в продукте");
            } else if (f.marker() != null && !ClassUtils.isPresent(f.marker(), classLoader)) {
                state = new FeatureState(f, false, FeatureState.Reason.ABSENT_IN_BUILD, null, roleSet,
                        "нет в сборке: " + f.marker());
            } else if (explicit != null) {
                state = new FeatureState(f, explicit, FeatureState.Reason.PROPERTY, null, roleSet, enabled.originText());
            } else {
                state = new FeatureState(f, f.isOnIn(preset), FeatureState.Reason.PRESET, null, roleSet,
                        "пресет " + preset.key());
            }
            own.put(f, state);
        }
        Map<Feature, FeatureState> states = FeatureSnapshot.cascade(own, warnings);

        // Параметры безопасности
        Map<CeilingParam, String> params = new EnumMap<>(CeilingParam.class);
        Map<CeilingParam, String> origins = new EnumMap<>(CeilingParam.class);
        for (CeilingParam p : CeilingParam.values()) {
            Found found = sources.find(p.propertyName());
            String raw;
            String origin;
            if (found != null) {
                raw = found.value();
                origin = found.originText();
            } else if (p == CeilingParam.WEBSOCKET_ALLOWED_ORIGINS) {
                // Как до выключателей: «*» — без своего сужения. Чужой Origin рукопожатия всё равно отсекает CORS
                // Spring Security (403): проходят адреса CORS и тот же источник. Сузить — явным списком в файле потолка
                raw = "*";
                origin = "умолчание (как раньше: адреса CORS и тот же источник)";
            } else {
                raw = p.defaultValue();
                origin = "умолчание";
            }
            params.put(p, p.normalize(raw, origin));
            origins.put(p, origin);
        }
        warnings.addAll(sources.outside.values());
        if ("full".equals(params.get(CeilingParam.ADMIN_MODE)) && params.get(CeilingParam.ADMIN_ROLES).isEmpty()) {
            throw new FeatureConfigurationException("awms.features.admin.mode=full требует awms.features.admin.roles: "
                    + "кто, кроме SECURITY, включает функции обратно");
        }

        // Основание изменения
        Found ticketValue = sources.find(TICKET);
        String ticket = ticketValue == null ? null : fixUtf8(ticketValue.value());
        if (ticket != null && ticket.isEmpty()) ticket = null;
        if (ticket != null && ticket.length() > TICKET_MAX) {
            throw new FeatureConfigurationException("awms.features.ticket длиннее " + TICKET_MAX + " знаков ("
                    + ticketValue.originText() + ")");
        }

        String fingerprint = sha256(canonical(states, params).getBytes(StandardCharsets.UTF_8));
        String fileSha = exists ? fileSha256(ceiling) : null;
        return FeatureSnapshot.ceiling(states, preset, presetSource, ticket, params, origins, fingerprint, ceiling,
                exists, fileSha, warnings);
    }

    /**
     * Каноническая строка белого списка: по строке на ключ, строки по алфавиту. Функции —
     * {@code awms.features.<key>=on|off;roles=…}, параметры — {@code имя=значение}. Пресет и основание не входят:
     * отпечаток описывает итоговые значения, а не то, как они записаны.
     */
    static String canonical(Map<Feature, FeatureState> states, Map<CeilingParam, String> params) {
        Set<String> lines = new TreeSet<>();
        for (FeatureState s : states.values()) {
            lines.add(PREFIX + "." + s.key() + "=" + (s.enabled() ? "on" : "off") + ";roles=" + String.join(",", s.roles()));
        }
        params.forEach((p, v) -> lines.add(p.key() + "=" + v));
        return String.join("\n", lines);
    }

    private static Boolean parseBoolean(Feature f, Found found) {
        String v = found.value().toLowerCase(Locale.ROOT);
        if (v.equals("true")) return true;
        if (v.equals("false")) return false;
        throw new FeatureConfigurationException("awms.features." + f.key() + ".enabled=«" + found.value() + "» ("
                + found.originText() + "): ожидается true или false");
    }

    private static Set<String> parseRoles(Feature f, Found found) {
        Set<String> roles = AppRoles.parse(found.value());
        for (String role : roles) {
            if (!ROLE.matcher(role).matches()) {
                throw new FeatureConfigurationException("awms.features." + f.key() + ".roles (" + found.originText()
                        + "): некорректное имя роли «" + role + "»");
            }
        }
        return roles;
    }

    /**
     * Spring Boot читает .properties в ISO-8859-1, а администратор сохраняет файл потолка в UTF-8: русское
     * основание пришло бы «кракозябрами». Если строка — корректный UTF-8, прочитанный как ISO-8859-1, перекодируем.
     */
    static String fixUtf8(String value) {
        boolean high = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c > 0xFF) return value;
            if (c > 0x7F) high = true;
        }
        if (!high) return value;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value.getBytes(StandardCharsets.ISO_8859_1)))
                    .toString();
        } catch (CharacterCodingException e) {
            return value;
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fileSha256(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            return null;
        }
    }

    // ===================== Источники значений =====================

    /** Откуда пришло значение ключа белого списка */
    enum Kind {
        /** Файл потолка */
        CEILING,
        /** Умолчания jar: classpath application.properties */
        JAR,
        /** Тестовый контекст */
        TEST,
        /** Переменная окружения */
        ENV,
        /** Всё остальное: системные свойства, аргументы, SPRING_APPLICATION_JSON, другие файлы */
        OTHER
    }

    /**
     * Найденное значение: как записано (подстановки ${…} допустимы только у параметров безопасности вне файла потолка —
     * у них значение уже разрешено окружением), вид источника и текст источника для лога и отчёта
     */
    record Found(String value, Kind kind, String originText) {
    }

    private static final class Sources {

        private final List<PropertySource<?>> sources = new ArrayList<>();
        private final Path ceiling;
        private final boolean ceilingExists;
        /** Параметры безопасности, заданные вне файла потолка: ключ → предупреждение */
        private final Map<String, String> outside = new LinkedHashMap<>();
        /** Окружение: подстановки ${…} в параметрах вне файла потолка разрешает оно, как Spring при запуске */
        private final ConfigurableEnvironment environment;

        Sources(ConfigurableEnvironment environment, Path ceiling, boolean ceilingExists) {
            this.environment = environment;
            MutablePropertySources propertySources = environment.getPropertySources();
            for (PropertySource<?> ps : propertySources) {
                // Обёртка configurationProperties повторяет все источники — её пропускаем
                if (ConfigurationPropertySources.isAttachedConfigurationPropertySource(ps)) continue;
                if (ps instanceof PropertySource.StubPropertySource) continue;
                sources.add(ps);
            }
            this.ceiling = ceiling;
            this.ceilingExists = ceilingExists;
        }

        /** Проверка источников всех ключей белого списка и состава файла потолка */
        void verify() {
            for (PropertySource<?> ps : sources) {
                ConfigurationPropertySource cps = ConfigurationPropertySource.from(ps);
                if (cps == null) continue;
                // 1. Параметры 4.5 — поиском по имени с правилами Spring (в том числе формы переменных окружения)
                for (CeilingParam p : CeilingParam.values()) {
                    ConfigurationProperty prop = cps.getConfigurationProperty(p.propertyName());
                    if (prop != null) checkSource(ps, prop.getOrigin(), p.key());
                }
                // 2. Пространство awms.features.* — перебором (имя переменной окружения однозначно обратимо)
                if (cps instanceof IterableConfigurationPropertySource iterable) {
                    for (ConfigurationPropertyName name : iterable) {
                        if (ROOT.isAncestorOf(name) && CeilingParam.byName(name) == null) {
                            ConfigurationProperty prop = iterable.getConfigurationProperty(name);
                            checkFeatureName(ps, name, prop == null ? null : prop.getOrigin());
                        }
                    }
                }
                // 3. В файле потолка — только белый список
                if (ps instanceof EnumerablePropertySource<?> enumerable && ps instanceof OriginLookup) {
                    for (String raw : enumerable.getPropertyNames()) {
                        Origin origin = OriginLookup.getOrigin(ps, raw);
                        if (kind(ps, origin) != Kind.CEILING) continue;
                        ConfigurationPropertyName name = ConfigurationPropertyName.adapt(raw, '.');
                        boolean allowed = !name.isEmpty() && (ROOT.isAncestorOf(name) || CeilingParam.byName(name) != null);
                        if (!allowed) {
                            throw new FeatureConfigurationException("Ключ «" + raw + "» в файле потолка ("
                                    + describe(ps, origin, raw) + ") вне белого списка. В файле потолка допустимы только "
                                    + "awms.features.* и параметры безопасности; секреты — в файле секретов, остальные "
                                    + "настройки — в application.properties");
                        }
                    }
                }
            }
        }

        private void checkSource(PropertySource<?> ps, Origin origin, String key) {
            Kind kind = kind(ps, origin);
            if (kind == Kind.CEILING || kind == Kind.JAR || kind == Kind.TEST) return;
            // Не отказ, а предупреждение: служба сервера может задавать порт, адрес и т. п. аргументами или окружением —
            // так было до выключателей, и запуск из-за этого не должен ломаться. Значение применяется как раньше и входит
            // в отпечаток службы, а CLI print-features окружения службы не видит: отпечатки разойдутся. Предупреждение
            // попадает в лог один раз на ключ и в предупреждения профиля (шапка отчёта «Профиль безопасности»)
            String message = "Параметр «" + key + "» задан вне файла потолка: " + describe(ps, origin, key)
                    + ". Значение применяется и входит в отпечаток службы, но print-features по файлу потолка его не "
                    + "видит — отпечатки разойдутся. Для согласования с ИБ перенесите значение в файл потолка " + ceiling;
            if (outside.putIfAbsent(key, message) == null) LOG.warn(message);
        }

        /** Выключатель awms.features.* — только из файла потолка, умолчаний jar или теста, иначе отказ запуска */
        private void checkFeatureSource(PropertySource<?> ps, Origin origin, String key) {
            Kind kind = kind(ps, origin);
            if (kind == Kind.CEILING || kind == Kind.JAR || kind == Kind.TEST) return;
            throw new FeatureConfigurationException("Ключ «" + key + "» задан вне файла потолка: "
                    + describe(ps, origin, key) + ". Профиль функций принимается только из файла потолка " + ceiling
                    + " (и умолчаний jar); переопределять его окружением, аргументами или системными свойствами "
                    + "нельзя. Перенесите значение в файл потолка и перезапустите службу");
        }

        private void checkFeatureName(PropertySource<?> ps, ConfigurationPropertyName name, Origin origin) {
            Kind kind = kind(ps, origin);
            if (kind == Kind.ENV) {
                // AWMS_FEATURES_FILE — только путь к файлу потолка
                if (name.equals(FILE)) return;
                if (name.equals(PRESET)) {
                    if (!ceilingExists) return; // режим разработчика: пресет без файла
                    throw new FeatureConfigurationException("Переменная окружения " + envName(origin, name)
                            + " задана, а файл потолка есть: " + ceiling + ". Пресет задаётся в файле потолка; "
                            + "переменная " + PRESET_VARIABLE + " допустима только без файла (режим разработчика)");
                }
            }
            String problem = keyProblem(name);
            if (kind == Kind.CEILING || kind == Kind.JAR || kind == Kind.TEST) {
                if (problem != null) {
                    throw new FeatureConfigurationException("Ключ «" + name + "» (" + describe(ps, origin, name.toString())
                            + "): " + problem);
                }
                return;
            }
            String what = kind == Kind.ENV ? "Переменная окружения " + envName(origin, name) : "Ключ «" + name + "» ("
                    + describe(ps, origin, name.toString()) + ")";
            throw new FeatureConfigurationException(what + ": " + (problem != null ? problem + ". " : "")
                    + "Профиль функций не переопределяется окружением, аргументами или системными свойствами — "
                    + "только файлом потолка " + ceiling + ". Уберите " + (kind == Kind.ENV ? "переменную" : "ключ")
                    + " и перезапустите службу");
        }

        /** Почему имя под awms.features.* не подходит; null — имя известно */
        private static String keyProblem(ConfigurationPropertyName name) {
            int n = name.getNumberOfElements();
            List<String> rest = new ArrayList<>();
            for (int i = ROOT.getNumberOfElements(); i < n; i++) {
                rest.add(name.getElement(i, ConfigurationPropertyName.Form.DASHED));
            }
            if (rest.size() == 1 && (rest.get(0).equals("preset") || rest.get(0).equals("ticket"))) return null;
            if (rest.size() < 2) return "ожидается awms.features.<функция>.enabled или .roles";
            String attribute = rest.get(rest.size() - 1);
            String key = String.join(".", rest.subList(0, rest.size() - 1));
            if (!attribute.equals("enabled") && !attribute.equals("roles")) {
                return "ожидается awms.features." + key + ".enabled или .roles";
            }
            if (Feature.byKey(key).isEmpty()) return "неизвестная функция «" + key + "»";
            return null;
        }

        /** Значение с наивысшим приоритетом или null */
        Found find(ConfigurationPropertyName name) {
            for (PropertySource<?> ps : sources) {
                ConfigurationPropertySource cps = ConfigurationPropertySource.from(ps);
                if (cps == null) continue;
                ConfigurationProperty prop = cps.getConfigurationProperty(name);
                if (prop == null || prop.getValue() == null) continue;
                String value = String.valueOf(prop.getValue()).trim();
                Origin origin = prop.getOrigin();
                Kind kind = kind(ps, origin);
                boolean feature = ROOT.isAncestorOf(name) && CeilingParam.byName(name) == null;
                // Действующее значение — только из допустимого источника. verify() перебирает лишь перечислимые
                // источники; ключ из неперечислимого (JNDI и т. п.) ловится здесь. Выключатели awms.features.*
                // вне потолка — по-прежнему отказ; параметры безопасности — предупреждение (checkSource)
                if (!(kind == Kind.ENV && name.equals(PRESET) && !ceilingExists)) {
                    if (feature) {
                        checkFeatureSource(ps, origin, name.toString());
                    } else {
                        checkSource(ps, origin, name.toString());
                    }
                }
                if (value.contains("${")) {
                    // В файле потолка (и умолчаниях jar) значение записывается явно: его читает ИБ и print-features,
                    // подстановка из окружения подменила бы его мимо файла. Выключатели — так же из любого источника
                    if (feature || kind == Kind.CEILING || kind == Kind.JAR || kind == Kind.TEST) {
                        throw new FeatureConfigurationException("Ключ «" + name + "» (" + describe(ps, origin,
                                name.toString()) + "): подстановки ${…} в профиле функций запрещены — значение должно "
                                + "быть записано в файле потолка явно");
                    }
                    // Параметр вне файла потолка (окружение, аргумент, внешний конфиг), например
                    // --server.port=${PORT:8084}: как до выключателей, подстановку разрешает окружение, запуск не
                    // отменяется; предупреждение «задан вне файла потолка» уже есть (checkSource)
                    value = environment.resolvePlaceholders(value).trim();
                }
                return new Found(value, kind, originText(kind, ps, origin, name.toString()));
            }
            return null;
        }

        private Kind kind(PropertySource<?> ps, Origin origin) {
            Resource resource = resourceOf(origin);
            if (resource != null) {
                if (resource instanceof ClassPathResource classPath && "application.properties".equals(classPath.getPath())) {
                    return Kind.JAR;
                }
                if (isCeiling(resource)) return Kind.CEILING;
            }
            if (TEST_SOURCES.contains(ps.getName())) return Kind.TEST;
            if (ps instanceof SystemEnvironmentPropertySource) return Kind.ENV;
            return Kind.OTHER;
        }

        private boolean isCeiling(Resource resource) {
            try {
                if (!resource.isFile()) return false;
                Path file = resource.getFile().toPath().toAbsolutePath().normalize();
                if (file.equals(ceiling)) return true;
                return ceilingExists && Files.exists(file) && Files.isSameFile(file, ceiling);
            } catch (IOException | InvalidPathException e) {
                return false;
            }
        }

        private static Resource resourceOf(Origin origin) {
            for (Origin o = origin; o != null; o = o.getParent()) {
                if (o instanceof TextResourceOrigin text && text.getResource() != null) {
                    Resource r = text.getResource();
                    while (r instanceof OriginTrackedResource tracked) r = tracked.getResource();
                    return r;
                }
            }
            return null;
        }

        /** Источник для сообщения об ошибке */
        private static String describe(PropertySource<?> ps, Origin origin, String key) {
            if (ps instanceof SystemEnvironmentPropertySource) return "переменная окружения " + envName(origin, null);
            String name = ps.getName();
            if (StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME.equals(name)) {
                return "системное свойство -D" + propertyName(origin, key);
            }
            if ("commandLineArgs".equals(name)) return "аргумент командной строки --" + propertyName(origin, key);
            if ("spring.application.json".equals(name)) return "SPRING_APPLICATION_JSON";
            Resource resource = resourceOf(origin);
            if (resource != null) return resource.getDescription() + lineOf(origin);
            return "источник «" + name + "»";
        }

        /** Источник значения для лога и отчёта: «файл awms-features.properties:5», «jar: умолчание» */
        private static String originText(Kind kind, PropertySource<?> ps, Origin origin, String key) {
            return switch (kind) {
                case CEILING -> {
                    Resource resource = resourceOf(origin);
                    yield "файл " + (resource == null ? "потолка" : resource.getFilename()) + lineOf(origin);
                }
                case JAR -> "jar: умолчание";
                case TEST -> "тест";
                case ENV -> "переменная окружения " + envName(origin, null);
                default -> describe(ps, origin, key);
            };
        }

        private static String lineOf(Origin origin) {
            for (Origin o = origin; o != null; o = o.getParent()) {
                if (o instanceof TextResourceOrigin text && text.getLocation() != null) {
                    return ":" + (text.getLocation().getLine() + 1);
                }
            }
            return "";
        }

        private static String envName(Origin origin, ConfigurationPropertyName name) {
            if (origin instanceof SystemEnvironmentOrigin env) return env.getProperty();
            if (origin instanceof PropertySourceOrigin source) return source.getPropertyName();
            return name == null ? "" : name.toString().toUpperCase(Locale.ROOT).replace('.', '_').replace("-", "");
        }

        private static String propertyName(Origin origin, String key) {
            if (origin instanceof PropertySourceOrigin source) return source.getPropertyName();
            return key;
        }
    }
}
