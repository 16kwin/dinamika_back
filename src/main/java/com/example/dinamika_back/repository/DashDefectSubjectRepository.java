package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashDefectSubject;
import com.example.dinamika_back.model.DashDefectSubjectId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashDefectSubjectRepository extends JpaRepository<DashDefectSubject, DashDefectSubjectId> {

    /** Субъекты вида графика в порядке вывода на оси X */
    List<DashDefectSubject> findByViewKindOrderBySortOrderAsc(String viewKind);
}
