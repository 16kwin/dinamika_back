package com.example.dinamika_back.features.journal;

import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureGateFilter;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.FeatureState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Аудит профиля при каждом старте (раздел 10.1 проекта) — задача ядра, работает при любом потолке:
 * <ol>
 *   <li>проверяет HMAC-цепочку журнала профиля и сверяет голову цепочки в БД с последней строкой
 *       {@code JOURNAL_HEAD} файлового лога {@code logs/awms-security.log}. Нарушение → запись {@code TAMPER}
 *       (повторно о том же нарушении не пишет), WARN в лог и пометка в отчёте и профиле функций;</li>
 *   <li>пишет {@code STARTUP} с полным профилем (все ключи белого списка с итоговыми значениями);</li>
 *   <li>если отпечаток, пресет или SHA-256 файла потолка отличаются от последнего {@code STARTUP}, пишет
 *       {@code CEILING_CHANGED} с разницей «было → стало» по ключам белого списка, основанием и SHA-256 файла.
 *       Первый старт с журналом записывает начальный профиль так же.</li>
 * </ol>
 * Здесь же таймер сводки отказов ворот (поток ядра {@code feature-denied-summary}): раз в час отказы
 * {@link FeatureGateFilter#drainDenied()} уходят в журнал записью {@code DENIED_SUMMARY}.
 *
 * <p>Ошибка записи в журнал отменяет запуск: служба без журнала профиля для согласования непригодна.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupProfileAuditor implements ApplicationRunner, DisposableBean {

    /** Файл журнала безопасности относительно каталога логов (logback-spring.xml) */
    public static final String SECURITY_LOG_NAME = "awms-security.log";
    /** Сколько прежних файлов журнала безопасности хранит ротация (logback-spring.xml) */
    static final int SECURITY_LOG_HISTORY = 20;
    private static final Pattern HEAD = Pattern.compile(FeatureJournal.HEAD_MARKER + " id=(\\d+) hash=([0-9a-f]{64})");
    private static final Duration SUMMARY_PERIOD = Duration.ofHours(1);
    /** Пар «функция адрес» в одной записи DENIED_SUMMARY */
    private static final int SUMMARY_PAIRS = 100;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(StartupProfileAuditor.class);

    private final FeatureJournal journal;
    private final FeatureService features;
    private final Path logDir;
    private final ObjectProvider<FilterRegistrationBean<FeatureGateFilter>> gate;
    private final Environment environment;
    private volatile Status status = Status.NOT_CHECKED;
    private ScheduledExecutorService summary;

    @Autowired
    public StartupProfileAuditor(FeatureJournal journal, FeatureService features, Environment environment,
                                 ObjectProvider<FilterRegistrationBean<FeatureGateFilter>> gate) {
        this(journal, features, securityLogDir(environment), gate, environment);
    }

    /** Для тестов: свой каталог логов */
    public StartupProfileAuditor(FeatureJournal journal, FeatureService features, Path logDir,
                                 ObjectProvider<FilterRegistrationBean<FeatureGateFilter>> gate, Environment environment) {
        this.journal = journal;
        this.features = features;
        this.logDir = logDir;
        this.gate = gate;
        this.environment = environment;
    }

    /** Каталог логов: {@code logging.file.path} или {@code logs} (рабочий каталог службы) */
    public static Path securityLogDir(Environment environment) {
        String dir = environment == null ? null : environment.getProperty("logging.file.path");
        return Paths.get(dir == null || dir.isBlank() ? "logs" : dir.trim()).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) {
        audit();
        startSummaryTimer();
    }

    /** Проверка журнала и записи STARTUP / CEILING_CHANGED */
    public void audit() {
        FeatureJournal.Verification chain = journal.verify();
        HeadCheck head = checkHead();
        List<String> problems = new ArrayList<>();
        if (!chain.valid()) {
            problems.add("Журнал профиля: цепочка нарушена на записи " + chain.brokenAt() + " — " + chain.problem());
            tamper("chain", chain.brokenAt(), chain.problem(), head);
        }
        if (head.mismatch()) {
            problems.add("Журнал профиля: " + head.text());
            tamper("head", head.logId(), head.text(), head);
        }
        if (!journal.keyed()) {
            FeatureJournal.SECURITY.warn("Журнал профиля ведётся без ключа HMAC (awms.journal.hmac-key нет в файле "
                    + "секретов): цепочка SHA-256 выявляет правку записей, но не подделку администратором БД с "
                    + "пересчётом. Ключ создаёт init-secrets (пакет B10)");
        }
        status = new Status(true, chain, head, List.copyOf(problems));

        FeatureSnapshot ceiling = features.ceiling();
        Optional<FeatureLogEntry> previous = journal.last(FeatureLogEntry.Type.STARTUP);
        Map<String, String> profile = profile(ceiling);

        Map<String, Object> startup = new LinkedHashMap<>();
        startup.put("profile", profile);
        startup.put("presetSource", ceiling.presetSource());
        startup.put("ceilingFile", String.valueOf(ceiling.ceilingFile()));
        startup.put("ceilingFileExists", ceiling.ceilingFileExists());
        startup.put("revision", ceiling.revision());
        startup.put("port", environment == null ? null : environment.getProperty("local.server.port"));
        startup.put("journalKeyed", journal.keyed());
        if (!problems.isEmpty()) startup.put("journalProblems", problems);
        journal.append(FeatureJournal.Event.of(FeatureLogEntry.Type.STARTUP)
                .actor(serviceActor())
                .newValue(ceiling.revision())
                .details(startup));

        String oldFingerprint = previous.map(FeatureLogEntry::getFingerprint).orElse(null);
        String oldPreset = previous.map(FeatureLogEntry::getPreset).orElse(null);
        String oldSha = previous.map(FeatureLogEntry::getConfigSha256).orElse(null);
        boolean changed = previous.isEmpty() || !Objects.equals(oldFingerprint, ceiling.fingerprint())
                || !Objects.equals(oldPreset, ceiling.preset().key())
                || !Objects.equals(oldSha, ceiling.ceilingFileSha256());
        if (changed) {
            Map<String, Object> diff = new LinkedHashMap<>();
            Map<String, String> before = previous.map(StartupProfileAuditor::profileOf).orElse(null);
            diff.put("changes", before == null && previous.isPresent() ? null : changes(before, profile));
            if (previous.isEmpty()) diff.put("note", "начальный профиль: прежних записей STARTUP нет");
            else if (before == null) diff.put("note", "прежний профиль в журнале не записан, разница неизвестна");
            diff.put("presetOld", oldPreset);
            diff.put("presetNew", ceiling.preset().key());
            diff.put("configSha256Old", oldSha);
            diff.put("configSha256New", ceiling.ceilingFileSha256());
            diff.put("ticketOld", previous.map(FeatureLogEntry::getTicket).orElse(null));
            diff.put("ticketNew", ceiling.ticket());
            diff.put("osUser", FeatureJournal.serviceAccount());
            FeatureLogEntry entry = journal.append(FeatureJournal.Event.of(FeatureLogEntry.Type.CEILING_CHANGED)
                    .actor(serviceActor())
                    .oldValue(oldFingerprint)
                    .newValue(ceiling.fingerprint())
                    .reason(previous.isEmpty() ? "начальный профиль установки" : changeReason(oldFingerprint, ceiling))
                    .details(diff));
            FeatureJournal.SECURITY.info("Профиль функций изменён с прошлого запуска: запись CEILING_CHANGED {} "
                    + "(основание: {})", entry.getId(), ceiling.ticket() == null ? "—" : ceiling.ticket());
        }
        for (String problem : problems) FeatureJournal.SECURITY.warn(problem);
    }

    /** Нарушение журнала → TAMPER; о том же нарушении (тот же вид и номер) повторно не пишет */
    private void tamper(String check, Long id, String problem, HeadCheck head) {
        Optional<FeatureLogEntry> last = journal.last(FeatureLogEntry.Type.TAMPER);
        String signature = check + ":" + id;
        if (last.isPresent() && signature.equals(last.get().getNewValue())) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("check", check);
        details.put("entry", id);
        details.put("logHead", head.logId() == null ? null : Map.of("id", head.logId(), "hash", head.logHash()));
        details.put("logFile", String.valueOf(head.file()));
        journal.append(FeatureJournal.Event.of(FeatureLogEntry.Type.TAMPER)
                .actor(serviceActor())
                .newValue(signature)
                .reason(problem)
                .details(details));
    }

    /**
     * Сверка головы цепочки с файловым логом: последняя строка {@code JOURNAL_HEAD} (текущий файл, затем прежние
     * файлы ротации) должна быть в БД с тем же хэшем. Записей после неё в БД может быть больше (сбой записи лога,
     * другой экземпляр) — это не нарушение; нарушение — пропавшая или изменённая запись
     */
    public HeadCheck checkHead() {
        Path found = null;
        Long id = null;
        String hash = null;
        for (Path file : securityLogFiles()) {
            Matcher last = lastHead(file);
            if (last != null) {
                found = file;
                id = Long.parseLong(last.group(1));
                hash = last.group(2);
                break;
            }
        }
        if (id == null) {
            long count = journal.count();
            return new HeadCheck(null, null, null, false, count == 0 ? "журнал пуст, сверять нечего"
                    : "в файловом логе нет строки " + FeatureJournal.HEAD_MARKER + ": голова не сверена (первый "
                    + "запуск с журналом или лог удалён)", logDir.resolve(SECURITY_LOG_NAME));
        }
        Optional<FeatureLogEntry> row = journal.findById(id);
        if (row.isEmpty()) {
            return new HeadCheck(id, hash, null, true, "в БД нет записи " + id + ", которая есть в файловом логе "
                    + found.getFileName() + ": хвост журнала удалён", found);
        }
        if (!hash.equals(row.get().getRowHash())) {
            return new HeadCheck(id, hash, row.get().getRowHash(), true, "запись " + id
                    + " в БД отличается от головы цепочки в файловом логе " + found.getFileName(), found);
        }
        return new HeadCheck(id, hash, row.get().getRowHash(), false, "голова цепочки совпадает с файловым логом "
                + "(запись " + id + ")", found);
    }

    private List<Path> securityLogFiles() {
        List<Path> files = new ArrayList<>();
        files.add(logDir.resolve(SECURITY_LOG_NAME));
        String base = SECURITY_LOG_NAME.substring(0, SECURITY_LOG_NAME.length() - ".log".length());
        for (int i = 1; i <= SECURITY_LOG_HISTORY; i++) files.add(logDir.resolve(base + "." + i + ".log"));
        return files;
    }

    /** Последняя строка головы в файле или null */
    private static Matcher lastHead(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            // Сама строка головы — ASCII; ISO-8859-1 читает любой байт без ошибок разбора
            List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            for (int i = lines.size() - 1; i >= 0; i--) {
                Matcher m = HEAD.matcher(lines.get(i));
                if (m.find()) return m;
            }
        } catch (IOException e) {
            log.warn("Журнал безопасности {} не прочитан: {}", file, e.getMessage());
        }
        return null;
    }

    // ===================== Сводка отказов ворот =====================

    private synchronized void startSummaryTimer() {
        if (summary != null) return;
        summary = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "feature-denied-summary");
            t.setDaemon(true);
            return t;
        });
        long period = SUMMARY_PERIOD.toMillis();
        summary.scheduleAtFixedRate(this::writeDeniedSummary, period, period, TimeUnit.MILLISECONDS);
    }

    /** Отказы ворот с прошлой сводки → DENIED_SUMMARY (нет отказов — нет записи) */
    public void writeDeniedSummary() {
        try {
            FilterRegistrationBean<FeatureGateFilter> registration = gate.getIfAvailable();
            if (registration == null || registration.getFilter() == null) return;
            Map<String, Long> denied = registration.getFilter().drainDenied();
            if (denied.isEmpty()) return;
            long total = denied.values().stream().mapToLong(Long::longValue).sum();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("periodMinutes", SUMMARY_PERIOD.toMinutes());
            details.put("denied", total);
            details.put("pairs", denied.size());
            details.put("top", FeatureJournal.topDenied(denied, SUMMARY_PAIRS));
            String single = denied.keySet().stream().map(k -> k.contains(" ") ? k.substring(0, k.indexOf(' ')) : k)
                    .distinct().reduce((a, b) -> a.equals(b) ? a : "*").orElse(null);
            journal.append(FeatureJournal.Event.of(FeatureLogEntry.Type.DENIED_SUMMARY)
                    .actor(serviceActor())
                    .feature("*".equals(single) ? null : single)
                    .newValue(String.valueOf(total))
                    .details(details));
        } catch (RuntimeException e) {
            log.warn("Сводка отказов ворот не записана в журнал профиля: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void destroy() {
        if (summary == null) return;
        summary.shutdownNow();
        summary = null;
        // Отказы с последней сводки до остановки — в журнал
        writeDeniedSummary();
    }

    // ===================== Профиль =====================

    /**
     * Все ключи белого списка с итоговыми значениями — как каноническая строка отпечатка
     * ({@code awms.features.<key>=on|off;roles=…} и параметры безопасности)
     */
    public static Map<String, String> profile(FeatureSnapshot snapshot) {
        Map<String, String> result = new TreeMap<>();
        for (FeatureState s : snapshot.states().values()) {
            result.put("awms.features." + s.key(), (s.enabled() ? "on" : "off") + ";roles=" + String.join(",", s.roles()));
        }
        for (CeilingParam p : CeilingParam.values()) {
            result.put(p.key(), snapshot.param(p));
        }
        return result;
    }

    /**
     * Отпечаток по профилю: SHA-256 строк «ключ=значение», отсортированных как строки (не по ключу:
     * «video.live=…» идёт раньше «video=…»), — совпадает с отпечатком FeatureResolver
     */
    public static String fingerprintOf(Map<String, String> profile) {
        TreeSet<String> lines = new TreeSet<>();
        profile.forEach((key, value) -> lines.add(key + "=" + value));
        return JournalHmac.sha256Hex(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    /** Профиль из подробностей записи STARTUP или null */
    static Map<String, String> profileOf(FeatureLogEntry startup) {
        if (startup.getDetails() == null) return null;
        try {
            JsonNode node = JSON.readTree(startup.getDetails()).get("profile");
            if (node == null || !node.isObject()) return null;
            Map<String, String> result = new TreeMap<>();
            node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText()));
            return result;
        } catch (IOException e) {
            return null;
        }
    }

    /** Разница профилей: ключи, у которых значение изменилось, появилось или пропало */
    static List<Map<String, String>> changes(Map<String, String> before, Map<String, String> after) {
        Map<String, String> old = before == null ? Map.of() : before;
        TreeMap<String, Boolean> keys = new TreeMap<>();
        old.keySet().forEach(k -> keys.put(k, true));
        after.keySet().forEach(k -> keys.put(k, true));
        List<Map<String, String>> result = new ArrayList<>();
        for (String key : keys.keySet()) {
            String was = old.get(key);
            String now = after.get(key);
            if (Objects.equals(was, now)) continue;
            Map<String, String> change = new LinkedHashMap<>();
            change.put("key", key);
            change.put("old", was);
            change.put("new", now);
            result.add(change);
        }
        return result;
    }

    private static String changeReason(String oldFingerprint, FeatureSnapshot ceiling) {
        if (!Objects.equals(oldFingerprint, ceiling.fingerprint())) return "изменились итоговые значения потолка";
        return "изменился файл потолка (пресет, основание или запись), итоговые значения те же";
    }

    private static FeatureJournal.Actor serviceActor() {
        return new FeatureJournal.Actor(FeatureJournal.serviceAccount(), null, null);
    }

    /** Состояние журнала по итогам аудита старта: для отчёта и профиля функций */
    public Status status() {
        return status;
    }

    /**
     * Сверка головы цепочки с файловым логом.
     *
     * @param logId    номер записи из последней строки JOURNAL_HEAD или null — строки нет
     * @param logHash  хэш из этой строки
     * @param dbHash   хэш этой записи в БД или null — записи нет
     * @param mismatch нарушение: записи нет или хэш другой
     * @param text     итог словами
     * @param file     файл, где найдена строка (или где искали)
     */
    public record HeadCheck(Long logId, String logHash, String dbHash, boolean mismatch, String text, Path file) {
    }

    /**
     * Итог аудита старта.
     *
     * @param checked  аудит выполнен
     * @param chain    проверка цепочки
     * @param head     сверка головы с файловым логом
     * @param problems нарушения словами (пусто — журнал цел)
     */
    public record Status(boolean checked, FeatureJournal.Verification chain, HeadCheck head, List<String> problems) {
        static final Status NOT_CHECKED = new Status(false, null, null, List.of());
    }
}
