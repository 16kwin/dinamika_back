package com.example.dinamika_back.features.report;

import com.example.dinamika_back.config.security.AppRoles;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.example.dinamika_back.features.journal.FeatureLogEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Отчёт «Профиль безопасности» и события страницы «Безопасность» (раздел 6.7 проекта). Доступ — роли
 * {@link AppRoles#SECURITY_VIEWERS} (ADMIN, DIRECTOR, SECURITY), проверка здесь же, как в StationPlanController.
 * Ворота функций эти пути не закрывают никогда; методов записи потолка нет (это проверяет
 * {@code NoCeilingWriteEndpointTest}): POST пишет только событие в журнал профиля.
 *
 * <ul>
 *   <li>{@code GET /api/security-profile} — полный отчёт (раздел 9.2);</li>
 *   <li>{@code POST /api/security-profile/events} {@code {type, fingerprint, revision, details}} — {@code REPORT_VIEWED},
 *       {@code REPORT_PRINTED}, {@code REPORT_EXPORTED}, {@code PROBE_RUN}. Тот же пользователь с тем же событием
 *       и теми же подробностями в течение минуты пишется один раз.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/security-profile")
public class SecurityProfileController {

    /** События, которые пишет страница «Безопасность» */
    static final Set<FeatureLogEntry.Type> PAGE_EVENTS = Set.of(FeatureLogEntry.Type.REPORT_VIEWED,
            FeatureLogEntry.Type.REPORT_PRINTED, FeatureLogEntry.Type.REPORT_EXPORTED, FeatureLogEntry.Type.PROBE_RUN);
    private static final Duration REPEAT_WINDOW = Duration.ofMinutes(1);
    private static final int REPEAT_KEYS_MAX = 1_000;
    private static final int DETAILS_MAX = 8_000;
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{12,64}");
    private static final Pattern REVISION = Pattern.compile("[0-9a-fA-F]{12}-\\d{1,18}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SecurityProfileService reports;
    private final FeatureJournal journal;
    private final FeatureService features;
    private final Clock clock;
    private final Map<String, Long> recent = new ConcurrentHashMap<>();

    @Autowired
    public SecurityProfileController(SecurityProfileService reports, FeatureJournal journal, FeatureService features) {
        this(reports, journal, features, Clock.systemUTC());
    }

    SecurityProfileController(SecurityProfileService reports, FeatureJournal journal, FeatureService features,
                              Clock clock) {
        this.reports = reports;
        this.journal = journal;
        this.features = features;
        this.clock = clock;
    }

    /** Полный отчёт «Профиль безопасности» */
    @GetMapping
    public ResponseEntity<Map<String, Object>> report() {
        Authentication viewer = requireViewer();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.report(viewer));
    }

    /** Событие страницы «Безопасность» в журнал профиля */
    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> event(@RequestBody(required = false) EventRequest request) {
        Authentication viewer = requireViewer();
        if (request == null || request.type() == null) throw badRequest("Не указан тип события");
        FeatureLogEntry.Type type;
        try {
            type = FeatureLogEntry.Type.valueOf(request.type().trim());
        } catch (IllegalArgumentException e) {
            throw badRequest("Неизвестный тип события");
        }
        if (!PAGE_EVENTS.contains(type)) throw badRequest("Это событие страница не пишет");
        String fingerprint = request.fingerprint() == null ? null : request.fingerprint().trim();
        if (fingerprint != null && !HEX.matcher(fingerprint).matches()) throw badRequest("Некорректный отпечаток");
        String revision = request.revision() == null ? null : request.revision().trim();
        if (revision != null && !REVISION.matcher(revision).matches()) throw badRequest("Некорректная ревизия");

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reportFingerprint", fingerprint);
        details.put("reportRevision", revision);
        // Отчёт снят с того же профиля, что работает сейчас (иначе — страница устарела после перезапуска)
        details.put("sameProfile", SecurityProfileService.sameFingerprint(fingerprint, features.ceiling()));
        if (request.details() != null && !request.details().isEmpty()) details.put("page", pageDetails(request.details()));

        String key = viewer.getName() + "|" + type + "|" + details.hashCode();
        long now = clock.millis();
        Long last = recent.get(key);
        Map<String, Object> body = new LinkedHashMap<>();
        if (last != null && now - last < REPEAT_WINDOW.toMillis()) {
            body.put("recorded", false);
            body.put("duplicate", true);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
        }
        FeatureLogEntry entry = journal.append(FeatureJournal.Event.of(type)
                .newValue(fingerprint == null ? null : fingerprint.toLowerCase())
                .details(details));
        remember(key, now);
        body.put("recorded", true);
        body.put("duplicate", false);
        body.put("id", entry.getId());
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("id", entry.getId());
        head.put("hash", entry.getRowHash());
        body.put("head", head);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** Подробности страницы (результаты проб и т. п.) — как прислала страница, но не больше предела */
    private static Object pageDetails(Map<String, Object> details) {
        try {
            String text = JSON.writeValueAsString(details);
            if (text.length() <= DETAILS_MAX) return details;
            return Map.of("truncated", true, "length", text.length());
        } catch (JsonProcessingException e) {
            return Map.of("error", "подробности не разобраны");
        }
    }

    private void remember(String key, long now) {
        if (recent.size() >= REPEAT_KEYS_MAX) {
            recent.entrySet().removeIf(e -> now - e.getValue() >= REPEAT_WINDOW.toMillis());
            if (recent.size() >= REPEAT_KEYS_MAX) recent.clear();
        }
        recent.put(key, now);
    }

    private static Authentication requireViewer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!AppRoles.hasAny(authentication, AppRoles.SECURITY_VIEWERS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Профиль безопасности доступен ролям ADMIN, "
                    + "DIRECTOR и SECURITY");
        }
        return authentication;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** Тело события страницы */
    public record EventRequest(String type, String fingerprint, String revision, Map<String, Object> details) {
    }
}
