package com.example.dinamika_back.service;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Каталоги файловых хранилищ из настроек. Относительный путь — от рабочего каталога процесса (как uploads/
 * и video-archive), поэтому при старте в журнал пишется абсолютный путь: если сервер запустили из другого
 * каталога, это видно сразу, а не по ошибкам «файл не найден». В проде лучше задавать абсолютные пути.
 */
@Slf4j
public final class StorageDirs {

    private StorageDirs() {
    }

    /** Абсолютный путь каталога; what — что в нём хранится, для журнала */
    public static Path resolve(String configured, String what) {
        String value = configured == null ? "" : configured.trim();
        Path path = Paths.get(value);
        Path absolute = path.toAbsolutePath().normalize();
        if (path.isAbsolute()) {
            log.info("Каталог {}: {}", what, absolute);
        } else {
            log.info("Каталог {}: {} (относительный путь '{}' — от рабочего каталога процесса)", what, absolute, value);
        }
        return absolute;
    }
}
