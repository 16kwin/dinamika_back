package com.example.dinamika_back.features;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * В jar нельзя «вшить» потолок: в classpath application.properties нет ключей awms.features.* и параметров
 * безопасности awms.* (раздел 4.5), а файл потолка подключается импортом по AWMS_FEATURES_FILE.
 * После пакета B10 сюда добавляется проверка секретов.
 */
class ClasspathPropertiesTest {

    private static final ConfigurationPropertyName FEATURES = ConfigurationPropertyName.of("awms.features");

    private static List<String> names() throws IOException {
        List<String> names = new ArrayList<>();
        for (PropertySource<?> source : new PropertiesPropertySourceLoader()
                .load("jar", new ClassPathResource("application.properties"))) {
            names.addAll(Arrays.asList(((EnumerablePropertySource<?>) source).getPropertyNames()));
        }
        return names;
    }

    private static String value(String key) throws IOException {
        for (PropertySource<?> source : new PropertiesPropertySourceLoader()
                .load("jar", new ClassPathResource("application.properties"))) {
            Object v = source.getProperty(key);
            if (v != null) return v.toString();
        }
        return null;
    }

    @Test
    void noFeatureKeysAndNoAwmsSecurityParametersInJar() throws IOException {
        List<String> names = names();
        assertThat(names).isNotEmpty();
        for (String raw : names) {
            ConfigurationPropertyName name = ConfigurationPropertyName.adapt(raw, '.');
            assertThat(FEATURES.isAncestorOf(name) || FEATURES.equals(name))
                    .as("ключ профиля функций в jar: " + raw).isFalse();
            CeilingParam param = CeilingParam.byName(name);
            if (param != null) {
                assertThat(param.key()).as("параметр безопасности awms.* в jar: " + raw).doesNotStartWith("awms.");
            }
        }
    }

    @Test
    void ceilingFileIsImportedByVariableWithDefaultNextToJar() throws IOException {
        assertThat(value("spring.config.import"))
                .contains("optional:file:${AWMS_FEATURES_FILE:./config/awms-features.properties}");
    }
}
