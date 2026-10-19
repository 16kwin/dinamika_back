package com.example.dinamika_back.features;

import com.example.dinamika_back.config.MigrationPhotoImporter;
import com.example.dinamika_back.config.imgConfig.ImgConfig;
import com.example.dinamika_back.controller.DashboardController;
import com.example.dinamika_back.controller.NomenclatureModelController;
import com.example.dinamika_back.controller.StationPlanController;
import com.example.dinamika_back.listener.WorkshopPlanFilesListener;
import com.example.dinamika_back.service.DashboardAuditService;
import com.example.dinamika_back.service.DashboardControlEventService;
import com.example.dinamika_back.service.DashboardDayEventService;
import com.example.dinamika_back.service.DashboardOperatorService;
import com.example.dinamika_back.service.DashboardPurchaseGraphService;
import com.example.dinamika_back.service.DashboardQualityService;
import com.example.dinamika_back.service.DashboardService;
import com.example.dinamika_back.service.NomenclatureModelService;
import com.example.dinamika_back.service.StationPlanService;
import com.example.dinamika_back.service.StationPlanStorage;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * План цеха, 3D, панели, унаследованная раздача и импорт фото под выключателями (пакет B4): без функции в потолке
 * бины модуля не создаются (L3), а то, что держит ядро, остаётся — хранилище планов (его внедряет слушатель удаления
 * цеха) и сервис 3D-моделей (каскадное удаление файлов позиции номенклатуры).
 */
class ModulesContextTest {

    /** map.plan: контроллер и сервис (с чисткой сирот при старте) */
    private static final List<Class<?>> PLAN_BEANS = List.of(StationPlanController.class, StationPlanService.class);
    /** model3d: только контроллер */
    private static final List<Class<?>> MODEL3D_BEANS = List.of(NomenclatureModelController.class);
    /** dashboards: контроллер и 7 сервисов (таблица 4.2 проекта) */
    private static final List<Class<?>> DASHBOARD_BEANS = List.of(DashboardController.class, DashboardService.class,
            DashboardAuditService.class, DashboardControlEventService.class, DashboardDayEventService.class,
            DashboardOperatorService.class, DashboardPurchaseGraphService.class, DashboardQualityService.class);
    /** legacy и photoimport: выключены во всех пресетах */
    private static final List<Class<?>> LEGACY_BEANS = List.of(ImgConfig.class);
    private static final List<Class<?>> PHOTOIMPORT_BEANS = List.of(MigrationPhotoImporter.class);
    /** Ядро: остаётся при любом составе функций */
    private static final List<Class<?>> CORE_BEANS = List.of(StationPlanStorage.class, WorkshopPlanFilesListener.class,
            NomenclatureModelService.class);

    private static final List<Class<?>> ALL = Stream.of(PLAN_BEANS, MODEL3D_BEANS, DASHBOARD_BEANS, LEGACY_BEANS,
            PHOTOIMPORT_BEANS, CORE_BEANS).flatMap(List::stream).toList();

    /**
     * Контекст модулей B4. Окружение процесса подменяется пустым (как в OnFeatureConditionTest); зависимости
     * (репозитории, JdbcTemplate) — моки по параметрам конструкторов; EntityManagerFactory — для @PersistenceContext
     * сервиса плана. Каталоги хранилищ не создаются: конструкторы только вычисляют путь
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ApplicationContextRunner runner() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of())))
                .withPropertyValues("station-plans.storage-dir=target/modules-context-test/plans",
                        "nomenclature.models-dir=target/modules-context-test/models")
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withUserConfiguration(FeatureService.class)
                .withUserConfiguration(ALL.toArray(Class<?>[]::new));
        for (Class type : dependencies()) {
            runner = runner.withBean(type, () -> mock(type));
        }
        return runner;
    }

    /** Типы параметров конструкторов классов модулей и ядра, кроме самих этих классов и значений @Value */
    private static Set<Class<?>> dependencies() {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> type : ALL) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    if (!ALL.contains(parameter) && parameter != String.class && !parameter.isPrimitive()) {
                        result.add(parameter);
                    }
                }
            }
        }
        return result;
    }

    private static void assertPresent(AssertableApplicationContext context, String profile, List<Class<?>> beans) {
        for (Class<?> bean : beans) assertThat(context).as(profile).hasSingleBean(bean);
    }

    private static void assertAbsent(AssertableApplicationContext context, String profile, List<Class<?>> beans) {
        for (Class<?> bean : beans) assertThat(context).as(profile).doesNotHaveBean(bean);
    }

    // ==================== Пресеты ====================

    @Test
    void isolatedCreatesNoModulesButKeepsCore() {
        runner().withPropertyValues("awms.features.preset=isolated").run(context -> {
            assertThat(context).hasNotFailed();
            assertAbsent(context, "isolated", PLAN_BEANS);
            assertAbsent(context, "isolated", MODEL3D_BEANS);
            assertAbsent(context, "isolated", DASHBOARD_BEANS);
            assertAbsent(context, "isolated", LEGACY_BEANS);
            assertAbsent(context, "isolated", PHOTOIMPORT_BEANS);
            assertPresent(context, "isolated", CORE_BEANS);
            // Задач при старте нет: импорт фото — единственный ApplicationRunner среди проверяемых классов
            assertThat(context.getBeansOfType(ApplicationRunner.class)).isEmpty();
        });
    }

    @Test
    void standardAndFullCreatePlanModelsAndDashboardsButNotLegacy() {
        for (String preset : List.of("standard", "full")) {
            runner().withPropertyValues("awms.features.preset=" + preset).run(context -> {
                assertThat(context).hasNotFailed();
                assertPresent(context, preset, PLAN_BEANS);
                assertPresent(context, preset, MODEL3D_BEANS);
                assertPresent(context, preset, DASHBOARD_BEANS);
                assertPresent(context, preset, CORE_BEANS);
                // legacy и photoimport выключены и в full (таблица 4.2)
                assertAbsent(context, preset, LEGACY_BEANS);
                assertAbsent(context, preset, PHOTOIMPORT_BEANS);
                assertThat(context.getBeansOfType(WebMvcConfigurer.class)).isEmpty();
                assertThat(context.getBeansOfType(ApplicationRunner.class)).isEmpty();
            });
        }
    }

    // ==================== Отдельные функции ====================

    @Test
    void mapPlanOffKeepsPlanStorageAndWorkshopListener() {
        List<String[]> profiles = new ArrayList<>();
        profiles.add(new String[]{"awms.features.preset=full", "awms.features.map.plan.enabled=false"});
        // Родитель выключен — план выключен каскадом
        profiles.add(new String[]{"awms.features.preset=full", "awms.features.map.enabled=false"});
        for (String[] profile : profiles) {
            String name = String.join(" ", profile);
            runner().withPropertyValues(profile).run(context -> {
                assertThat(context).hasNotFailed();
                assertAbsent(context, name, PLAN_BEANS);
                // Хранилище плана живо: удаление цеха по-прежнему удаляет файл плана
                assertThat(context).as(name).hasSingleBean(StationPlanStorage.class);
                assertThat(context).as(name).hasSingleBean(WorkshopPlanFilesListener.class);
                // Остальные модули не затронуты
                assertPresent(context, name, MODEL3D_BEANS);
                assertPresent(context, name, DASHBOARD_BEANS);
            });
        }
    }

    @Test
    void mapEditOffKeepsPlanView() {
        // Подфункция правки закрывается воротами (L2): бины просмотра остаются
        runner().withPropertyValues("awms.features.preset=full", "awms.features.map.edit.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertPresent(context, "map.edit=false", PLAN_BEANS);
        });
    }

    @Test
    void model3dOffKeepsModelService() {
        runner().withPropertyValues("awms.features.preset=full", "awms.features.model3d.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertAbsent(context, "model3d=false", MODEL3D_BEANS);
            // Сервис нужен ядру: удаление позиции номенклатуры удаляет её 3D-файлы
            assertThat(context).hasSingleBean(NomenclatureModelService.class);
            assertPresent(context, "model3d=false", PLAN_BEANS);
            assertPresent(context, "model3d=false", DASHBOARD_BEANS);
        });
    }

    @Test
    void dashboardsOffCreatesNoneOfEightBeans() {
        runner().withPropertyValues("awms.features.preset=full", "awms.features.dashboards.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertAbsent(context, "dashboards=false", DASHBOARD_BEANS);
            assertPresent(context, "dashboards=false", PLAN_BEANS);
            assertPresent(context, "dashboards=false", MODEL3D_BEANS);
        });
        // Подфункции панелей закрываются воротами (L2): бины модуля остаются
        runner().withPropertyValues("awms.features.preset=full", "awms.features.dashboards.economic.enabled=false",
                "awms.features.dashboards.audit.enabled=false", "awms.features.dashboards.screen.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertPresent(context, "dashboards.*=false", DASHBOARD_BEANS);
                });
    }

    @Test
    void legacyAndPhotoImportOnlyByExplicitCeilingKey() {
        runner().withPropertyValues("awms.features.preset=full", "awms.features.legacy.enabled=true",
                "awms.features.photoimport.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertPresent(context, "legacy+photoimport", LEGACY_BEANS);
            assertPresent(context, "legacy+photoimport", PHOTOIMPORT_BEANS);
            assertThat(context.getBeansOfType(ApplicationRunner.class)).containsOnlyKeys(
                    context.getBeanNamesForType(MigrationPhotoImporter.class));
        });
    }
}
