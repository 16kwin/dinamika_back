package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.DashAuditMetricDTO;
import com.example.dinamika_back.dto.DashAuditOverpricedDTO;
import com.example.dinamika_back.dto.DashAuditSumDTO;
import com.example.dinamika_back.dto.DashboardAuditResponse;
import com.example.dinamika_back.model.DashGraphNomenclature;
import com.example.dinamika_back.model.DashGraphPurchase;
import com.example.dinamika_back.model.DashGraphSupplier;
import com.example.dinamika_back.repository.DashAuditDailyRepository;
import com.example.dinamika_back.repository.DashGraphNomenclatureRepository;
import com.example.dinamika_back.repository.DashGraphPurchaseRepository;
import com.example.dinamika_back.repository.DashGraphSupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Панель «Аудитор»: инциденты и выдачи сверх нормы за период относительно числа операций
 * и лента заказов, где цена выше нормативной больше чем на лимит (заказы графа закупок).
 */
@Service
@RequiredArgsConstructor
public class DashboardAuditService {

    /** Лимит превышения нормативной цены для ленты, % — тот же, что по умолчанию на графе закупок */
    public static final int LIMIT_PERCENT = DashboardPurchaseGraphService.DEFAULT_LIMIT_PERCENT;

    /** Сколько заказов отдаём в ленту «Закупки с завышенной ценой» */
    private static final int OVERPRICED_FEED_SIZE = 50;

    /** Множитель нормативной цены: 1 + лимит / 100 = 1.20 */
    private static final BigDecimal LIMIT_FACTOR = BigDecimal.valueOf(100L + LIMIT_PERCENT).movePointLeft(2);

    private static final int PERCENT_SCALE = 1;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DashAuditDailyRepository auditDailyRepository;
    private final DashGraphPurchaseRepository purchaseRepository;
    private final DashGraphNomenclatureRepository nomenclatureRepository;
    private final DashGraphSupplierRepository supplierRepository;

    /** Данные панели аудитора за период [from, to]. */
    public DashboardAuditResponse getAudit(LocalDate from, LocalDate to) {
        DashAuditSumDTO sums = auditDailyRepository.sumForPeriod(from, to);
        int operations = toInt(sums != null ? sums.getOperations() : null);
        int incidents = toInt(sums != null ? sums.getIncidents() : null);
        int overNorm = toInt(sums != null ? sums.getOverNorm() : null);

        return new DashboardAuditResponse(
                from,
                to,
                new DashAuditMetricDTO(incidents, operations, percentOf(incidents, operations)),
                new DashAuditMetricDTO(overNorm, operations, percentOf(overNorm, operations)),
                LIMIT_PERCENT,
                buildOverpriced(from, to));
    }

    /**
     * Заказы периода с ценой выше нормативной × (1 + лимит/100), самые поздние первыми.
     * Названия номенклатуры и поставщиков — из справочников графа закупок.
     */
    private List<DashAuditOverpricedDTO> buildOverpriced(LocalDate from, LocalDate to) {
        List<DashGraphPurchase> rows = purchaseRepository.findOverpriced(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                LIMIT_FACTOR, PageRequest.of(0, OVERPRICED_FEED_SIZE));
        if (rows.isEmpty()) return List.of();

        Map<String, DashGraphNomenclature> nomenclature = new HashMap<>();
        for (DashGraphNomenclature nom : nomenclatureRepository.findAll()) {
            nomenclature.put(nom.getNomKey(), nom);
        }
        Map<String, String> supplierNames = new HashMap<>();
        for (DashGraphSupplier supplier : supplierRepository.findAll()) {
            supplierNames.put(supplier.getSupplierKey(), supplier.getName());
        }

        List<DashAuditOverpricedDTO> result = new ArrayList<>(rows.size());
        for (DashGraphPurchase row : rows) {
            DashGraphNomenclature nom = nomenclature.get(row.getNomKey());
            BigDecimal refPrice = nom != null ? nom.getRefPrice() : null;
            BigDecimal price = orZero(row.getPrice());
            BigDecimal qty = orZero(row.getQty());
            result.add(new DashAuditOverpricedDTO(
                    row.getId(),
                    row.getOrderNo(),
                    row.getPurchaseAt(),
                    row.getNomKey(),
                    nom != null ? nom.getName() : row.getNomKey(),
                    row.getSupplierKey(),
                    supplierNames.getOrDefault(row.getSupplierKey(), row.getSupplierKey()),
                    price,
                    refPrice,
                    overPercent(price, refPrice),
                    plainQuantity(qty),
                    qty.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
        }
        return result;
    }

    /** Превышение (price / refPrice − 1) × 100 с одним знаком; без нормативной цены — 0 */
    private static BigDecimal overPercent(BigDecimal price, BigDecimal refPrice) {
        if (refPrice == null || refPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        }
        return price.multiply(HUNDRED)
                .divide(refPrice, PERCENT_SCALE, RoundingMode.HALF_UP)
                .subtract(HUNDRED);
    }

    /** value / base × 100 с одним знаком; база 0 даёт 0 */
    private static BigDecimal percentOf(int value, int base) {
        if (base == 0) return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(value)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(base), PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Количество без хвостовых нулей: 6.00 → 6, 12.50 → 12.5 */
    private static BigDecimal plainQuantity(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }
}
