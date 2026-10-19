package com.example.dinamika_back.model;

import java.io.Serializable;
import java.util.Objects;

/** Ключ позиции станции на плане: план + станция */
public class StationPlanPositionId implements Serializable {

    private Long planId;
    private Long stationId;

    public StationPlanPositionId() {
    }

    public StationPlanPositionId(Long planId, Long stationId) {
        this.planId = planId;
        this.stationId = stationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StationPlanPositionId other)) return false;
        return Objects.equals(planId, other.planId) && Objects.equals(stationId, other.stationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, stationId);
    }
}
