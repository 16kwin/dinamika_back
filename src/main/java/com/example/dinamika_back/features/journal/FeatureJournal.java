package com.example.dinamika_back.features.journal;

import com.example.dinamika_back.config.security.AppRoles;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Журнал профиля (раздел 10.1 проекта): что включено на установке, кто и почему это менял, проверка цепочки.
 *
 * <ul>
 *   <li>Запись — только добавлением, в транзакции с {@code pg_advisory_xact_lock}: у каждой строки свой предыдущий
 *       хэш, {@code row_hash} — HMAC с ключом вне БД ({@link JournalHmac}).</li>
 *   <li>После каждой записи (после фиксации транзакции) в файловый лог {@code AWMS.SECURITY} уходит строка
 *       {@code JOURNAL_HEAD id=… hash=…} (логгер {@value #HEAD_LOGGER}): удаление хвоста журнала выявляет сверка
 *       головы с логом и с бумагой.</li>
 *   <li>Отпечаток, пресет, основание и SHA-256 файла потолка подставляются в каждую запись сами; автор и адрес — из
 *       текущего запроса, вне запроса — учётная запись ОС службы.</li>
 * </ul>
 */
@Component
public class FeatureJournal {

    /** Логгер файлового журнала безопасности: logs/awms-security.log (logback-spring.xml) */
    public static final Logger SECURITY = LoggerFactory.getLogger("AWMS.SECURITY");
    /**
     * Логгер строк головы цепочки — дочерний к AWMS.SECURITY, пишет в тот же файл. Им пишется только строка головы,
     * и сверка при старте принимает только его строки: маркер в чужом сообщении (путь запроса, имя) головой не станет
     */
    public static final String HEAD_LOGGER = "AWMS.SECURITY.HEAD";
    /** Строка головы цепочки в файловом логе; её же ищет сверка при старте */
    public static final String HEAD_MARKER = "JOURNAL_HEAD";
    private static final Logger HEAD_LOG = LoggerFactory.getLogger(HEAD_LOGGER);
    /**
     * Атрибут запроса с адресом клиента, разобранным за доверенным прокси (X-Forwarded-For / X-Real-IP — только от
     * доверенных адресов прокси; ставит пакет B5). Журналы берут адрес отсюда, без атрибута — getRemoteAddr()
     */
    public static final String CLIENT_IP_ATTRIBUTE = "awms.clientIp";

    /** Ключ блокировки цепочки (pg_advisory_xact_lock): «AWMSJRNL» */
    static final long LOCK_KEY = 0x41574D534A524E4CL;
    /** Порция записей при проверке цепочки */
    private static final int VERIFY_BATCH = 500;
    /** Предел JSON подробностей: сводка отказов и пробы из браузера не раздувают журнал */
    static final int DETAILS_MAX = 20_000;

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final FeatureLogRepository repository;
    private final FeatureService features;
    private final JournalHmac hmac;
    private final String hostName;
    private final String appVersion;
    private final Clock clock;

    @Autowired
    public FeatureJournal(FeatureLogRepository repository, FeatureService features, Environment environment,
                          ObjectProvider<BuildProperties> build) {
        this(repository, features, new JournalHmac(environment.getProperty("awms.journal.hmac-key")),
                hostName(), appVersion(build.getIfAvailable()), Clock.systemDefaultZone());
    }

    /** Для тестов: репозиторий в памяти, свой ключ и часы */
    public FeatureJournal(FeatureLogRepository repository, FeatureService features, JournalHmac hmac, String hostName,
                          String appVersion, Clock clock) {
        this.repository = repository;
        this.features = features;
        this.hmac = hmac;
        this.hostName = hostName;
        this.appVersion = appVersion;
        this.clock = clock;
    }

    // ===================== Запись =====================

    /**
     * Добавить запись. Транзакция своя или вызывающего (тогда строка и голова в логе появятся после его фиксации).
     * Ошибка записи не глотается: событие профиля без записи в журнал — повод остановиться
     */
    @Transactional
    public FeatureLogEntry append(Event event) {
        repository.lockChain(LOCK_KEY);
        FeatureLogEntry previous = repository.findTopByOrderByIdDesc().orElse(null);
        FeatureSnapshot ceiling = features.ceiling();
        Actor actor = event.actor != null ? event.actor : currentActor();

        FeatureLogEntry e = new FeatureLogEntry();
        e.setId(repository.nextId());
        e.setOccurredAt(JournalHmac.truncate(LocalDateTime.now(clock)));
        e.setEventType(event.type.name());
        e.setFeatureKey(clip(event.featureKey, 64));
        e.setOldValue(clip(event.oldValue, 255));
        e.setNewValue(clip(event.newValue, 255));
        e.setPreset(ceiling.preset().key());
        e.setFingerprint(ceiling.fingerprint());
        e.setTicket(clip(ceiling.ticket(), 500));
        e.setReason(clip(event.reason, 1000));
        e.setActor(clip(actor.login(), 255));
        e.setActorRole(clip(actor.role(), 64));
        e.setClientIp(clip(actor.ip(), 64));
        e.setHostName(clip(hostName, 255));
        e.setAppVersion(clip(appVersion, 64));
        e.setConfigSha256(ceiling.ceilingFileSha256());
        e.setDetails(json(event.details));
        e.setPrevHash(previous == null ? null : previous.getRowHash());
        e.setKeyId(hmac.keyId());
        e.setRowHash(hmac.rowHash(e));
        e.setFresh(true);
        repository.save(e);
        e.setFresh(false);

        afterCommit(() -> HEAD_LOG.info("{} id={} hash={} type={}", HEAD_MARKER, e.getId(), e.getRowHash(),
                e.getEventType()));
        return e;
    }

    /** Исходящий запрос заблокирован сторожем (OutboundGuard, пакет B3): {@code OUTBOUND_BLOCKED} */
    public FeatureLogEntry outboundBlocked(Feature feature, String target, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("target", target);
        return append(Event.of(FeatureLogEntry.Type.OUTBOUND_BLOCKED)
                .feature(feature == null ? null : feature.key())
                .newValue(target)
                .reason(reason)
                .details(details));
    }

    // ===================== Чтение =====================

    /** Голова цепочки: последняя запись */
    public Optional<Head> head() {
        return repository.findTopByOrderByIdDesc().map(e -> new Head(e.getId(), e.getRowHash()));
    }

    public Optional<FeatureLogEntry> findById(long id) {
        return repository.findById(id);
    }

    /** Последняя запись данного типа */
    public Optional<FeatureLogEntry> last(FeatureLogEntry.Type type) {
        return repository.findTopByEventTypeOrderByIdDesc(type.name());
    }

    /** Последние записи, новые сверху */
    public List<FeatureLogEntry> recent(int limit) {
        return repository.findByOrderByIdDesc(PageRequest.of(0, limit));
    }

    /** Последние записи данных типов, новые сверху */
    public List<FeatureLogEntry> recent(Collection<FeatureLogEntry.Type> types, int limit) {
        return repository.findByEventTypeInOrderByIdDesc(types.stream().map(Enum::name).toList(),
                PageRequest.of(0, limit));
    }

    public long count() {
        return repository.count();
    }

    /** Есть ли ключ HMAC (иначе цепочка SHA-256 без ключа: подделку администратором БД она не выявляет) */
    public boolean keyed() {
        return hmac.keyed();
    }

    public String keyId() {
        return hmac.keyId();
    }

    /**
     * Проверка цепочки от первой записи до последней. Нарушение — первая запись, у которой предыдущий хэш не совпал
     * с хэшем предыдущей строки (строку удалили или вставили), хэш не сходится (строку изменили), строка без ключа
     * идёт после строк с ключом или подписана ключом, которого на установке нет
     */
    public Verification verify() {
        String prevHash = null;
        boolean keyedSeen = false;
        long checked = 0;
        long lastId = 0;
        Head head = null;
        while (true) {
            List<FeatureLogEntry> batch = repository.findByIdGreaterThanOrderByIdAsc(lastId,
                    PageRequest.of(0, VERIFY_BATCH));
            if (batch.isEmpty()) break;
            for (FeatureLogEntry row : batch) {
                String problem = null;
                if (!Objects.equals(row.getPrevHash(), prevHash)) {
                    problem = prevHash == null
                            ? "первая запись ссылается на предыдущую, которой нет: начало журнала удалено"
                            : "предыдущий хэш не совпадает с записью перед ней: запись удалена, вставлена или "
                            + "переставлена в обход приложения";
                } else if (JournalHmac.NO_KEY.equals(row.getKeyId()) && keyedSeen) {
                    problem = "запись без ключа HMAC после записей с ключом";
                } else {
                    String expected = hmac.hash(row.getKeyId(), row);
                    if (expected == null) {
                        problem = "запись подписана ключом " + row.getKeyId() + ", которого на установке нет";
                    } else if (!expected.equals(row.getRowHash())) {
                        problem = "хэш записи не сходится: запись изменена в обход приложения";
                    }
                }
                if (problem != null) {
                    return new Verification(false, row.getId(), problem, checked, head, keyedSeen);
                }
                if (!JournalHmac.NO_KEY.equals(row.getKeyId())) keyedSeen = true;
                prevHash = row.getRowHash();
                head = new Head(row.getId(), row.getRowHash());
                checked++;
                lastId = row.getId();
            }
        }
        return new Verification(true, null, null, checked, head, keyedSeen);
    }

    // ===================== Служебное =====================

    /**
     * Кто действует: вошедший пользователь и адрес запроса; вне запроса — учётная запись ОС службы. Адрес — атрибут
     * запроса {@link #CLIENT_IP_ATTRIBUTE}, если его поставил разбор адреса клиента за доверенным прокси (пакет B5),
     * иначе адрес соединения (за nginx это адрес nginx)
     */
    public static Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String ip = null;
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            Object resolved = servlet.getRequest().getAttribute(CLIENT_IP_ATTRIBUTE);
            ip = resolved instanceof String s && !s.isBlank() ? s : servlet.getRequest().getRemoteAddr();
        }
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken) && authentication.getName() != null) {
            String role = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                    .map(AppRoles::roleName).filter(r -> !r.isEmpty()).sorted().findFirst().orElse(null);
            return new Actor(authentication.getName(), role, ip);
        }
        return new Actor(ip == null ? serviceAccount() : "аноним", null, ip);
    }

    /** Учётная запись ОС, под которой работает служба: «os:LocalService» */
    public static String serviceAccount() {
        return "os:" + System.getProperty("user.name", "?");
    }

    /** Имя компьютера: переменная ОС или имя хоста */
    public static String hostName() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null || name.isBlank()) name = System.getenv("HOSTNAME");
        if (name != null && !name.isBlank()) return name.trim();
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "?";
        }
    }

    static String appVersion(BuildProperties build) {
        return build == null ? "без build-info" : build.getVersion();
    }

    /** JSON подробностей: ключи по алфавиту — запись читается одинаково; сверх предела — только размер */
    static String json(Map<String, ?> details) {
        if (details == null || details.isEmpty()) return null;
        try {
            String text = JSON.writeValueAsString(new TreeMap<>(details));
            if (text.length() > DETAILS_MAX) {
                text = JSON.writeValueAsString(Map.of("truncated", true, "length", text.length()));
            }
            return clean(text);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"подробности не сериализуются\"}";
        }
    }

    /** Без NUL (PostgreSQL его не хранит) и не длиннее столбца */
    static String clip(String value, int max) {
        if (value == null) return null;
        String text = clean(value);
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String clean(String value) {
        return value.indexOf('\0') < 0 ? value : value.replace("\0", "");
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // ===================== Типы =====================

    /** Кто действует: логин (или учётная запись ОС), роль, адрес клиента */
    public record Actor(String login, String role, String ip) {
    }

    /** Голова цепочки: номер и хэш последней записи */
    public record Head(long id, String hash) {
    }

    /**
     * Итог проверки цепочки.
     *
     * @param valid     цепочка цела
     * @param brokenAt  номер первой записи с нарушением или null
     * @param problem   что нарушено или null
     * @param checked   сколько записей проверено до нарушения (или всего)
     * @param head      последняя проверенная запись (голова, если цепочка цела)
     * @param keyedSeen в цепочке есть записи с ключом HMAC
     */
    public record Verification(boolean valid, Long brokenAt, String problem, long checked, Head head,
                               boolean keyedSeen) {
    }

    /** Событие для записи: тип, функция, «было → стало», причина, подробности (JSON) */
    public static final class Event {
        private final FeatureLogEntry.Type type;
        private String featureKey;
        private String oldValue;
        private String newValue;
        private String reason;
        private Actor actor;
        private Map<String, ?> details;

        private Event(FeatureLogEntry.Type type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        public static Event of(FeatureLogEntry.Type type) {
            return new Event(type);
        }

        public Event feature(String key) {
            this.featureKey = key;
            return this;
        }

        public Event oldValue(String value) {
            this.oldValue = value;
            return this;
        }

        public Event newValue(String value) {
            this.newValue = value;
            return this;
        }

        public Event reason(String value) {
            this.reason = value;
            return this;
        }

        /** Автор явно (по умолчанию — текущий пользователь или учётная запись службы) */
        public Event actor(Actor value) {
            this.actor = value;
            return this;
        }

        public Event details(Map<String, ?> value) {
            this.details = value;
            return this;
        }

        public FeatureLogEntry.Type type() {
            return type;
        }
    }

    /** Сводка отказов ворот для DENIED_SUMMARY: пары «функция адрес» по убыванию, не больше {@code limit} */
    static List<Map<String, Object>> topDenied(Map<String, Long> denied, int limit) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(denied.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : entries) {
            if (result.size() >= limit) break;
            String key = entry.getKey();
            int space = key.indexOf(' ');
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("feature", space < 0 ? key : key.substring(0, space));
            pair.put("ip", space < 0 ? null : key.substring(space + 1));
            pair.put("count", entry.getValue());
            result.add(pair);
        }
        return result;
    }
}
