package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.DashOperatorEventDTO;
import com.example.dinamika_back.dto.DashOperatorMetricDTO;
import com.example.dinamika_back.dto.DashStationDTO;
import com.example.dinamika_back.dto.DashStationItemDTO;
import com.example.dinamika_back.dto.DashboardOperatorResponse;
import com.example.dinamika_back.model.DashOperatorEvent;
import com.example.dinamika_back.model.DashOperatorMetricDaily;
import com.example.dinamika_back.model.DashStationBalance;
import com.example.dinamika_back.model.DashStationBalanceItem;
import com.example.dinamika_back.repository.DashOperatorEventRepository;
import com.example.dinamika_back.repository.DashOperatorMetricDailyRepository;
import com.example.dinamika_back.repository.DashStationBalanceItemRepository;
import com.example.dinamika_back.repository.DashStationBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Панель «Оператор склада»: показатели станций и СГД, критические и минимальные остатки,
 * ленты заказов на поставку и событий.
 */
@Service
@RequiredArgsConstructor
public class DashboardOperatorService {

    /** Критический остаток: количество не больше критического порога */
    public static final String STATUS_CRITICAL = "critical";
    /** Минимальный остаток: количество выше критического порога, но не больше минимального */
    public static final String STATUS_MINIMAL = "minimal";
    /** Остаток выше обоих порогов */
    public static final String STATUS_NORMAL = "normal";

    private static final String KIND_ORDER = "order";
    private static final String KIND_EVENT = "event";

    private static final int PERCENT_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DashOperatorMetricDailyRepository metricRepository;
    private final DashStationBalanceRepository stationRepository;
    private final DashStationBalanceItemRepository stationItemRepository;
    private final DashOperatorEventRepository eventRepository;

    /** Данные панели оператора на последнюю доступную дату. */
    public DashboardOperatorResponse getOperator() {
        DashOperatorMetricDaily metric = metricRepository.findFirstByOrderByStatDateDesc().orElse(null);

        return new DashboardOperatorResponse(
                metric != null ? metric.getStatDate() : null,
                buildMetrics(metric),
                buildStations(),
                buildEvents(KIND_ORDER),
                buildEvents(KIND_EVENT));
    }

    /**
     * Четыре карточки-показателя. База доли: для ТМЦ в станциях — ёмкость станций,
     * для выдачи и выдачи сверхнормы — ТМЦ в станциях, для деталей — ёмкость СГД.
     */
    private List<DashOperatorMetricDTO> buildMetrics(DashOperatorMetricDaily metric) {
        if (metric == null) return List.of();

        int inStations = orZero(metric.getTmcInStations());
        int capacity = orZero(metric.getTmcCapacity());
        int issued = orZero(metric.getIssuedTmc());
        int overNorm = orZero(metric.getIssuedOverNorm());
        int sgdParts = orZero(metric.getSgdParts());
        int sgdCapacity = orZero(metric.getSgdCapacity());

        return List.of(
                new DashOperatorMetricDTO("tmc_in_stations", "ТМЦ в станциях",
                        inStations, capacity, percentOf(inStations, capacity)),
                new DashOperatorMetricDTO("issued_tmc", "Выдано ТМЦ",
                        issued, inStations, percentOf(issued, inStations)),
                new DashOperatorMetricDTO("issued_over_norm", "Выдано сверхнормы",
                        overNorm, inStations, percentOf(overNorm, inStations)),
                new DashOperatorMetricDTO("sgd_parts", "Детали на СГД",
                        sgdParts, sgdCapacity, percentOf(sgdParts, sgdCapacity)));
    }

    /**
     * Станции с номенклатурой. На график выводится номенклатура с наименьшим остатком —
     * репозиторий уже отдаёт строки по возрастанию остатка внутри станции.
     */
    private List<DashStationDTO> buildStations() {
        Map<String, List<DashStationBalanceItem>> byStation = new LinkedHashMap<>();
        for (DashStationBalanceItem item : stationItemRepository.findAllByOrderByStationKeyAscQuantityAsc()) {
            byStation.computeIfAbsent(item.getStationKey(), key -> new ArrayList<>()).add(item);
        }

        List<DashStationDTO> result = new ArrayList<>();
        for (DashStationBalance station : stationRepository.findAllByOrderBySortOrderAsc()) {
            List<DashStationBalanceItem> items = byStation.getOrDefault(station.getStationKey(), List.of());
            if (items.isEmpty()) {
                result.add(new DashStationDTO(station.getStationKey(), station.getName(),
                        0, null, 0, 0, STATUS_NORMAL, List.of()));
                continue;
            }

            DashStationBalanceItem lowest = items.get(0);
            List<DashStationItemDTO> itemDtos = items.stream()
                    .map(item -> new DashStationItemDTO(
                            item.getId(),
                            item.getNomName(),
                            orZero(item.getQuantity()),
                            orZero(item.getMinLevel()),
                            orZero(item.getCriticalLevel()),
                            statusOf(item)))
                    .toList();

            result.add(new DashStationDTO(
                    station.getStationKey(),
                    station.getName(),
                    orZero(lowest.getQuantity()),
                    lowest.getNomName(),
                    orZero(lowest.getMinLevel()),
                    orZero(lowest.getCriticalLevel()),
                    statusOf(lowest),
                    itemDtos));
        }
        return result;
    }

    private List<DashOperatorEventDTO> buildEvents(String kind) {
        List<DashOperatorEvent> rows = eventRepository.findByKindOrderByEventAtDesc(kind);
        List<DashOperatorEventDTO> result = new ArrayList<>(rows.size());
        for (DashOperatorEvent row : rows) {
            result.add(new DashOperatorEventDTO(row.getId(), row.getTitle(), row.getEventAt(),
                    Boolean.TRUE.equals(row.getDone())));
        }
        return result;
    }

    /** Статус остатка: сначала критический порог, затем минимальный */
    private static String statusOf(DashStationBalanceItem item) {
        int quantity = orZero(item.getQuantity());
        if (quantity <= orZero(item.getCriticalLevel())) return STATUS_CRITICAL;
        if (quantity <= orZero(item.getMinLevel())) return STATUS_MINIMAL;
        return STATUS_NORMAL;
    }

    private static BigDecimal percentOf(int value, int base) {
        if (base == 0) return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(value)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(base), PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
