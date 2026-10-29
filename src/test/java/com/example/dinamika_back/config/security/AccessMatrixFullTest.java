package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.FeatureWebConfig;
import com.example.dinamika_back.features.FeaturesController;
import com.example.dinamika_back.service.AuthService;
import com.example.dinamika_back.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тестовый потолок full (как на стенде): слой правил потолка не меняет ни одного ответа защиты — таблица
 * {@link AccessMatrix} по всем маршрутам совпадает с эталоном, снятым до слоя. Ворота функций подключены, как
 * в службе: закрытые в full функции (testsync) отвечают 404 до Spring Security.
 */
@WebMvcTest(controllers = FeaturesController.class)
// Тысячи запросов: печать каждого при сбое затопила бы лог
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class, FeatureWebConfig.class})
class AccessMatrixFullTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private Environment environment;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void fullCeilingChangesNothing() throws Exception {
        assertThat(FeatureResolver.resolve(environment).preset().key()).isEqualTo("full");
        Map<String, String> table = AccessMatrix.table(mvc);
        if (Boolean.getBoolean(AccessMatrix.RECORD_PROPERTY)) {
            AccessMatrix.record(table);
            return;
        }
        assertSameAsGolden(table);
    }

    /** Каждая строка эталона совпадает; маршруты, появившиеся после эталона, только перечисляются */
    static void assertSameAsGolden(Map<String, String> table) throws Exception {
        Map<String, String> golden = AccessMatrix.golden();
        List<String> changed = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, String> e : table.entrySet()) {
            String expected = golden.get(e.getKey());
            if (expected == null) {
                added.add(e.getValue());
            } else if (!expected.equals(e.getValue())) {
                changed.add("было: " + expected + "\n  стало: " + e.getValue());
            }
        }
        if (!added.isEmpty()) {
            System.out.println("Маршруты вне эталона " + AccessMatrix.GOLDEN + " (" + added.size() + "):\n  "
                    + String.join("\n  ", added));
        }
        assertThat(changed).as("ответы защиты, отличные от эталона (до слоя правил потолка); если правила изменены "
                + "намеренно — пересоздать эталон, см. AccessMatrix").isEmpty();
        // Эталон описывает настоящую таблицу, а не пустую: в нём все маршруты контроллеров того времени
        assertThat(golden).hasSizeGreaterThan(700);
    }
}
