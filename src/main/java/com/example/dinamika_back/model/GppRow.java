package com.example.dinamika_back.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Строка ГПП (reg_gpp_rows): изделие, деталь или материал. Дерево — через parentUid,
 * порядок среди братьев — position. У материала нет дат, длительности и детей.
 *
 * <p>uid генерирует клиент, поэтому строка реализует {@link Persistable}: при полной замене строк
 * документа новые записи вставляются сразу, без SELECT на каждую (как было бы при merge).
 */
@Entity
@Table(name = "reg_gpp_rows")
@Getter
@Setter
@NoArgsConstructor
public class GppRow implements Persistable<UUID> {

    public static final String KIND_PRODUCT = "PRODUCT";
    public static final String KIND_PART = "PART";
    public static final String KIND_MATERIAL = "MATERIAL";

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "doc_gpp_uid", nullable = false)
    private UUID docUid;

    @Column(name = "parent_uid")
    private UUID parentUid;

    /** Порядок среди братьев, с 0 */
    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "op_number", length = 10)
    private String opNumber;

    @Column(name = "code", length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "finish_date")
    private LocalDate finishDate;

    /** Длительность в календарных днях (1..3650); у изделия — его собственная операция «000» */
    @Column(name = "duration_days")
    private Integer durationDays;

    /** Цвет полосы диаграммы '#RRGGBB' */
    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "collapsed", nullable = false)
    private Boolean collapsed;

    /** Строка создана в этом запросе и ещё не записана в базу */
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean fresh = true;

    @Override
    public UUID getId() {
        return uid;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        fresh = false;
    }
}
