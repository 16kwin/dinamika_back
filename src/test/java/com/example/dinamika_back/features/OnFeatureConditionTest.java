package com.example.dinamika_back.features;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Условие {@link ConditionalOnFeature}: бин есть, только когда потолок разрешает функцию; заглушка
 * ({@code enabled = false}) — ровно наоборот; причина видна в отчёте условий; ошибка профиля отменяет запуск.
 */
class OnFeatureConditionTest {

    /** Модуль: конфигурация целиком под условием, как у будущих модулей (раздел 12 проекта) */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnFeature(Feature.VIDEO)
    static class VideoModule {
        @Bean
        String videoBean() {
            return "видео";
        }
    }

    /** Заглушка «когда функции нет» */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnFeature(value = Feature.VIDEO, enabled = false)
    static class VideoStub {
        @Bean
        String videoStub() {
            return "заглушка";
        }
    }

    /** Условие на методе @Bean */
    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        @ConditionalOnFeature(Feature.ORDERS)
        Integer ordersClient() {
            return 1;
        }
    }

    /**
     * Окружение процесса подменяется пустым: переменная разработчика AWMS_FEATURES_PRESET (или любая AWMS_FEATURES_*)
     * при запуске из IDE иначе отменила бы контекст, ведь тестовый потолок задан
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            Map.of())))
            .withUserConfiguration(VideoModule.class, VideoStub.class, Beans.class);

    @Test
    void fullCreatesModuleBeansAndNoStubs() {
        runner.withPropertyValues("awms.features.preset=full").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("videoBean").doesNotHaveBean("videoStub").hasBean("ordersClient");
        });
    }

    @Test
    void isolatedCreatesStubsOnly() {
        runner.withPropertyValues("awms.features.preset=isolated").run(context -> {
            assertThat(context).doesNotHaveBean("videoBean").hasBean("videoStub").doesNotHaveBean("ordersClient");
            // Причина видна в отчёте условий (--debug)
            ConditionEvaluationReport report = ConditionEvaluationReport.get(context.getBeanFactory());
            List<String> messages = new ArrayList<>();
            report.getConditionAndOutcomesBySource().values().forEach(outcomes ->
                    outcomes.forEach(o -> messages.add(o.getOutcome().getMessage())));
            assertThat(messages).anySatisfy(m -> assertThat(m).contains("функция video").contains("выключена пресетом"));
        });
    }

    @Test
    void explicitKeyOverridesPreset() {
        runner.withPropertyValues("awms.features.preset=full", "awms.features.video.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean("videoBean").hasBean("videoStub").hasBean("ordersClient");
        });
    }

    @Test
    void ceilingOverrideFromEnvironmentFailsContext() {
        runner.withPropertyValues("awms.features.preset=full")
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("AWMS_FEATURES_VIDEO_ENABLED", "true"))))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(FeatureConfigurationException.class)
                            .hasMessageContaining("AWMS_FEATURES_VIDEO_ENABLED");
                });
    }
}
