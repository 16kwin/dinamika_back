package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.DashGraphGroupDTO;
import com.example.dinamika_back.dto.DashGraphLinkDTO;
import com.example.dinamika_back.dto.DashGraphNomenclatureDTO;
import com.example.dinamika_back.dto.DashGraphPurchaseDTO;
import com.example.dinamika_back.dto.DashGraphSupplierDTO;
import com.example.dinamika_back.dto.DashboardPurchaseGraphResponse;
import com.example.dinamika_back.model.DashGraphNomenclature;
import com.example.dinamika_back.model.DashGraphPurchase;
import com.example.dinamika_back.model.DashGraphSupplier;
import com.example.dinamika_back.model.DashGraphSupplierLink;
import com.example.dinamika_back.repository.DashGraphGroupRepository;
import com.example.dinamika_back.repository.DashGraphNomenclatureRepository;
import com.example.dinamika_back.repository.DashGraphPurchaseRepository;
import com.example.dinamika_back.repository.DashGraphSupplierLinkRepository;
import com.example.dinamika_back.repository.DashGraphSupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * «Граф закупок» (панель «Служба закупа» и отдельный экран): справочники номенклатуры и поставщиков,
 * закупки за период по парам номенклатура × поставщик и связи аффилированности между поставщиками.
 * Флаги «свыше лимита» сервер не считает: лимит меняется ползунком на фронте.
 */
@Service
@RequiredArgsConstructor
public class DashboardPurchaseGraphService {

    /** Лимит превышения нормативной цены по умолчанию, % */
    public static final int DEFAULT_LIMIT_PERCENT = 20;

    private static final int MONEY_SCALE = 2;

    private final DashGraphGroupRepository groupRepository;
    private final DashGraphNomenclatureRepository nomenclatureRepository;
    private final DashGraphSupplierRepository supplierRepository;
    private final DashGraphPurchaseRepository purchaseRepository;
    private final DashGraphSupplierLinkRepository linkRepository;

    /** Граф закупок за период [from, to] по дате заказа (обе даты включительно). */
    public DashboardPurchaseGraphResponse getPurchaseGraph(LocalDate from, LocalDate to) {
        List<DashGraphGroupDTO> groups = groupRepository.findAllByOrderBySortOrderAsc().stream()
                .map(group -> new DashGraphGroupDTO(group.getGroupKey(), group.getName()))
                .toList();

        List<DashGraphNomenclature> nomenclature = nomenclatureRepository.findAllByOrderBySortOrderAsc();
        List<DashGraphSupplier> suppliers = supplierRepository.findAllByOrderBySortOrderAsc();

        // Порядок справочников — для сортировки пар и связей
        Map<String, Integer> nomOrder = new HashMap<>();
        for (DashGraphNomenclature nom : nomenclature) {
            nomOrder.put(nom.getNomKey(), nom.getSortOrder());
        }
        Map<String, Integer> supplierOrder = new HashMap<>();
        for (DashGraphSupplier supplier : suppliers) {
            supplierOrder.put(supplier.getSupplierKey(), supplier.getSortOrder());
        }

        List<DashGraphNomenclatureDTO> nomenclatureDtos = nomenclature.stream()
                .map(nom -> new DashGraphNomenclatureDTO(
                        nom.getNomKey(),
                        nom.getName(),
                        nom.getGroupKey(),
                        nom.getUnit(),
                        nom.getRefPrice(),
                        Boolean.TRUE.equals(nom.getFavorite())))
                .toList();

        List<DashGraphSupplierDTO> supplierDtos = suppliers.stream()
                .map(supplier -> new DashGraphSupplierDTO(
                        supplier.getSupplierKey(),
                        supplier.getName(),
                        Boolean.TRUE.equals(supplier.getAnchor()),
                        supplier.getInn(),
                        supplier.getCity()))
                .toList();

        return new DashboardPurchaseGraphResponse(
                from,
                to,
                DEFAULT_LIMIT_PERCENT,
                groups,
                nomenclatureDtos,
                supplierDtos,
                buildPurchases(from, to, nomOrder, supplierOrder),
                buildLinks(supplierOrder));
    }

    /**
     * Заказы периода, сведённые по парам номенклатура × поставщик. Заказы приходят по возрастанию
     * времени, поэтому последний встреченный заказ пары — её последний заказ в периоде.
     * Порядок пар — по справочнику номенклатуры, затем поставщиков.
     */
    private List<DashGraphPurchaseDTO> buildPurchases(LocalDate from, LocalDate to,
                                                      Map<String, Integer> nomOrder,
                                                      Map<String, Integer> supplierOrder) {
        Map<PairKey, PairTotals> byPair = new HashMap<>();
        List<DashGraphPurchase> rows = purchaseRepository.findByPeriod(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        for (DashGraphPurchase row : rows) {
            byPair.computeIfAbsent(new PairKey(row.getNomKey(), row.getSupplierKey()), key -> new PairTotals())
                    .add(row);
        }

        Comparator<PairKey> pairOrder = Comparator
                .comparing((PairKey key) -> nomOrder.getOrDefault(key.nomKey(), Integer.MAX_VALUE))
                .thenComparing(key -> supplierOrder.getOrDefault(key.supplierKey(), Integer.MAX_VALUE))
                .thenComparing(PairKey::nomKey)
                .thenComparing(PairKey::supplierKey);

        return byPair.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(pairOrder))
                .map(entry -> {
                    PairKey key = entry.getKey();
                    PairTotals totals = entry.getValue();
                    return new DashGraphPurchaseDTO(
                            key.nomKey(),
                            key.supplierKey(),
                            totals.orders,
                            plainQuantity(totals.qty),
                            totals.amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                            averagePrice(totals.amount, totals.qty),
                            totals.lastAt,
                            totals.lastOrderNo);
                })
                .toList();
    }

    /** Все связи поставщиков: по порядку справочника первого поставщика, затем второго */
    private List<DashGraphLinkDTO> buildLinks(Map<String, Integer> supplierOrder) {
        return linkRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((DashGraphSupplierLink link) ->
                                supplierOrder.getOrDefault(link.getSupplierA(), Integer.MAX_VALUE))
                        .thenComparing(link -> supplierOrder.getOrDefault(link.getSupplierB(), Integer.MAX_VALUE)))
                .map(link -> new DashGraphLinkDTO(
                        link.getSupplierA(), link.getSupplierB(), link.getKind(), link.getReason()))
                .toList();
    }

    /** Средняя цена пары amount / qty с двумя знаками; без количества — 0 */
    private static BigDecimal averagePrice(BigDecimal amount, BigDecimal qty) {
        if (qty.signum() == 0) return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return amount.divide(qty, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Количество без хвостовых нулей: 540.00 → 540, 12.50 → 12.5 */
    private static BigDecimal plainQuantity(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Пара номенклатура × поставщик */
    private record PairKey(String nomKey, String supplierKey) {
    }

    /** Накопитель заказов одной пары за период */
    private static final class PairTotals {
        private int orders;
        private BigDecimal qty = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
        private LocalDateTime lastAt;
        private String lastOrderNo;

        private void add(DashGraphPurchase row) {
            BigDecimal rowQty = orZero(row.getQty());
            orders++;
            qty = qty.add(rowQty);
            amount = amount.add(rowQty.multiply(orZero(row.getPrice())));
            lastAt = row.getPurchaseAt();
            lastOrderNo = row.getOrderNo();
        }
    }
}
