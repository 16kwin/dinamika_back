package com.example.dinamika_back.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Описание функции в реестре {@link Feature}: построитель, которым записана каждая строка реестра.
 * Методы построения вызываются только при инициализации enum, после неё описание не меняется.
 */
public final class FeatureDef {

    private final String key;
    private final String title;
    private final EnumSet<FeaturePreset> presets = EnumSet.noneOf(FeaturePreset.class);
    private final List<String> requires = new ArrayList<>();
    private final List<GateRule> rules = new ArrayList<>();
    private final List<GateRule> callbacks = new ArrayList<>();
    private final List<String> threads = new ArrayList<>();
    private final List<String> tables = new ArrayList<>();
    private final List<String> dirProperties = new ArrayList<>();
    private FeatureLevel level = FeatureLevel.L2;
    private String marker;
    private String probe;
    private String outbound;
    private boolean ceilingOnly;
    private boolean publicFlag;
    private boolean uiOnly;
    private boolean absent;
    private String absentReason;
    /** Начало последней группы правил: к ней относится {@link #audited} */
    private int groupStart;

    private FeatureDef(String key, String title) {
        this.key = key;
        this.title = title;
    }

    /** Обычная функция */
    static FeatureDef def(String key, String title) {
        return new FeatureDef(key, title);
    }

    /** Отсутствует в продукте: включить нельзя, отчёт печатает обоснование (раздел 4.3) */
    static FeatureDef absent(String key, String title, String reason) {
        FeatureDef def = new FeatureDef(key, title);
        def.absent = true;
        def.absentReason = reason;
        def.level = FeatureLevel.L4;
        def.ceilingOnly = true;
        return def;
    }

    /** Пресеты, в которых функция включена; без аргументов — выключена во всех */
    FeatureDef on(FeaturePreset... inPresets) {
        presets.clear();
        Collections.addAll(presets, inPresets);
        return this;
    }

    FeatureDef level(FeatureLevel value) {
        this.level = value;
        return this;
    }

    /** Класс-маркер модуля: нет класса в jar — функция выключена с причиной ABSENT_IN_BUILD (пакет B9) */
    FeatureDef marker(String className) {
        this.marker = className;
        return this;
    }

    /** Функции, без которых эта не работает (кроме родителя — он следует из ключа) */
    FeatureDef requires(String... keys) {
        Collections.addAll(requires, keys);
        return this;
    }

    /** Правила ворот: путь закрыт, когда функция выключена */
    FeatureDef gate(String... specs) {
        return add(GateRule.Kind.GATE, specs);
    }

    /** Пути загрузки файлов: закрыты для multipart/*, когда функция выключена (только uploads) */
    FeatureDef multipart(String... specs) {
        return add(GateRule.Kind.MULTIPART, specs);
    }

    /** Пути, где файл необязателен: до B4b не закрываются воротами, файл отклоняет сторож (только uploads) */
    FeatureDef mixed(String... specs) {
        return add(GateRule.Kind.MIXED, specs);
    }

    /** Помечает правила последнего вызова gate/multipart/mixed: успешные ответы пишутся в журнал доступа */
    FeatureDef audited(AccessAudit.Event event) {
        for (int i = groupStart; i < rules.size(); i++) {
            rules.set(i, rules.get(i).audited(event));
        }
        return this;
    }

    /** Входящие колбэки внешней системы (заказы): единственный список для permitAll, CSRF и фильтра ключа */
    FeatureDef callbacks(String... specs) {
        for (String spec : specs) callbacks.add(GateRule.parse(spec, GateRule.Kind.GATE));
        return this;
    }

    /** Адрес пробы для отчёта и проверки из браузера (по умолчанию — первое правило ворот) */
    FeatureDef probe(String path) {
        this.probe = path;
        return this;
    }

    /** Префиксы имён потоков модуля — для самопроверки отчёта «потоков нет» */
    FeatureDef threads(String... prefixes) {
        Collections.addAll(threads, prefixes);
        return this;
    }

    /** Таблицы данных функции — для отчёта «сохранено, доступ закрыт» и удаления данных (B11) */
    FeatureDef tables(String... names) {
        Collections.addAll(tables, names);
        return this;
    }

    /** Свойства с каталогами данных функции */
    FeatureDef dirProperty(String... properties) {
        Collections.addAll(dirProperties, properties);
        return this;
    }

    /** Описание сетевого взаимодействия функции для отчёта */
    FeatureDef outbound(String description) {
        this.outbound = description;
        return this;
    }

    /** Управляется только потолком: переключатель администратора (этап 2) к ней не применяется */
    FeatureDef ceilingOnly() {
        this.ceilingOnly = true;
        return this;
    }

    /** Флаг нужен до входа: отдаётся анонимно в {@code /api/features/public} */
    FeatureDef publicFlag() {
        this.publicFlag = true;
        return this;
    }

    /** Функция только фронтенда: на бэкенде ей нечего выключать */
    FeatureDef ui() {
        this.uiOnly = true;
        return this;
    }

    private FeatureDef add(GateRule.Kind kind, String... specs) {
        groupStart = rules.size();
        for (String spec : specs) rules.add(GateRule.parse(spec, kind));
        return this;
    }

    String key() {
        return key;
    }

    String title() {
        return title;
    }

    Set<FeaturePreset> presets() {
        return Collections.unmodifiableSet(presets);
    }

    List<String> requiresKeys() {
        return Collections.unmodifiableList(requires);
    }

    List<GateRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    List<GateRule> callbacks() {
        return Collections.unmodifiableList(callbacks);
    }

    List<String> threads() {
        return Collections.unmodifiableList(threads);
    }

    List<String> tables() {
        return Collections.unmodifiableList(tables);
    }

    List<String> dirProperties() {
        return Collections.unmodifiableList(dirProperties);
    }

    FeatureLevel level() {
        return level;
    }

    String marker() {
        return marker;
    }

    String probe() {
        return probe;
    }

    String outbound() {
        return outbound;
    }

    boolean isCeilingOnly() {
        return ceilingOnly;
    }

    boolean publicFlagValue() {
        return publicFlag;
    }

    boolean uiOnly() {
        return uiOnly;
    }

    boolean isAbsent() {
        return absent;
    }

    String absentReason() {
        return absentReason;
    }
}
