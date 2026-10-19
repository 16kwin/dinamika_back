package com.example.dinamika_back.listener;

import com.example.dinamika_back.model.Workshop;
import com.example.dinamika_back.service.StationPlanStorage;
import jakarta.persistence.PreRemove;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Удаление цеха (сам цех или каскадом с предприятием): запись плана цеха уходит каскадом в базе, мимо
 * StationPlanService, поэтому картинку плана удаляем здесь — после фиксации транзакции. Удаляется только файл,
 * на который ссылается план в этой базе: чужие файлы в общем каталоге не трогаются. Ошибки только в журнал —
 * удаление цеха важнее.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopPlanFilesListener {

    static final String IMAGE_PATHS_SQL = "SELECT image_path FROM station_plans WHERE workshop_id = ? AND image_path IS NOT NULL";

    private final JdbcTemplate jdbcTemplate;
    private final StationPlanStorage storage;

    @PreRemove
    public void beforeRemove(Workshop workshop) {
        Long workshopId = workshop.getId();
        // Без транзакции Spring некому сообщить о фиксации; файл тогда уберёт чистка сирот
        if (workshopId == null || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        List<String> paths;
        try {
            // JDBC, а не JPA: в обратном вызове Hibernate (идёт каскад удаления) запрос JPA сбросил бы сессию.
            // Соединение то же, что у транзакции, — строка плана ещё на месте
            paths = jdbcTemplate.queryForList(IMAGE_PATHS_SQL, String.class, workshopId);
        } catch (RuntimeException e) {
            log.warn("Не удалось найти план удаляемого цеха {}: {}", workshopId, e.getMessage());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                long removed = paths.stream().filter(storage::delete).count();
                storage.deleteFolderIfEmpty(workshopId);
                if (removed > 0) log.info("Цех {} удалён — удалён и файл его плана", workshopId);
            }
        });
    }
}
