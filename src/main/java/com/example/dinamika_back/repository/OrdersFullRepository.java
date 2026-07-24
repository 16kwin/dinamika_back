// AWMS — repository/OrdersFullRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.OrdersFull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrdersFullRepository extends JpaRepository<OrdersFull, String> {
    Optional<OrdersFull> findByOrderUid(String orderUid);
}