package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ GET /api/dashboard/purchase-graph — «Граф закупок»: справочники, закупки по парам
 * номенклатура × поставщик за период и связи между поставщиками.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardPurchaseGraphResponse {
    private LocalDate from;
    private LocalDate to;
    /** Лимит превышения нормативной цены по умолчанию, %; флаги «свыше лимита» считает фронт */
    private Integer limitPercent;
    /** Группы номенклатуры в порядке справочника */
    private List<DashGraphGroupDTO> groups;
    /** Вся номенклатура графа в порядке справочника (без фильтра по периоду) */
    private List<DashGraphNomenclatureDTO> nomenclature;
    /** Все поставщики в порядке справочника (без фильтра по периоду) */
    private List<DashGraphSupplierDTO> suppliers;
    /** Закупки за период по парам; пары без заказов в периоде не выводятся */
    private List<DashGraphPurchaseDTO> purchases;
    /** Связи между поставщиками (от периода не зависят) */
    private List<DashGraphLinkDTO> links;
}
