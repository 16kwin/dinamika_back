package com.example.dinamika_back.features;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Условие {@link ConditionalOnFeature}: сверяет функцию с потолком установки ({@link FeatureResolver}).
 * Ошибка профиля (опечатка, ключ из окружения) здесь же отменяет запуск.
 */
class OnFeatureCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        MergedAnnotation<ConditionalOnFeature> annotation = metadata.getAnnotations().get(ConditionalOnFeature.class);
        Feature feature = annotation.getEnum("value", Feature.class);
        boolean wanted = annotation.getBoolean("enabled");
        FeatureState state = FeatureResolver.resolve(context.getEnvironment()).state(feature);
        if (state.enabled() == wanted) {
            return ConditionOutcome.match("функция " + state.key() + (wanted ? " разрешена потолком" : ": "
                    + state.reasonText()));
        }
        return ConditionOutcome.noMatch("функция " + state.key() + (wanted ? ": " + state.reasonText()
                : " разрешена потолком, заглушка не нужна"));
    }
}
