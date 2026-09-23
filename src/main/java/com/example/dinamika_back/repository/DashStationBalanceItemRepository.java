package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashStationBalanceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashStationBalanceItemRepository extends JpaRepository<DashStationBalanceItem, Long> {

    /** Номенклатура всех станций: по станции, внутри — по возрастанию остатка */
    List<DashStationBalanceItem> findAllByOrderByStationKeyAscQuantityAsc();
}
