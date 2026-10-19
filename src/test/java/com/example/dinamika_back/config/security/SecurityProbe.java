package com.example.dinamika_back.config.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import java.util.Map;

/**
 * Заглушка для тестов правил доступа: отвечает 200 на любой путь и метод, если на путь нет контроллера среза.
 * В тесте 200 — запрос прошёл Spring Security, 403 — защита его отклонила (без заглушки «прошёл» выглядел бы как
 * 404 и путался с воротами).
 *
 * <p>Не контроллер, а отображение URL с порядком 1 (после контроллеров, раньше раздачи /uploads): тестовые классы
 * лежат на classpath вместе с приложением, и контроллер {@code /**} попал бы в сканирование маршрутов контрактных
 * тестов и в полный контекст. {@code @TestConfiguration} из сканирования исключён, подключается через {@code @Import}.
 */
@TestConfiguration(proxyBeanMethods = false)
class SecurityProbe {

    static final String PASSED = "passed";

    @Bean
    SimpleUrlHandlerMapping securityProbeMapping() {
        HttpRequestHandler passed = (request, response) -> {
            response.setStatus(200);
            response.getWriter().write(PASSED);
        };
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping(Map.of("/**", passed));
        mapping.setOrder(1);
        return mapping;
    }
}
