package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashGraphSupplierLink;
import com.example.dinamika_back.model.DashGraphSupplierLinkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DashGraphSupplierLinkRepository extends JpaRepository<DashGraphSupplierLink, DashGraphSupplierLinkId> {
}
