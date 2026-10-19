package com.example.dinamika_back.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.dinamika_back.features.FeatureDef.absent;
import static com.example.dinamika_back.features.FeatureDef.def;
import static com.example.dinamika_back.features.FeatureLevel.L1;
import static com.example.dinamika_back.features.FeatureLevel.L2;
import static com.example.dinamika_back.features.FeatureLevel.L3;
import static com.example.dinamika_back.features.FeaturePreset.FULL;
import static com.example.dinamika_back.features.FeaturePreset.STANDARD;

/**
 * Реестр функций AWMS — единственный список (разделы 4.2, 4.3 и 6.2 проекта). Из него строятся ворота,
 * условие на бинах, {@code /api/features}, отпечаток профиля, отчёт и тесты покрытия.
 *
 * <p>Ключи: сегменты {@code [a-z0-9]+} через точку, вложенность означает родителя ({@code video.live} → {@code video}).
 * Родители и {@code requires} записаны строками (enum не может сослаться на константу ниже), строки проверяет
 * {@code FeatureCatalogContractTest}. Новая функция — одна строка; изменение списка ключей сверяется
 * с {@code src/test/resources/feature-keys.txt}.
 *
 * <p>Ворота: запрос получает 404, если совпал с любым правилом любой выключенной функции. Правило родителя
 * закрывает всех детей, правило с методом GET закрывает и HEAD.
 */
public enum Feature {

    // ===== Видео с IP-камер =====
    VIDEO(def("video", "Видео с IP-камер").on(FULL).level(L3)
            .marker("com.example.dinamika_back.controller.VideoController")
            .gate("/api/video/**").probe("/api/video/status")
            .threads("live-", "clip-", "live-idle")
            .tables("video_camera", "video_record", "video_record_file").dirProperty("video.storage-dir")
            .outbound("RTSP к IP-камерам (TCP 554, UDP RTP), процессы ffmpeg/ffprobe")),
    VIDEO_LIVE(def("video.live", "Живой просмотр и кадр").on(FULL)
            .gate("GET /api/video/cameras/*/live", "GET /api/video/live", "GET /api/video/cameras/*/snapshot")
            .probe("/api/video/cameras/1/snapshot")),
    VIDEO_CLIPS(def("video.clips", "Запись фрагмента по кнопке").on(FULL).requires("video.live", "video.download")
            .gate("POST /api/video/cameras/*/clips", "POST /api/video/clips/*/stop", "GET /api/video/clips/*/download")
            .threads("clip-")),
    VIDEO_RECORDING(def("video.recording", "Запись по событиям станции").on(FULL)
            .gate("POST /api/video/events")),
    VIDEO_ARCHIVE(def("video.archive", "Журнал видеоархива и просмотр").on(FULL)
            .gate("GET /api/video/records", "GET /api/video/records/*")
            .gate("GET /api/video/files/*/content").audited(AccessAudit.Event.VIDEO_VIEWED)),
    // L1 при разрешённом просмотре: /content отдаёт тот же файл (раздел 3.1); L2 — только с выключенным video.archive
    VIDEO_DOWNLOAD(def("video.download", "Скачивание видео на рабочее место").on(FULL).level(L1)
            .gate("GET /api/video/files/*/download", "GET /api/video/clips/*/download")
            .audited(AccessAudit.Event.VIDEO_DOWNLOADED)
            .probe("/api/video/files/1/download")),
    VIDEO_CAMERAS(def("video.cameras", "Изменение адресов камер и проверка потока").on(FULL)
            .gate("PUT /api/video/cameras/*", "POST /api/video/cameras/*/check")),

    // ===== Карта станций и план цеха =====
    // Своих API у карты нет: мнемосхема строится по /api/locations/hierarchy
    MAP(def("map", "Карта станций (мнемосхема)").on(STANDARD, FULL).level(L1).ui()),
    // StationPlanStorage остаётся бином при любом состоянии: его внедряет WorkshopPlanFilesListener
    MAP_PLAN(def("map.plan", "Интерактивный план цеха (просмотр)").on(STANDARD, FULL).level(L3)
            .marker("com.example.dinamika_back.controller.StationPlanController")
            .gate("/api/station-plans/**").probe("/api/station-plans/workshops/0")
            .tables("station_plans", "station_plan_positions").dirProperty("station-plans.storage-dir")),
    // Картинке плана ещё нужен uploads: путь /image закрыт и правилом multipart функции uploads
    MAP_EDIT(def("map.edit", "Загрузка плана и правка расстановки").on(STANDARD, FULL).requires("map.plan")
            .gate("PUT /api/station-plans/workshops/*/positions", "POST /api/station-plans/workshops/*/image",
                    "DELETE /api/station-plans/workshops/*/image")),

    // ===== 3D-модели номенклатуры =====
    // NomenclatureModelService остаётся бином ради каскадного удаления файлов; справочник /api/nomenclature/models не затрагивается
    MODEL3D(def("model3d", "3D-модели номенклатуры (просмотр)").on(STANDARD, FULL).level(L3)
            .marker("com.example.dinamika_back.controller.NomenclatureModelController")
            .gate("/api/nomenclature/*/models", "/api/nomenclature/models/3d/**")
            .gate("GET /api/nomenclature/models/3d/*/source").audited(AccessAudit.Event.MODEL3D_SOURCE)
            .tables("spr_material_models").dirProperty("nomenclature.models-dir")),
    MODEL3D_UPLOAD(def("model3d.upload", "Загрузка и удаление 3D-моделей").on(STANDARD, FULL).requires("uploads")
            .gate("POST /api/nomenclature/*/models", "DELETE /api/nomenclature/models/3d/*")),

    // ===== ГПП =====
    // Сейчас L2 (только ворота); после пакета B8 не создаются GppController и GppService (L3)
    GPP(def("gpp", "ГПП — график подготовки производства").on(STANDARD, FULL).level(L2)
            .marker("com.example.dinamika_back.controller.GppController")
            .gate("/api/gpp/**").probe("/api/gpp")
            .tables("doc_gpp", "reg_gpp_rows", "reg_gpp_row_dependencies")),

    // ===== Информационные панели главной =====
    DASHBOARDS(def("dashboards", "Информационные панели главной").on(STANDARD, FULL).level(L3)
            .marker("com.example.dinamika_back.controller.DashboardController")
            .gate("/api/dashboard/**").probe("/api/dashboard/control-events")),
    DASHBOARDS_ECONOMIC(def("dashboards.economic", "Экономический блок (затраты, бюджет)").on(STANDARD, FULL)
            .gate("/api/dashboard/economic", "/api/dashboard/economic/**")),
    DASHBOARDS_AUDIT(def("dashboards.audit", "Панель аудитора").on(STANDARD, FULL)
            .gate("GET /api/dashboard/audit").probe("/api/dashboard/audit")),
    DASHBOARDS_GRAPH(def("dashboards.graph", "Граф закупок и аффилированности").on(FULL).requires("dashboards.audit")
            .gate("GET /api/dashboard/purchase-graph")),
    DASHBOARDS_SCREEN(def("dashboards.screen", "«Второй экран» (лента событий)").on(STANDARD, FULL)
            .gate("GET /api/dashboard/day-events")),

    // ===== Заказы и ТКП =====
    ORDERS(def("orders", "Заказы и ТКП (обмен с внешней системой)").on(FULL).level(L3)
            .marker("com.example.dinamika_back.controller.OrderController")
            .gate("/api/orders/**", "/api/tkp/**").probe("/api/orders/active")
            // Колбэки SaaS (через Задел) — единственный список: его читают OrdersCallbackKeyFilter (B3)
            // и SecurityConfig (B5: permitAll и CSRF-исключения). Остальные пути заказов вызывает фронтенд
            .callbacks("POST /api/orders/*/track", "POST /api/orders/*/status", "POST /api/orders/*/statusreason",
                    "POST /api/tkp/*", "POST /api/tkp/*/statusinvoice")
            .tables("awms_orders_list", "awms_orders_full", "awms_order_statuses", "awms_order_tracking",
                    "awms_tkp_list", "awms_tkp_full", "awms_tkp_statuses")
            .outbound("HTTP на sync.service.url — только через OutboundGuard и при awms.network.outbound.allowed")),

    // ===== Живые обновления =====
    WEBSOCKET(def("websocket", "Живые обновления (WebSocket)").on(FULL).level(L3).ceilingOnly()
            .marker("com.example.dinamika_back.config.WebSocketConfig")
            .gate("/ws-stations/**").probe("/ws-stations/info")
            .threads("clientInboundChannel-", "clientOutboundChannel-", "brokerChannel-", "MessageBroker-", "SockJS-")),

    // ===== Выгрузки и загрузки =====
    // L1: убраны кнопки и серверный рендер; таблица на экране и печать браузера остаются (раздел 3.1)
    EXPORT(def("export", "Выгрузка PDF/Excel/Word и печать").on(STANDARD, FULL).level(L1)
            .gate("/api/**/export-pdf", "/api/**/export-excel", "/api/**/export-word", "/api/**/print")
            .audited(AccessAudit.Event.EXPORT)),
    // Единственные списки путей загрузки файлов. Ядро шлёт multipart и без файла (штрихкоды, поставки),
    // поэтому закрывается только файловая часть: multipart — всегда при выключенных uploads, mixed — сторожем
    // обработчика до пакета B4b (после B4b mixed-пути переходят в multipart)
    UPLOADS(def("uploads", "Загрузка файлов на сервер").on(STANDARD, FULL).level(L2)
            .multipart("POST /api/nomenclature/*/images", "POST /api/nomenclature/*/blueprints",
                    "POST /api/nomenclature/*/documents", "POST /api/nomenclature/*/models",
                    "POST /api/manufacturers-crud/*/images", "POST /api/manufacturers-crud/*/documents",
                    "POST /api/suppliers/*/images", "POST /api/suppliers/*/documents",
                    "POST /api/station-models/*/images", "POST /api/station-models/*/documents",
                    "POST /api/stations/*/documents", "POST /api/station-plans/workshops/*/image")
            .mixed("POST /api/nomenclature/*/codes", "POST /api/nomenclature/*/supply",
                    "POST /api/suppliers/*/deliveries")),
    INTEGRATIONS(def("integrations", "Вкладки «Интеграции» (журнал обмена 1С/SAP, без соединений)").on(FULL)
            .gate("/api/nomenclature/*/integrations", "/api/nomenclature/integrations/**",
                    "/api/suppliers/*/integrations", "/api/suppliers/integrations/**")),

    // ===== Доступ и браузер =====
    // Правила доступа без входа ставит SecurityConfig (пакет B5); ворот у функции нет
    ANONYMOUS(def("anonymous", "Анонимный доступ (режим табло)").on(FULL).level(L2).ceilingOnly()),
    PWA(def("pwa", "Установка как приложение (service worker)").on(FULL).level(L1).ui().publicFlag()),
    PASSWORDS(def("passwords", "Предлагать браузеру сохранить пароль").on().level(L1).ui().publicFlag()),
    DRAFTS(def("drafts", "Черновики карточек в браузере").on(STANDARD, FULL).level(L1).ui()),
    STUBS(def("stubs", "Заглушки «Отчёты», «Настройки», «Уведомления», «Карта загрузки», «Списание»").on(FULL)
            .level(L1).ui()),

    // ===== Служебное и унаследованное: выключено во всех пресетах =====
    TESTSYNC(def("testsync", "Тестовые документы и синхронизация").on().level(L3).ceilingOnly()
            .marker("com.example.dinamika_back.controller.TestDocumentController")
            .gate("/api/sync/**", "/api/test-documents/**")
            .tables("test_documents")
            .outbound("POST {sync.service.url}/api/sync/test-document — только через OutboundGuard")),
    LEGACY(def("legacy", "Унаследованная раздача /images, /proc_docs и других каталогов").on().level(L3).ceilingOnly()
            .marker("com.example.dinamika_back.config.imgConfig.ImgConfig")),
    PHOTOIMPORT(def("photoimport", "Импорт migration_photos при старте").on().level(L3).ceilingOnly()
            .marker("com.example.dinamika_back.config.MigrationPhotoImporter")),

    // ===== Отсутствует в продукте (раздел 4.3): включить нельзя, отчёт печатает обоснование =====
    AI(absent("ai", "ИИ, ML, распознавание лиц",
            "В составе нет ML-библиотек и моделей; «расчёт совместимости аналогов» детерминирован, "
                    + "predict() графа закупок — раскладка на экране")),
    TELEMETRY(absent("telemetry", "Телеметрия, отчёты об ошибках",
            "Нет actuator, Sentry и подобного; в Flyway core телеметрия не зарегистрирована")),
    REMOTE(absent("remote", "Удалённый доступ поставщика", "Нет агентов, туннелей, SSH/VNC")),
    AUTOUPDATE(absent("autoupdate", "Автообновление, сетевая проверка лицензии",
            "Нет: обновление — офлайн-пакет")),
    PUSH(absent("push", "Web Push", "Нет push-подписок, service worker не использует push")),
    CLOUD(absent("cloud", "Облачные сервисы, CDN",
            "Шрифты из @fontsource, WASM локально; ссылка SockJS на CDN убирается в пакете B3")),
    MAIL(absent("mail", "Почта (SMTP)", "Нет spring-boot-starter-mail"));

    private static final Map<String, Feature> BY_KEY = new LinkedHashMap<>();

    static {
        for (Feature f : values()) {
            if (BY_KEY.put(f.def.key(), f) != null) {
                throw new IllegalStateException("Повтор ключа функции: " + f.def.key());
            }
        }
    }

    private final FeatureDef def;

    Feature(FeatureDef def) {
        this.def = def;
    }

    /** Функция по ключу ({@code video.live}) */
    public static Optional<Feature> byKey(String key) {
        return Optional.ofNullable(key == null ? null : BY_KEY.get(key));
    }

    /** Все функции, кроме отсутствующих в продукте */
    public static List<Feature> real() {
        return Arrays.stream(values()).filter(f -> !f.isAbsent()).toList();
    }

    public String key() {
        return def.key();
    }

    /** Название для отчёта и стартовой таблицы */
    public String title() {
        return def.title();
    }

    /** Родитель по вложенности ключа или null для функции верхнего уровня */
    public Feature parent() {
        int dot = def.key().lastIndexOf('.');
        return dot < 0 ? null : BY_KEY.get(def.key().substring(0, dot));
    }

    /** Ключ родителя по вложенности или null (для контрактного теста: родитель обязан существовать) */
    public String parentKey() {
        int dot = def.key().lastIndexOf('.');
        return dot < 0 ? null : def.key().substring(0, dot);
    }

    /** Ключи функций из requires (как записаны в реестре) */
    public List<String> requiresKeys() {
        return def.requiresKeys();
    }

    /** Функции из requires */
    public List<Feature> requires() {
        List<Feature> result = new ArrayList<>();
        for (String key : def.requiresKeys()) {
            Feature f = BY_KEY.get(key);
            if (f != null) result.add(f);
        }
        return Collections.unmodifiableList(result);
    }

    /** Родитель и requires: всё, без чего функция не работает */
    public List<Feature> dependencies() {
        List<Feature> result = new ArrayList<>();
        Feature parent = parent();
        if (parent != null) result.add(parent);
        result.addAll(requires());
        return Collections.unmodifiableList(result);
    }

    public Set<FeaturePreset> presets() {
        return def.presets();
    }

    public boolean isOnIn(FeaturePreset preset) {
        return def.presets().contains(preset);
    }

    /** Уровень отключения потолком (фактический уровень печатает отчёт) */
    public FeatureLevel level() {
        return def.level();
    }

    /** Класс-маркер модуля или null */
    public String marker() {
        return def.marker();
    }

    /** Все правила функции: gate, multipart и mixed */
    public List<GateRule> rules() {
        return def.rules();
    }

    /** Входящие колбэки внешней системы (только orders) */
    public List<GateRule> callbacks() {
        return def.callbacks();
    }

    /** Адрес пробы: заданный в реестре или первое правило ворот; null — у функции нет маршрутов */
    public String probe() {
        if (def.probe() != null) return def.probe();
        return def.rules().stream().filter(r -> r.kind() == GateRule.Kind.GATE).findFirst()
                .map(GateRule::sampleUrl).orElse(null);
    }

    public List<String> threads() {
        return def.threads();
    }

    public List<String> tables() {
        return def.tables();
    }

    public List<String> dirProperties() {
        return def.dirProperties();
    }

    /** Описание сетевого взаимодействия или null */
    public String outbound() {
        return def.outbound();
    }

    /** Только потолок: переключатель администратора (этап 2) к функции не применяется */
    public boolean isCeilingOnly() {
        return def.isCeilingOnly();
    }

    /** Флаг отдаётся анонимно в {@code /api/features/public} */
    public boolean isPublicFlag() {
        return def.publicFlagValue();
    }

    /** Функция только фронтенда */
    public boolean isUiOnly() {
        return def.uiOnly();
    }

    /** Отсутствует в продукте (раздел 4.3) */
    public boolean isAbsent() {
        return def.isAbsent();
    }

    /** Обоснование «отсутствует в продукте» или null */
    public String absentReason() {
        return def.absentReason();
    }
}
