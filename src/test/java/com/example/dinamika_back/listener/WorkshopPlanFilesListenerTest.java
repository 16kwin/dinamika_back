package com.example.dinamika_back.listener;

import com.example.dinamika_back.model.Workshop;
import com.example.dinamika_back.service.StationPlanStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Удаление цеха: после фиксации удаляется файл его плана (только тот, на который ссылается план этой базы),
 * при откате — ничего; сбой поиска плана не мешает удалению цеха.
 */
class WorkshopPlanFilesListenerTest {

    private static final long WORKSHOP = 7L;

    @TempDir
    Path dir;

    private JdbcTemplate jdbc;
    private StationPlanStorage storage;
    private WorkshopPlanFilesListener listener;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        storage = new StationPlanStorage(dir.toString());
        listener = new WorkshopPlanFilesListener(jdbc, storage);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void deletesPlanFileAndFolderAfterCommit() {
        String image = storage.save(WORKSHOP, new byte[]{1}, "png");
        stubPaths(List.of(image));

        listener.beforeRemove(workshop());
        assertThat(storage.exists(image)).as("до фиксации файл на месте").isTrue();
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(storage.exists(image)).isFalse();
        assertThat(dir.resolve(Long.toString(WORKSHOP))).doesNotExist();
    }

    @Test
    void keepsFilesThePlanDoesNotReference() {
        // Тот же каталог у другой базы: её файл того же цеха не трогаем
        String image = storage.save(WORKSHOP, new byte[]{1}, "png");
        String foreign = storage.save(WORKSHOP, new byte[]{2}, "png");
        stubPaths(List.of(image));

        listener.beforeRemove(workshop());
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(storage.exists(image)).isFalse();
        assertThat(storage.exists(foreign)).isTrue();
    }

    @Test
    void keepsFileWhenDeletionRolledBack() {
        String image = storage.save(WORKSHOP, new byte[]{1}, "png");
        stubPaths(List.of(image));

        listener.beforeRemove(workshop());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(storage.exists(image)).isTrue();
    }

    @Test
    void lookupFailureDoesNotBreakWorkshopDeletion() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(WORKSHOP)))
                .thenThrow(new DataAccessResourceFailureException("база недоступна"));

        assertThatCode(() -> listener.beforeRemove(workshop())).doesNotThrowAnyException();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void doesNothingWithoutTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
        listener.beforeRemove(workshop());
        verify(jdbc, never()).queryForList(anyString(), eq(String.class), eq(WORKSHOP));
        TransactionSynchronizationManager.initSynchronization();
    }

    // ==================== Вспомогательное ====================

    private void stubPaths(List<String> paths) {
        when(jdbc.queryForList(WorkshopPlanFilesListener.IMAGE_PATHS_SQL, String.class, WORKSHOP)).thenReturn(paths);
    }

    private static Workshop workshop() {
        Workshop workshop = new Workshop();
        workshop.setId(WORKSHOP);
        return workshop;
    }

    /** Завершение транзакции: Spring вызывает afterCommit только при фиксации, afterCompletion — всегда */
    private static void complete(int status) {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) sync.afterCommit();
            sync.afterCompletion(status);
        }
    }
}
