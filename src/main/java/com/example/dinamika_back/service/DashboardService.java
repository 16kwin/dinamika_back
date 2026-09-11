package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.DashBudgetDTO;
import com.example.dinamika_back.dto.DashCostPointDTO;
import com.example.dinamika_back.dto.DashCostsByTypeDTO;
import com.example.dinamika_back.dto.DashCostsDTO;
import com.example.dinamika_back.dto.DashDistributionDTO;
import com.example.dinamika_back.dto.DashDistributionItemDTO;
import com.example.dinamika_back.dto.DashIndicatorsDTO;
import com.example.dinamika_back.dto.DashTypeAmountDTO;
import com.example.dinamika_back.dto.DashTypeDTO;
import com.example.dinamika_back.dto.DashboardEconomicResponse;
import com.example.dinamika_back.dto.DashboardSettingsDTO;
import com.example.dinamika_back.model.DashNomenclatureType;
import com.example.dinamika_back.model.UserDashboardSettings;
import com.example.dinamika_back.repository.DashBudgetDailyRepository;
import com.example.dinamika_back.repository.DashCostDailyRepository;
import com.example.dinamika_back.repository.DashCostTypeDailyRepository;
import com.example.dinamika_back.repository.DashIndicatorDailyRepository;
import com.example.dinamika_back.repository.DashNomenclatureTypeRepository;
import com.example.dinamika_back.repository.UserDashboardSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Панель «Экономический блок»: агрегаты за период и настройки карточек пользователя.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Сколько видов по умолчанию в столбчатой диаграмме — первые по sort_order (1..9) */
    public static final int DEFAULT_BAR_COUNT = 9;

    /** Виды (оси) радара по умолчанию — порядок осей по часовой стрелке, как на макете */
    public static final List<String> DEFAULT_RADAR_TYPES = List.of(
            "frezy_monolitnye", "plastiny_tverdosplavnye", "sverla_tverdosplavnye",
            "frezy_smennye", "reztsy_tokarnye", "osnastka_frezernaya", "metizy");

    // Ограничения на количество выбранных видов
    public static final int BAR_TYPES_MIN = 1;
    public static final int BAR_TYPES_MAX = 9;
    public static final int RADAR_TYPES_MIN = 5;
    public static final int RADAR_TYPES_MAX = 9;

    /** Запас над максимумом радара: максимум среди выбранных видов даёт 100 / 1.1 = 90.9% */
    private static final BigDecimal DISTRIBUTION_HEADROOM = new BigDecimal("1.1");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final DashNomenclatureTypeRepository typeRepository;
    private final DashCostDailyRepository costDailyRepository;
    private final DashCostTypeDailyRepository costTypeDailyRepository;
    private final DashIndicatorDailyRepository indicatorDailyRepository;
    private final DashBudgetDailyRepository budgetDailyRepository;
    private final UserDashboardSettingsRepository settingsRepository;

    // ==================== Данные панели ====================

    /**
     * Данные всех карточек панели за период [from, to] с учётом выбранных пользователем видов.
     */
    public DashboardEconomicResponse getEconomic(LocalDate from, LocalDate to, Integer userId) {
        List<DashNomenclatureType> types = typeRepository.findAllByOrderBySortOrderAsc();
        Map<String, String> names = new LinkedHashMap<>();
        for (DashNomenclatureType type : types) {
            names.put(type.getTypeKey(), type.getName());
        }
        DashboardSettingsDTO settings = resolveSettings(userId, types);

        // График «Затраты на приобретение»: по точке на каждый день диапазона, дни без строк пропускаются
        List<DashCostPointDTO> points = costDailyRepository.findByPeriod(from, to).stream()
                .map(c -> new DashCostPointDTO(c.getCostDate(), c.getPlanAmount(), c.getFactAmount()))
                .toList();

        // Суммы затрат по видам номенклатуры за период
        Map<String, BigDecimal> sumsByType = new HashMap<>();
        for (DashTypeAmountDTO row : costTypeDailyRepository.sumByTypeForPeriod(from, to)) {
            sumsByType.put(row.getKey(), row.getAmount());
        }

        // Столбчатая диаграмма: выбранные виды в порядке списка selected
        List<String> barKeys = settings.getBarTypes();
        List<DashTypeAmountDTO> barItems = barKeys.stream()
                .map(key -> new DashTypeAmountDTO(key, names.get(key), amountOrZero(sumsByType.get(key))))
                .toList();

        // Показатели затрат: закупки / выдача
        DashIndicatorsDTO indicatorSums = indicatorDailyRepository.sumForPeriod(from, to);
        DashIndicatorsDTO indicators = new DashIndicatorsDTO(
                amountOrZero(indicatorSums != null ? indicatorSums.getPurchases() : null),
                amountOrZero(indicatorSums != null ? indicatorSums.getIssue() : null));

        // Исполнение бюджета: факт / план
        DashBudgetDTO budgetSums = budgetDailyRepository.sumForPeriod(from, to);
        BigDecimal plan = amountOrZero(budgetSums != null ? budgetSums.getPlan() : null);
        BigDecimal fact = amountOrZero(budgetSums != null ? budgetSums.getFact() : null);
        DashBudgetDTO budget = new DashBudgetDTO(plan, fact, percentOf(fact, plan, 2));

        // Распределение затрат (радар): процент от максимума среди выбранных видов с запасом 10%
        List<String> radarKeys = settings.getRadarTypes();
        BigDecimal radarMax = radarKeys.stream()
                .map(key -> amountOrZero(sumsByType.get(key)))
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal radarBase = radarMax.multiply(DISTRIBUTION_HEADROOM);
        List<DashDistributionItemDTO> radarItems = radarKeys.stream()
                .map(key -> {
                    BigDecimal amount = amountOrZero(sumsByType.get(key));
                    return new DashDistributionItemDTO(key, names.get(key), amount, percentOf(amount, radarBase, 1));
                })
                .toList();

        List<DashTypeDTO> availableTypes = types.stream()
                .map(type -> new DashTypeDTO(type.getTypeKey(), type.getName()))
                .toList();

        return new DashboardEconomicResponse(
                from, to,
                new DashCostsDTO(points),
                new DashCostsByTypeDTO(barKeys, barItems),
                indicators,
                budget,
                new DashDistributionDTO(radarKeys, radarItems),
                availableTypes);
    }

    // ==================== Настройки ====================

    /** Настройки карточек пользователя (значения по умолчанию, если записи нет) */
    public DashboardSettingsDTO getSettings(Integer userId) {
        return resolveSettings(userId, typeRepository.findAllByOrderBySortOrderAsc());
    }

    /** Сохранить выбранные виды; при невалидных данных — IllegalArgumentException (контроллер отдаёт 400) */
    @Transactional
    public void saveSettings(Integer userId, DashboardSettingsDTO request) {
        if (userId == null) {
            throw new IllegalArgumentException("Параметр userId обязателен");
        }
        if (request == null) {
            throw new IllegalArgumentException("Тело запроса обязательно");
        }
        Set<String> known = new HashSet<>();
        for (DashNomenclatureType type : typeRepository.findAll()) {
            known.add(type.getTypeKey());
        }
        validateKeys("barTypes", request.getBarTypes(), BAR_TYPES_MIN, BAR_TYPES_MAX, known);
        validateKeys("radarTypes", request.getRadarTypes(), RADAR_TYPES_MIN, RADAR_TYPES_MAX, known);

        String barTypesJson = toJson(request.getBarTypes());
        String radarTypesJson = toJson(request.getRadarTypes());

        UserDashboardSettings settings = settingsRepository.findByUserId(userId).orElse(null);
        if (settings == null) {
            settings = UserDashboardSettings.builder()
                    .userId(userId)
                    .barTypesJson(barTypesJson)
                    .radarTypesJson(radarTypesJson)
                    .build();
        } else {
            settings.setBarTypesJson(barTypesJson);
            settings.setRadarTypesJson(radarTypesJson);
            settings.setUpdatedAt(LocalDateTime.now());
        }
        settingsRepository.save(settings);
    }

    /**
     * Настройки пользователя с подстановкой значений по умолчанию.
     * Неизвестные ключи из сохранённых настроек отбрасываются; пустой список заменяется дефолтом.
     */
    private DashboardSettingsDTO resolveSettings(Integer userId, List<DashNomenclatureType> typesBySortOrder) {
        List<String> knownKeys = typesBySortOrder.stream().map(DashNomenclatureType::getTypeKey).toList();
        Set<String> known = new HashSet<>(knownKeys);
        List<String> defaultBars = knownKeys.stream().limit(DEFAULT_BAR_COUNT).toList();
        List<String> defaultRadar = DEFAULT_RADAR_TYPES.stream().filter(known::contains).toList();

        UserDashboardSettings saved = userId == null
                ? null
                : settingsRepository.findByUserId(userId).orElse(null);
        if (saved == null) {
            return new DashboardSettingsDTO(defaultBars, defaultRadar);
        }
        List<String> bars = parseKeys(saved.getBarTypesJson(), known);
        List<String> radar = parseKeys(saved.getRadarTypesJson(), known);
        return new DashboardSettingsDTO(
                bars.isEmpty() ? defaultBars : bars,
                radar.isEmpty() ? defaultRadar : radar);
    }

    /** Проверка списка ключей: количество в [min, max], без повторов, все ключи существуют */
    private static void validateKeys(String field, List<String> keys, int min, int max, Set<String> known) {
        if (keys == null || keys.size() < min || keys.size() > max) {
            throw new IllegalArgumentException(field + ": требуется от " + min + " до " + max + " видов номенклатуры");
        }
        Set<String> seen = new HashSet<>();
        for (String key : keys) {
            if (key == null || !known.contains(key)) {
                throw new IllegalArgumentException(field + ": неизвестный вид номенклатуры '" + key + "'");
            }
            if (!seen.add(key)) {
                throw new IllegalArgumentException(field + ": вид '" + key + "' указан повторно");
            }
        }
    }

    /** JSON-массив строк → список известных ключей без повторов (порядок сохраняется) */
    private static List<String> parseKeys(String json, Set<String> known) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> keys;
        try {
            keys = objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            if (key != null && known.contains(key) && !result.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    private static String toJson(List<String> keys) {
        try {
            return objectMapper.writeValueAsString(keys);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать настройки панели", e);
        }
    }

    /** null → 0.00 */
    private static BigDecimal amountOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** value / base * 100 с заданным числом знаков; при base = 0 → 0 */
    private static BigDecimal percentOf(BigDecimal value, BigDecimal base, int scale) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.UNNECESSARY);
        }
        return value.multiply(HUNDRED).divide(base, scale, RoundingMode.HALF_UP);
    }
}
