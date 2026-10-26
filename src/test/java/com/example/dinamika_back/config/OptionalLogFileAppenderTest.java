package com.example.dinamika_back.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Общий файл лога — как у Spring Boot без своего logback-spring.xml: пишется только по заданному пути
 * (logging.file.name / logging.file.path → LOG_FILE), с порогом; без пути файла нет и ошибок настройки нет.
 */
class OptionalLogFileAppenderTest {

    @Test
    void withoutLogFileNothingIsWritten(@TempDir Path dir) {
        LoggerContext context = context();
        OptionalLogFileAppender appender = appender(context, "");
        Logger logger = context.getLogger("test");
        logger.addAppender(appender);
        logger.info("строка");

        assertThat(appender.isStarted()).isTrue();
        assertThat(appender.writesFile()).isFalse();
        assertThat(dir).isEmptyDirectory();
        context.stop();
    }

    @Test
    void withLogFileWritesLikeSpringBootWithThreshold(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("awms.log");
        LoggerContext context = context();
        OptionalLogFileAppender appender = appender(context, file.toString());
        appender.setThreshold("INFO");
        appender.start();
        Logger logger = context.getLogger("test");
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        logger.debug("ниже порога");
        logger.info("запуск службы");
        assertThat(appender.writesFile()).isTrue();
        context.stop();

        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(text).contains("INFO").contains("запуск службы").doesNotContain("ниже порога");
    }

    /** Свой контекст logback, как у службы (адаптер MDC нужен logback 1.4 при записи события) */
    private static LoggerContext context() {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        return context;
    }

    private static OptionalLogFileAppender appender(LoggerContext context, String file) {
        OptionalLogFileAppender appender = new OptionalLogFileAppender();
        appender.setContext(context);
        appender.setName("FILE");
        appender.setFile(file);
        appender.setPattern("%d %5p [%t] %logger : %m%n");
        appender.setCharset("UTF-8");
        appender.setMaxFileSize("10MB");
        appender.setMaxHistory("7");
        appender.setTotalSizeCap("0");
        appender.setCleanHistoryOnStart("false");
        if (file.isEmpty()) appender.start();
        return appender;
    }
}
