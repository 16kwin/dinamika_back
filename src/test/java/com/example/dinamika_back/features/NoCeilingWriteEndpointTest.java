package com.example.dinamika_back.features;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Потолок не меняется на ходу (раздел 3.2, п. 3): в API нет методов записи профиля, нет actuator и @RefreshScope.
 * Изменить потолок можно только правкой файла на сервере и перезапуском.
 */
class NoCeilingWriteEndpointTest {

    private static final Set<RequestMethod> WRITES = Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
            RequestMethod.DELETE);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Test
    void featureControllersOnlyRead() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : controllers("com.example.dinamika_back")) {
            boolean inFeatures = type.getPackageName().startsWith("com.example.dinamika_back.features");
            // Переключатели администратора (этап 2) живут в features.admin и законно принимают PUT
            boolean switches = type.getPackageName().startsWith("com.example.dinamika_back.features.admin");
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] prefixes = base == null || base.path().length == 0 ? new String[]{""} : base.path();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                Set<RequestMethod> methods = mapping.method().length == 0 ? Set.of(RequestMethod.values())
                        : Set.of(mapping.method());
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                for (String prefix : prefixes) {
                    for (String path : paths) {
                        String full = prefix + path;
                        boolean writes = methods.stream().anyMatch(WRITES::contains);
                        // Профиль функций (/api/features/**) — только чтение, кем бы ни был объявлен
                        if (writes && (MATCHER.match("/api/features", full) || MATCHER.match("/api/features/**", full))) {
                            violations.add(type.getSimpleName() + "." + method.getName() + " " + methods + " " + full);
                        }
                        // Контроллеры механизма функций: без PUT, PATCH и DELETE; FeaturesController — и без POST
                        boolean forbidden = methods.stream().anyMatch(m -> m == RequestMethod.PUT
                                || m == RequestMethod.PATCH || m == RequestMethod.DELETE
                                || (m == RequestMethod.POST && type == FeaturesController.class));
                        if (inFeatures && !switches && forbidden) {
                            violations.add(type.getSimpleName() + "." + method.getName() + " " + methods + " " + full);
                        }
                    }
                }
            }
        }
        assertThat(violations).as("методы записи профиля функций").isEmpty();
    }

    @Test
    void noRuntimeReconfiguration() {
        ClassLoader cl = getClass().getClassLoader();
        assertThat(ClassUtils.isPresent("org.springframework.cloud.context.config.annotation.RefreshScope", cl))
                .as("@RefreshScope").isFalse();
        assertThat(ClassUtils.isPresent("org.springframework.boot.actuate.endpoint.annotation.Endpoint", cl))
                .as("actuator").isFalse();
        assertThat(ClassUtils.isPresent("org.springframework.boot.devtools.restart.Restarter", cl))
                .as("devtools").isFalse();
    }

    private static List<Class<?>> controllers(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            result.add(ClassUtils.resolveClassName(bd.getBeanClassName(), NoCeilingWriteEndpointTest.class.getClassLoader()));
        }
        assertThat(result).contains(FeaturesController.class);
        return result;
    }
}
