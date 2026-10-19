package com.example.dinamika_back.features;

import com.example.dinamika_back.config.security.AppRoles;
import com.example.dinamika_back.features.journal.StartupProfileAuditor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Профиль функций для фронтенда (раздел 6.7 проекта). Только чтение: методов записи потолка в API нет
 * (это проверяет {@code NoCeilingWriteEndpointTest}).
 *
 * <ul>
 *   <li>{@code GET /api/features} — после входа; значения уже учитывают роли пользователя;</li>
 *   <li>{@code GET /api/features/public} — без входа, только флаги, нужные до входа ({@code pwa}, {@code passwords}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeaturesController {

    private final FeatureService features;
    /** Итог аудита журнала при старте (B6): нарушения видят роли страницы «Безопасность» */
    private final ObjectProvider<StartupProfileAuditor> auditor;

    /**
     * {@code {preset, presetSource, revision, features:{"video":false,…}, installation:{…}, ui:{inactivityMinutes,
     * warningMinutes}, canViewSecurity, canManage, alerts?}}. {@code installation} — состояние функций на установке без
     * учёта ролей пользователя (потолок, зависимости, переключатели): по нему фронтенд чистит браузер (storagePurge), не
     * стирая на общем ПК данные функций, закрытых только этому пользователю. {@code alerts} — только ролям страницы
     * «Безопасность»: нарушения журнала профиля, найденные при старте
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> profile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        FeatureSnapshot snapshot = features.snapshot();
        Map<String, Boolean> flags = new LinkedHashMap<>();
        Map<String, Boolean> installation = new LinkedHashMap<>();
        for (Feature f : Feature.real()) {
            flags.put(f.key(), snapshot.isOnFor(f, authentication));
            installation.put(f.key(), snapshot.isOn(f));
        }
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("inactivityMinutes", snapshot.intParam(CeilingParam.UI_INACTIVITY_MINUTES));
        ui.put("warningMinutes", snapshot.intParam(CeilingParam.UI_WARNING_MINUTES));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("preset", snapshot.preset().key());
        body.put("presetSource", snapshot.presetSource());
        body.put("revision", snapshot.revision());
        body.put("features", flags);
        body.put("installation", installation);
        body.put("ui", ui);
        boolean canViewSecurity = AppRoles.hasAny(authentication, AppRoles.SECURITY_VIEWERS);
        body.put("canViewSecurity", canViewSecurity);
        body.put("canManage", features.canManage(authentication));
        if (canViewSecurity) {
            // Нарушения журнала профиля (TAMPER) — плашка ролям страницы «Безопасность»; пусто — журнал цел
            StartupProfileAuditor audit = auditor.getIfAvailable();
            body.put("alerts", audit == null ? List.of() : audit.status().problems());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** {@code {revision, features:{pwa, passwords}}}: только флаги с пометкой publicFlag в реестре */
    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicProfile() {
        FeatureSnapshot snapshot = features.snapshot();
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (Feature f : Feature.real()) {
            if (f.isPublicFlag()) flags.put(f.key(), snapshot.isOn(f));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", snapshot.revision());
        body.put("features", flags);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
