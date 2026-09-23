package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashDefectItem;
import com.example.dinamika_back.model.DashDefectItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashDefectItemRepository extends JpaRepository<DashDefectItem, DashDefectItemId> {

    /** Элементы раскрытия вида графика в порядке справочника */
    List<DashDefectItem> findByViewKindOrderBySortOrderAsc(String viewKind);
}
