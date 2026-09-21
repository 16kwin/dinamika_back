package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.DashDefectAggDTO;
import com.example.dinamika_back.dto.DashDefectItemDTO;
import com.example.dinamika_back.dto.DashDefectSubjectDTO;
import com.example.dinamika_back.dto.DashDefectViewDTO;
import com.example.dinamika_back.dto.DashProductionPointDTO;
import com.example.dinamika_back.dto.DashQcDTO;
import com.example.dinamika_back.dto.DashQcSumDTO;
import com.example.dinamika_back.dto.DashQualityIndicatorsDTO;
import com.example.dinamika_back.dto.DashQualitySumDTO;
import com.example.dinamika_back.dto.DashReleaseEventDTO;
import com.example.dinamika_back.dto.DashboardQualityResponse;
import com.example.dinamika_back.model.DashDefectItem;
import com.example.dinamika_back.model.DashDefectSubject;
import com.example.dinamika_back.repository.DashDefectDailyRepository;
import com.example.dinamika_back.repository.DashDefectItemRepository;
import com.example.dinamika_back.repository.DashDefectSubjectRepository;
import com.example.dinamika_back.repository.DashProductionDailyRepository;
import com.example.dinamika_back.repository.DashQcDailyRepository;
import com.example.dinamika_back.repository.DashQualityDailyRepository;
import com.example.dinamika_back.repository.DashReleaseEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Панель «Показатели» (топ-менеджмент): расход объема номенклатуры, уровень брака по деталям
 * и по подразделениям, показатели качества, прохождение контроля качества и лента выпуска.
 */
@Service
@RequiredArgsConstructor
public class DashboardQualityService {

    /** Вид графика «Уровень брака» — по деталям */
    public static final String VIEW_PARTS = "part";
    /** Вид графика «Уровень брака» — по подразделениям */
    public static final String VIEW_WORKSHOPS = "workshop";

    /** Сколько последних событий выпуска отдаём в ленту */
    private static final int RELEASE_FEED_SIZE = 20;

    /** Знаков после запятой в долях: фронт округляет до целого, но округление среднего должно совпадать */
    private static final int PERCENT_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DashProductionDailyRepository productionDailyRepository;
    private final DashDefectSubjectRepository defectSubjectRepository;
    private final DashDefectItemRepository defectItemRepository;
    private final DashDefectDailyRepository defectDailyRepository;
    private final DashQualityDailyRepository qualityDailyRepository;
    private final DashQcDailyRepository qcDailyRepository;
    private final DashReleaseEventRepository releaseEventRepository;

    /** Данные всех карточек панели за период [from, to]. */
    public DashboardQualityResponse getQuality(LocalDate from, LocalDate to) {
        List<DashProductionPointDTO> production = productionDailyRepository.findByPeriod(from, to).stream()
                .map(p -> new DashProductionPointDTO(p.getStatDate(), p.getPlanQty(), p.getFactQty()))
                .toList();

        DashDefectViewDTO parts = buildDefectView(VIEW_PARTS, from, to);
        DashDefectViewDTO workshops = buildDefectView(VIEW_WORKSHOPS, from, to);

        // Показатели качества: всего выпуск с производства = годный выпуск + брак
        DashQualitySumDTO qualitySums = qualityDailyRepository.sumForPeriod(from, to);
        int released = toInt(qualitySums != null ? qualitySums.getReleased() : null);
        int defect = toInt(qualitySums != null ? qualitySums.getDefect() : null);
        int total = released + defect;
        DashQualityIndicatorsDTO quality = new DashQualityIndicatorsDTO(
                released, defect, total, percentOf(defect, total));

        // Производство: прошли / ожидают / не прошли контроль качества
        DashQcSumDTO qcSums = qcDailyRepository.sumForPeriod(from, to);
        int passed = toInt(qcSums != null ? qcSums.getPassed() : null);
        int waiting = toInt(qcSums != null ? qcSums.getWaiting() : null);
        int failed = toInt(qcSums != null ? qcSums.getFailed() : null);
        int qcTotal = passed + waiting + failed;
        DashQcDTO qc = new DashQcDTO(passed, waiting, failed, qcTotal,
                percentOf(passed, qcTotal), percentOf(waiting, qcTotal), percentOf(failed, qcTotal));

        List<DashReleaseEventDTO> releases = releaseEventRepository
                .findAllByOrderByEventAtDesc(PageRequest.of(0, RELEASE_FEED_SIZE)).stream()
                .map(e -> new DashReleaseEventDTO(e.getId(), e.getName(), e.getEventAt()))
                .toList();

        return new DashboardQualityResponse(from, to, production, parts, workshops, quality, qc, releases);
    }

    /**
     * Один вид графика «Уровень брака»: столбики по субъектам в порядке справочника,
     * внутри каждого — раскрытие по элементам (строки pop-up) по убыванию количества брака.
     * Средний уровень брака считается по формуле из ТЗ: сумма брака / сумма выпуска × 100.
     */
    private DashDefectViewDTO buildDefectView(String viewKind, LocalDate from, LocalDate to) {
        List<DashDefectSubject> subjects = defectSubjectRepository.findByViewKindOrderBySortOrderAsc(viewKind);
        List<DashDefectItem> items = defectItemRepository.findByViewKindOrderBySortOrderAsc(viewKind);

        Map<String, String> itemNames = new HashMap<>();
        Map<String, Integer> itemOrder = new HashMap<>();
        for (DashDefectItem item : items) {
            itemNames.put(item.getItemKey(), item.getName());
            itemOrder.put(item.getItemKey(), item.getSortOrder());
        }

        // Агрегаты периода по парам субъект + элемент
        Map<String, List<DashDefectAggDTO>> bySubject = new LinkedHashMap<>();
        for (DashDefectAggDTO row : defectDailyRepository.sumBySubjectAndItem(viewKind, from, to)) {
            bySubject.computeIfAbsent(row.getSubjectKey(), key -> new ArrayList<>()).add(row);
        }

        List<DashDefectSubjectDTO> result = new ArrayList<>();
        int totalReleased = 0;
        int totalDefect = 0;

        for (DashDefectSubject subject : subjects) {
            List<DashDefectAggDTO> rows = bySubject.getOrDefault(subject.getSubjectKey(), List.of());

            List<DashDefectItemDTO> itemDtos = new ArrayList<>();
            int subjectReleased = 0;
            int subjectDefect = 0;
            for (DashDefectAggDTO row : rows) {
                int itemReleased = toInt(row.getReleased());
                int itemDefect = toInt(row.getDefect());
                subjectReleased += itemReleased;
                subjectDefect += itemDefect;
                itemDtos.add(new DashDefectItemDTO(
                        row.getItemKey(),
                        itemNames.getOrDefault(row.getItemKey(), row.getItemKey()),
                        itemReleased,
                        itemDefect,
                        percentOf(itemDefect, itemReleased)));
            }

            // Сначала самые «шумные» строки, при равенстве — порядок справочника
            itemDtos.sort(Comparator
                    .comparingInt(DashDefectItemDTO::getDefect).reversed()
                    .thenComparingInt(item -> itemOrder.getOrDefault(item.getKey(), Integer.MAX_VALUE)));

            totalReleased += subjectReleased;
            totalDefect += subjectDefect;

            result.add(new DashDefectSubjectDTO(
                    subject.getSubjectKey(),
                    subject.getName(),
                    subjectReleased,
                    subjectDefect,
                    percentOf(subjectDefect, subjectReleased),
                    itemDtos));
        }

        return new DashDefectViewDTO(totalReleased, totalDefect, percentOf(totalDefect, totalReleased), result);
    }

    /** value / base × 100 с четырьмя знаками; база 0 даёт 0 */
    private static BigDecimal percentOf(int value, int base) {
        if (base == 0) return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(value)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(base), PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Дневные ряды хранятся дробными; наружу выпуск и брак отдаём целыми, как в ТЗ */
    private static int toInt(BigDecimal value) {
        return value == null ? 0 : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
