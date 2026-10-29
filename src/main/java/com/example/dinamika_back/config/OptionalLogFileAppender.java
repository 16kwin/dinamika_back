package com.example.dinamika_back.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;

import java.nio.charset.Charset;

/**
 * Общий файл лога, как у Spring Boot без своего logback-spring.xml: пишется, только если задан
 * {@code logging.file.name} или {@code logging.file.path} (Spring Boot передаёт их в {@code LOG_FILE}), с теми же
 * шаблоном, кодировкой, порогом и ротацией ({@code logging.logback.rollingpolicy.*}). Без этих настроек файла нет —
 * тоже как раньше.
 *
 * <p>Нужен потому, что logback-spring.xml (журнал безопасности AWMS.SECURITY) заменяет стандартную настройку
 * Spring Boot целиком, а условий без Janino в logback нет: стандартный file-appender.xml при незаданном файле писал бы
 * в «LOG_FILE_IS_UNDEFINED» или во временный каталог. Здесь файл создаётся при запуске аппендера и только по
 * заданному пути; служба, которой файл лога задают аргументом, пишет его, как до выключателей.
 */
public class OptionalLogFileAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private String file;
    private String pattern;
    private String charset;
    private String threshold;
    private String fileNamePattern;
    private String maxFileSize;
    private String totalSizeCap;
    private String maxHistory;
    private String cleanHistoryOnStart;

    private Level minimum = Level.TRACE;
    private RollingFileAppender<ILoggingEvent> delegate;

    public void setFile(String file) {
        this.file = file;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public void setFileNamePattern(String fileNamePattern) {
        this.fileNamePattern = fileNamePattern;
    }

    public void setMaxFileSize(String maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public void setTotalSizeCap(String totalSizeCap) {
        this.totalSizeCap = totalSizeCap;
    }

    public void setMaxHistory(String maxHistory) {
        this.maxHistory = maxHistory;
    }

    public void setCleanHistoryOnStart(String cleanHistoryOnStart) {
        this.cleanHistoryOnStart = cleanHistoryOnStart;
    }

    /** Пишется ли файл (задан logging.file.name или logging.file.path) */
    public boolean writesFile() {
        return delegate != null;
    }

    @Override
    public void start() {
        if (blank(file)) {
            addInfo("logging.file.name и logging.file.path не заданы: общий файл лога не пишется (как у Spring Boot)");
            super.start();
            return;
        }
        minimum = Level.toLevel(blank(threshold) ? null : threshold.trim(), Level.TRACE);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(getContext());
        encoder.setPattern(blank(pattern) ? "%d %5p [%t] %logger : %m%n" : pattern);
        encoder.setCharset(blank(charset) ? Charset.defaultCharset() : Charset.forName(charset.trim()));
        encoder.start();

        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(getContext());
        appender.setName(getName() + "-file");
        appender.setFile(file.trim());
        appender.setEncoder(encoder);

        SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy = new SizeAndTimeBasedRollingPolicy<>();
        policy.setContext(getContext());
        policy.setParent(appender);
        policy.setFileNamePattern(blank(fileNamePattern) ? file.trim() + ".%d{yyyy-MM-dd}.%i.gz" : fileNamePattern.trim());
        policy.setMaxFileSize(FileSize.valueOf(blank(maxFileSize) ? "10MB" : maxFileSize.trim()));
        policy.setTotalSizeCap(FileSize.valueOf(blank(totalSizeCap) ? "0" : totalSizeCap.trim()));
        policy.setMaxHistory(blank(maxHistory) ? 7 : Integer.parseInt(maxHistory.trim()));
        policy.setCleanHistoryOnStart(!blank(cleanHistoryOnStart) && Boolean.parseBoolean(cleanHistoryOnStart.trim()));
        policy.start();

        appender.setRollingPolicy(policy);
        appender.start();
        delegate = appender;
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        RollingFileAppender<ILoggingEvent> target = delegate;
        if (target == null || !event.getLevel().isGreaterOrEqual(minimum)) return;
        target.doAppend(event);
    }

    @Override
    public void stop() {
        RollingFileAppender<ILoggingEvent> target = delegate;
        delegate = null;
        if (target != null) target.stop();
        super.stop();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
