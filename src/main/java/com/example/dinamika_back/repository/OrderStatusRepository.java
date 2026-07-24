// AWMS — repository/OrderStatusRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusRepository extends JpaRepository<OrderStatus, Long> {
    List<OrderStatus> findByOrderUidOrderByDatetimeDesc(String orderUid);
}