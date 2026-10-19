package com.example.dinamika_back.features;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Состояние функций для ворот, сторожей и API. Текущий снимок = потолок ∧ переключатели администратора;
 * на этапе 1 — только потолок. Пакет B7 подменяет снимок переключателями (ворота и сторож действуют сразу).
 *
 * <p>Сторож ставится на входе методов, которые запускают процесс, открывают соединение или рассылают данные:
 * {@code features.require(Feature.VIDEO_LIVE)} — второй рубеж за воротами, процесс ffmpeg для выключенной
 * подфункции не стартует даже при внутреннем вызове.
 */
@Component
public class FeatureService {

    private final FeatureSnapshot ceiling;
    private final AtomicReference<FeatureSnapshot> current;

    @Autowired
    public FeatureService(Environment environment) {
        this(FeatureResolver.resolve(environment));
    }

    /** Для тестов модулей: {@code new FeatureService(FeatureResolver.fromProperties(Map.of(...)))} */
    public FeatureService(FeatureSnapshot ceiling) {
        this.ceiling = ceiling;
        this.current = new AtomicReference<>(ceiling);
    }

    /** Снимок потолка: меняется только перезапуском */
    public FeatureSnapshot ceiling() {
        return ceiling;
    }

    /** Текущий снимок (потолок ∧ переключатели) */
    public FeatureSnapshot snapshot() {
        return current.get();
    }

    public boolean isOn(Feature feature) {
        return current.get().isOn(feature);
    }

    /** Включена и доступна пользователю по ролям из потолка */
    public boolean isOnFor(Feature feature, Authentication authentication) {
        return current.get().isOnFor(feature, authentication);
    }

    /** Сторож: функция выключена → 404 ({@link FeatureDisabledException}) */
    public void require(Feature feature) {
        if (!isOn(feature)) throw new FeatureDisabledException(feature);
    }

    /** Ревизия профиля для заголовка {@code X-AWMS-Features} */
    public String revision() {
        return current.get().revision();
    }

    /**
     * Может ли пользователь управлять переключателями. Переключателей до этапа 2 нет (пакет B7):
     * режим {@code awms.features.admin.mode} и роли проверит B7.
     */
    public boolean canManage(Authentication authentication) {
        return false;
    }
}
