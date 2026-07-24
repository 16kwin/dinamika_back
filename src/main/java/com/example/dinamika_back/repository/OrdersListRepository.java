// AWMS — repository/OrdersListRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.OrdersList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdersListRepository extends JpaRepository<OrdersList, String> {
    List<OrdersList> findByStatusIn(List<String> statuses);
    List<OrdersList> findByStatus(String status);
}