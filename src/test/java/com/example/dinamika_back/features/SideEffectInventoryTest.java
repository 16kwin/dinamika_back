package com.example.dinamika_back.features;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Инвентаризация побочных эффектов (раздел 6.4 проекта): каждый процесс, сетевой клиент, поток, планировщик
 * и задача при старте в src/main/java должны быть отнесены к функции или к ядру. Будущий модуль не сможет
 * незаметно открыть соединение, запустить процесс или поток: тест назовёт файл и строку.
 *
 * <p>Исходящие HTTP новых модулей — только через OutboundGuard (B3). Ставить новую строку в список — осознанное
 * решение: функция должна выключаться потолком вместе со своим побочным эффектом.
 */
class SideEffectInventoryTest {

    private static final Path SOURCES = Path.of("src/main/java/com/example/dinamika_back");

    /** Шаблоны побочных эффектов: процессы и сеть; потоки и планировщики; запуск и события */
    private static final List<Pattern> PATTERNS = Stream.of(
            "new ProcessBuilder\\(", "Runtime\\.getRuntime\\(\\)\\.exec", "new RestTemplate\\(", "RestTemplateBuilder",
            "WebClient", "HttpClient", "new Socket\\(", "ServerSocket", "DatagramSocket", "openConnection\\(",
            "openStream\\(", "new URL\\(",
            "new Thread\\(", "Thread\\.(ofVirtual|ofPlatform|startVirtualThread)", "Executors\\.",
            "ThreadPoolExecutor", "ForkJoinPool", "CompletableFuture\\.\\w*Async\\(", "new Timer\\(", "TaskScheduler",
            "@Scheduled", "@EnableScheduling", "@Async\\b", "@EnableAsync", "SmartLifecycle",
            "ApplicationRunner", "CommandLineRunner", "@PostConstruct", "@EventListener\\([^)]*ApplicationReadyEvent",
            "ApplicationListener<", "@TransactionalEventListener", "@EnableWebSocketMessageBroker"
    ).map(Pattern::compile).toList();

    /** Файл (относительно B/) → функция реестра или «core» */
    private static final Map<String, String> INVENTORY = new TreeMap<>(Map.ofEntries(
            // Видео: ffmpeg/ffprobe, потоки live-*, clip-*, планировщик live-idle, @PostConstruct recoverInterrupted
            Map.entry("service/FfmpegRunner.java", "video"),
            Map.entry("service/LiveChannel.java", "video"),
            Map.entry("service/RecorderProcess.java", "video"),
            Map.entry("service/VideoRecordingService.java", "video"),
            Map.entry("service/LiveStreamService.java", "video"),
            // Исходящий HTTP заказов и тестовой синхронизации
            Map.entry("config/RestTemplateConfig.java", "orders"),
            Map.entry("service/SyncService.java", "testsync"),
            // Задачи при старте
            Map.entry("config/MigrationPhotoImporter.java", "photoimport"),
            Map.entry("service/StationPlanService.java", "map.plan"),
            // Брокер STOMP и рассылка после коммита
            Map.entry("config/WebSocketConfig.java", "websocket"),
            Map.entry("controller/StationWebSocketController.java", "websocket"),
            // Ядро: таблица профиля при старте; таймер сводки отказов ворот (B6); аудит старта (B6);
            // срок хранения видео — работает при любом состоянии video (B11)
            Map.entry("features/FeatureStartupReporter.java", "core"),
            Map.entry("features/FeatureGateFilter.java", "core"),
            Map.entry("features/journal/StartupProfileAuditor.java", "core"),
            Map.entry("features/data/VideoRetentionJob.java", "core")
    ));

    @Test
    void everySideEffectBelongsToFeatureOrCore() throws IOException {
        List<String> unlisted = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCES.relativize(file).toString().replace('\\', '/');
                if (INVENTORY.containsKey(relative)) continue;
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String code = codeOnly(lines.get(i));
                    for (Pattern pattern : PATTERNS) {
                        if (pattern.matcher(code).find()) {
                            unlisted.add(relative + ":" + (i + 1) + " [" + pattern.pattern() + "] " + lines.get(i).trim());
                        }
                    }
                }
            }
        }
        assertThat(unlisted)
                .as("побочные эффекты вне инвентаризации: отнесите файл к функции (и закройте её потолком) "
                        + "или к ядру в SideEffectInventoryTest.INVENTORY")
                .isEmpty();
    }

    @Test
    void inventoryOwnersAreKnownFeatures() {
        INVENTORY.forEach((file, owner) -> {
            if (!owner.equals("core")) {
                assertThat(Feature.byKey(owner)).as(file + " → " + owner).isPresent();
                assertThat(Feature.byKey(owner).orElseThrow().isAbsent()).as(file).isFalse();
            }
        });
    }

    /** Строка без комментариев: шаблон в Javadoc или после // не считается */
    private static String codeOnly(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return "";
        int comment = line.indexOf("//");
        return comment >= 0 && !line.substring(0, comment).contains("\"") ? line.substring(0, comment) : line;
    }
}
