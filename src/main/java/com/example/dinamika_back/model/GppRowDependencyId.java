package com.example.dinamika_back.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Ключ зависимости строк ГПП: строка + её предшественник */
public class GppRowDependencyId implements Serializable {

    private UUID rowUid;
    private UUID dependsOnUid;

    public GppRowDependencyId() {
    }

    public GppRowDependencyId(UUID rowUid, UUID dependsOnUid) {
        this.rowUid = rowUid;
        this.dependsOnUid = dependsOnUid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GppRowDependencyId other)) return false;
        return Objects.equals(rowUid, other.rowUid) && Objects.equals(dependsOnUid, other.dependsOnUid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rowUid, dependsOnUid);
    }
}
