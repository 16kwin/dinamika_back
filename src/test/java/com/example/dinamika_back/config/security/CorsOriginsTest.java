package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.FeatureConfigurationException;
import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Дополнительные адреса CORS из потолка ({@code awms.cors.allowed-origins}) добавляются к прежнему списку сервера
 * и стенда в SecurityConfig — и только точные источники. Ответы идут с учётными данными, поэтому «*», шаблоны
 * и {@code null} отменяют запуск: иначе страница с любого адреса читала бы API и CSRF-токен через браузер
 * вошедшего пользователя.
 */
class CorsOriginsTest {

    private static final String KEY = "awms.cors.allowed-origins";
    /** Прежний список адресов в SecurityConfig: действует и без файла потолка */
    private static final List<String> LEGACY = List.of("http://localhost:3000", "http://127.0.0.1:3000",
            "http://109.69.22.155:3000", "http://109.69.22.155:8084", "http://localhost", "http://dynamikaawms.ru",
            "https://dynamikaawms.ru");

    @Test
    void exactOriginsAreAccepted() {
        List<String> origins = List.of("http://localhost", "http://localhost:3000", "https://dynamikaawms.ru",
                "http://109.69.22.155:8084", "http://[::1]:3000", "https://awms.zavod.local/");
        assertThat(SecurityConfig.corsOrigins(origins)).containsExactlyElementsOf(origins);
        assertThat(SecurityConfig.corsOrigins(List.of())).isEmpty();
    }

    @Test
    void templatesAndDefaultPassTheCheck() {
        // Шаблоны потолка из комплекта поставки (D1) и умолчание без файла проходят проверку
        for (String preset : List.of("isolated", "standard", "full")) {
            FeatureSnapshot ceiling = FeatureResolver.resolveFile(Path.of("deploy", "features", preset + ".properties"));
            assertThat(SecurityConfig.corsOrigins(ceiling.listParam(CeilingParam.CORS_ALLOWED_ORIGINS))).as(preset)
                    .isNotEmpty();
        }
        // Без ключа дополнительных адресов нет: действует прежний список SecurityConfig
        FeatureSnapshot byDefault = FeatureResolver.fromProperties(Map.of("awms.features.preset", "standard"));
        assertThat(SecurityConfig.corsOrigins(byDefault.listParam(CeilingParam.CORS_ALLOWED_ORIGINS))).isEmpty();
    }

    @Test
    void wildcardsPatternsAndNonOriginsStopStartup() {
        // Список LIST режет значение по запятым: «https://*.x:[8080,8081]» приходит двумя кусками
        for (String origin : List.of("*", "https://*", "http://*:8080", "*://evil.example", "https://*.zavod.local",
                "null", "file://", "ftp://awms.zavod.local", "localhost:3000", "awms.zavod.local",
                "http://awms.zavod.local/app", "http://awms.zavod.local?x=1", "http://user@awms.zavod.local",
                "https://*.x:[8080", "8081]", "http://[8080")) {
            assertThatThrownBy(() -> SecurityConfig.corsOrigins(List.of("http://localhost", origin)))
                    .as(origin)
                    .isInstanceOf(FeatureConfigurationException.class)
                    .hasMessageStartingWith(KEY + ": «" + origin + "»");
        }
    }

    @Test
    void contextDoesNotStartWithWildcard() {
        runner().withPropertyValues(KEY + "=*").run(context -> {
            assertThat(context).hasFailed();
            Throwable cause = context.getStartupFailure();
            while (!(cause instanceof FeatureConfigurationException) && cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertThat(cause).isInstanceOf(FeatureConfigurationException.class).hasMessageContaining(KEY)
                    .hasMessageContaining("«*»");
        });
        // Тот же контекст с точным адресом поднимается: отказ выше — из-за «*», а не из-за настройки теста.
        // Адрес добавляется к прежнему списку, повтор адреса из списка не дублируется
        runner().withPropertyValues(KEY + "=https://awms.zavod.local,http://localhost:3000").run(context -> {
            assertThat(context).hasNotFailed();
            CorsConfiguration cors = context.getBean("corsConfigurationSource", CorsConfigurationSource.class)
                    .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/features"));
            assertThat(cors).isNotNull();
            List<String> expected = new ArrayList<>(LEGACY);
            expected.add("https://awms.zavod.local");
            assertThat(cors.getAllowedOrigins()).containsExactlyElementsOf(expected);
            assertThat(cors.getAllowedOriginPatterns()).isNull();
            assertThat(cors.getAllowCredentials()).isTrue();
        });
        // Пустой ключ — только прежний список
        runner().withPropertyValues(KEY + "=").run(context -> {
            assertThat(context).hasNotFailed();
            CorsConfiguration cors = context.getBean("corsConfigurationSource", CorsConfigurationSource.class)
                    .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/features"));
            assertThat(cors).isNotNull();
            assertThat(cors.getAllowedOrigins()).containsExactlyElementsOf(LEGACY);
        });
    }

    private static WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class))
                .withUserConfiguration(SecurityConfig.class)
                // Готовые объекты, а не определения бинов: сеттеры @Autowired у заглушек не вызываются
                .withInitializer(context -> {
                    context.getBeanFactory().registerSingleton("authService", mock(AuthService.class));
                    context.getBeanFactory().registerSingleton("refreshTokenService", mock(RefreshTokenService.class));
                })
                .withPropertyValues("awms.features.preset=standard");
    }
}
