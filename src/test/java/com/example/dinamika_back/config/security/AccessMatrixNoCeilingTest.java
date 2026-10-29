package com.example.dinamika_back.config.security;

import com.example.dinamika_back.features.FeatureResolver;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.FeatureSnapshot;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Как на сервере команды dynamikaawms.ru: файла потолка нет (путь указывает в пустоту), действует пресет full.
 * Таблица ответов защиты по всем маршрутам совпадает с эталоном, снятым до слоя правил потолка.
 */
@WebMvcTest(controllers = FeaturesController.class)
// Тысячи запросов: печать каждого при сбое затопила бы лог
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({SecurityConfig.class, FeatureService.class, SecurityProbe.class, FeatureWebConfig.class})
@TestPropertySource(properties = "AWMS_FEATURES_FILE=target/no-such-dir/awms-features.properties")
class AccessMatrixNoCeilingTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private Environment environment;

    @MockBean
    private AuthService authService;
    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    void withoutCeilingFileServerBehavesAsBefore() throws Exception {
        FeatureSnapshot ceiling = FeatureResolver.resolve(environment);
        assertThat(ceiling.ceilingFileExists()).isFalse();
        assertThat(ceiling.preset().key()).isEqualTo("full");
        AccessMatrixFullTest.assertSameAsGolden(AccessMatrix.table(mvc));
    }
}
