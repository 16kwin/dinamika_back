package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.DashControlEventDTO;
import com.example.dinamika_back.dto.DashboardControlEventsResponse;
import com.example.dinamika_back.model.DashControlEvent;
import com.example.dinamika_back.repository.DashControlEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Лента контроля качества для панелей «Контролер» (участок контролёра) и «Главный контролер»
 * (всё предприятие). Дата события не хранится: сегодня − days_ago + время суток; сегодняшние
 * события сдвигаются так, чтобы ни одно не оказалось позже текущего момента.
 */
@Service
@RequiredArgsConstructor
public class DashboardControlEventService {

    /** Лента участка контролёра */
    public static final String SCOPE_SECTION = "section";
    /** Лента всего предприятия (главный контролёр) */
    public static final String SCOPE_ENTERPRISE = "enterprise";

    /** Подразделение контролёра: лента scope=section показывает только его */
    public static final String SECTION_DEPARTMENT = "Цех 1, Участок 2";
    /** Подпись ленты главного контролёра */
    public static final String ENTERPRISE_DEPARTMENT = "Предприятие";

    /** Сколько последних событий отдаём в ленту */
    private static final int FEED_SIZE = 60;
    /** Самое позднее сегодняшнее событие встаёт на столько минут раньше текущего момента */
    private static final long LATEST_GAP_MINUTES = 3;

    private final DashControlEventRepository controlEventRepository;

    /** Лента для scope (section | enterprise, пусто — section), самые свежие события первыми. */
    public DashboardControlEventsResponse getControlEvents(String scope) {
        String resolved = scope == null || scope.isBlank() ? SCOPE_SECTION : scope.trim();
        PageRequest page = PageRequest.of(0, FEED_SIZE);

        List<DashControlEvent> rows;
        String department;
        if (SCOPE_SECTION.equals(resolved)) {
            rows = controlEventRepository.findByDepartmentOrderByDaysAgoAscTimeOfDayDescIdDesc(SECTION_DEPARTMENT, page);
            department = SECTION_DEPARTMENT;
        } else if (SCOPE_ENTERPRISE.equals(resolved)) {
            rows = controlEventRepository.findAllByOrderByDaysAgoAscTimeOfDayDescIdDesc(page);
            department = ENTERPRISE_DEPARTMENT;
        } else {
            throw new IllegalArgumentException(
                    "Неизвестное значение scope: '" + scope + "' (ожидается section или enterprise)");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalTime latestToday = rows.stream()
                .filter(row -> daysAgoOf(row) == 0)
                .map(DashboardControlEventService::timeOf)
                .max(Comparator.naturalOrder())
                .orElse(null);
        List<DashControlEventDTO> items = rows.stream()
                .map(row -> new DashControlEventDTO(
                        row.getId(),
                        row.getTitle(),
                        momentOf(now, latestToday, row),
                        row.getDepartment(),
                        row.getExecutor(),
                        row.getController(),
                        row.getStatus()))
                // После сдвига сегодняшних событий порядок пересчитывается по фактическому моменту
                .sorted(Comparator.comparing(DashControlEventDTO::getAt).reversed())
                .toList();
        return new DashboardControlEventsResponse(resolved, department, items);
    }

    /**
     * Момент события. Прошлые дни: сегодня − days_ago, время суток из строки. Сегодняшние события
     * не могут быть «из будущего» (в 10:00 нельзя показать проверку 17:40), поэтому самое позднее из
     * них ставится на {@link #LATEST_GAP_MINUTES} минут раньше текущего момента, остальные сохраняют
     * интервалы между собой.
     */
    private static LocalDateTime momentOf(LocalDateTime now, LocalTime latestToday, DashControlEvent row) {
        int daysAgo = daysAgoOf(row);
        LocalTime time = timeOf(row);
        if (daysAgo == 0 && latestToday != null) {
            long beforeLatest = Duration.between(time, latestToday).toMinutes();
            return now.minusMinutes(LATEST_GAP_MINUTES + beforeLatest);
        }
        return now.toLocalDate().minusDays(daysAgo).atTime(time);
    }

    private static int daysAgoOf(DashControlEvent row) {
        return row.getDaysAgo() == null ? 0 : row.getDaysAgo();
    }

    private static LocalTime timeOf(DashControlEvent row) {
        return row.getTimeOfDay() == null ? LocalTime.MIDNIGHT : row.getTimeOfDay();
    }
}
