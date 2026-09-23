package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashStationBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashStationBalanceRepository extends JpaRepository<DashStationBalance, String> {

    /** Станции в порядке вывода на оси X */
    List<DashStationBalance> findAllByOrderBySortOrderAsc();
}
