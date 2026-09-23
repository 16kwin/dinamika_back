package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ GET /api/dashboard/quality — все карточки панели «Показатели» (топ-менеджмент) за период.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardQualityResponse {
    private LocalDate from;
    private LocalDate to;
    /** График «Расход объема производственной номенклатуры по предприятию» */
    private List<DashProductionPointDTO> production;
    /** «Уровень брака по деталям», вид «По деталям» */
    private DashDefectViewDTO parts;
    /** «Уровень брака по подразделениям», вид «По подразделениям» */
    private DashDefectViewDTO workshops;
    /** Карточки «Показатели качества» и «Средний уровень брака» */
    private DashQualityIndicatorsDTO quality;
    /** Карточка «Производство» */
    private DashQcDTO qc;
    /** Лента «Выпуск продукции» — последние события, не зависят от диапазона */
    private List<DashReleaseEventDTO> releases;
}
