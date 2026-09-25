package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.DashDayEventDTO;
import com.example.dinamika_back.dto.DashboardDayEventsResponse;
import com.example.dinamika_back.model.DashDayEvent;
import com.example.dinamika_back.repository.DashDayEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * «Экран событий текущего дня» (отдельная вкладка для второго монитора): события одного источника
 * за сегодня в колонках «В работе» и «Завершено». Дата события не хранится: демо-день сдвигается
 * так, чтобы самое позднее событие было чуть раньше текущего момента, — экран не показывает
 * событий «из будущего» в любое время суток.
 */
@Service
@RequiredArgsConstructor
public class DashboardDayEventService {

    /** Источник по умолчанию — склад (лента оператора) */
    public static final String SOURCE_OPERATOR = "operator";

    /** Источники экрана и их названия для заголовка */
    private static final Map<String, String> SOURCE_NAMES = Map.of(
            SOURCE_OPERATOR, "Склад",
            "control", "Контроль качества",
            "release", "Выпуск продукции",
            "overpriced", "Закупки с завышенной ценой");

    /** Самое позднее событие дня встаёт на столько минут раньше текущего момента */
    private static final long LATEST_GAP_MINUTES = 3;

    private final DashDayEventRepository dayEventRepository;

    /** События источника (operator | control | release | overpriced, пусто — operator) за сегодня. */
    public DashboardDayEventsResponse getDayEvents(String source) {
        String resolved = source == null || source.isBlank() ? SOURCE_OPERATOR : source.trim();
        String sourceName = SOURCE_NAMES.get(resolved);
        if (sourceName == null) {
            throw new IllegalArgumentException("Неизвестный источник source: '" + source
                    + "' (ожидается operator, control, release или overpriced)");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        // Репозиторий отдаёт строки по убыванию времени — сдвиг его сохраняет в обеих колонках
        List<DashDayEvent> rows = dayEventRepository.findBySourceOrderByTimeOfDayDescSortOrderAsc(resolved);
        LocalTime latest = rows.stream()
                .map(DashboardDayEventService::timeOf)
                .max(Comparator.naturalOrder())
                .orElse(LocalTime.MIDNIGHT);

        List<DashDayEventDTO> inWork = new ArrayList<>();
        List<DashDayEventDTO> done = new ArrayList<>();
        for (DashDayEvent row : rows) {
            long beforeLatest = Duration.between(timeOf(row), latest).toMinutes();
            LocalDateTime at = now.minusMinutes(LATEST_GAP_MINUTES + beforeLatest);
            // Экран — только за текущий день: ушедшее за полночь не показываем
            if (at.isBefore(startOfDay)) {
                continue;
            }
            DashDayEventDTO dto = new DashDayEventDTO(
                    row.getId(),
                    row.getTitle(),
                    at,
                    row.getPerson(),
                    row.getStatusLabel(),
                    row.getTone());
            if (Boolean.TRUE.equals(row.getDone())) {
                done.add(dto);
            } else {
                inWork.add(dto);
            }
        }
        return new DashboardDayEventsResponse(now.toLocalDate(), resolved, sourceName, inWork, done);
    }

    private static LocalTime timeOf(DashDayEvent row) {
        return row.getTimeOfDay() == null ? LocalTime.MIDNIGHT : row.getTimeOfDay();
    }
}
