package com.example.dinamika_back.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Зависимость строк ГПП «окончание → начало» (reg_gpp_row_dependencies): строка rowUid начинается
 * после окончания dependsOnUid. position — порядок предшественников строки.
 */
@Entity
@Table(name = "reg_gpp_row_dependencies")
@IdClass(GppRowDependencyId.class)
@Getter
@Setter
@NoArgsConstructor
public class GppRowDependency implements Persistable<GppRowDependencyId> {

    @Id
    @Column(name = "row_uid", nullable = false)
    private UUID rowUid;

    @Id
    @Column(name = "depends_on_uid", nullable = false)
    private UUID dependsOnUid;

    @Column(name = "position", nullable = false)
    private Integer position;

    /** Зависимость создана в этом запросе и ещё не записана в базу */
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean fresh = true;

    public GppRowDependency(UUID rowUid, UUID dependsOnUid, int position) {
        this.rowUid = rowUid;
        this.dependsOnUid = dependsOnUid;
        this.position = position;
    }

    @Override
    public GppRowDependencyId getId() {
        return new GppRowDependencyId(rowUid, dependsOnUid);
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
