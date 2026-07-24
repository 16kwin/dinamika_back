// AWMS — repository/OrderTrackingRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.OrderTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderTrackingRepository extends JpaRepository<OrderTracking, Long> {
    List<OrderTracking> findByOrderUidOrderByDatetimeDesc(String orderUid);
}