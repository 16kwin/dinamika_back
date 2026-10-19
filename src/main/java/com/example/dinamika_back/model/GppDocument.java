package com.example.dinamika_back.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Документ «График подготовки производства (ГПП по предприятию)» (doc_gpp) — один на предприятие,
 * создаётся миграцией. Строки графика — {@link GppRow}, зависимости строк — {@link GppRowDependency}.
 */
@Entity
@Table(name = "doc_gpp")
@Getter
@Setter
@NoArgsConstructor
public class GppDocument {

    /** «Общий по предприятию»: детали стартуют от даты изделия и ждут предшественников */
    public static final String TYPE_GENERAL = "GENERAL";
    /** «Последовательный»: операция изделия, затем детали одна за другой */
    public static final String TYPE_SEQUENTIAL = "SEQUENTIAL";

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "gpp_type", nullable = false, length = 20)
    private String gppType;

    @Column(name = "auto_calc", nullable = false)
    private Boolean autoCalc;

    /** «Период выборки» — диапазон шкалы диаграммы */
    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    /** Версия документа: растёт при каждом сохранении, устаревшая версия в запросе — 409 */
    @Column(name = "version", nullable = false)
    private Long version;

    /** ФИО (или логин) последнего сохранившего; null — документ ещё не сохраняли */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
