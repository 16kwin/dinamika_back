package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.StationPlanDTO;
import com.example.dinamika_back.dto.StationPlanPositionDTO;
import com.example.dinamika_back.dto.StationPlanPositionsRequest;
import com.example.dinamika_back.model.StationPlan;
import com.example.dinamika_back.model.StationPlanPosition;
import com.example.dinamika_back.repository.StationPlanPositionRepository;
import com.example.dinamika_back.repository.StationPlanRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Карта станций: проверка расстановки из запроса (координаты 0..1, uid без повторов), сохранение расстановки
 * (версия, пропуск станций не из цеха, пакетная вставка), адрес картинки и чистка файлов-сирот.
 */
class StationPlanServiceTest {

    private static final long WORKSHOP = 1L;
    private static final long PLAN_ID = 10L;

    @TempDir
    Path dir;

    private StationPlanRepository plans;
    private StationPlanPositionRepository positions;
    private StationPlanStorage storage;
    private Session session;
    private StationPlanService service;
    private StationPlan plan;

    @BeforeEach
    void setUp() {
        plans = mock(StationPlanRepository.class);
        positions = mock(StationPlanPositionRepository.class);
        storage = new StationPlanStorage(dir.toString());
        service = new StationPlanService(plans, positions, storage);
        EntityManager entityManager = mock(EntityManager.class);
        session = mock(Session.class);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        plan = new StationPlan();
        plan.setId(PLAN_ID);
        plan.setWorkshopId(WORKSHOP);
        plan.setVersion(5L);
        plan.setImagePath(storage.save(WORKSHOP, new byte[]{1, 2, 3}, "png"));
        plan.setImageMime("image/png");
        plan.setImageWidth(800);
        plan.setImageHeight(600);
        when(plans.workshopExists(WORKSHOP)).thenReturn(true);
        when(plans.findByWorkshopId(WORKSHOP)).thenReturn(Optional.of(plan));
        when(plans.lockByWorkshopId(WORKSHOP)).thenReturn(Optional.of(plan));
        when(plans.saveAndFlush(any(StationPlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(positions.saveAllAndFlush(ArgumentMatchers.<StationPlanPosition>anyIterable())).thenAnswer(inv -> list(inv.getArgument(0)));
        when(positions.findRows(anyLong(), anyLong())).thenReturn(List.of());
        when(positions.findStations(anyCollection())).thenReturn(List.of(
                station(101L, "ST-1", WORKSHOP),
                station(102L, "ST-MOVED", 2L)));
    }

    // ==================== Проверка запроса ====================

    @Test
    void acceptsFractionsIncludingEdges() {
        Map<String, double[]> result = StationPlanService.validatePositions(List.of(
                new StationPlanPositionDTO("ST-1", 0.0, 1.0),
                new StationPlanPositionDTO(" ST-2 ", 0.25, 0.75)));
        assertThat(result).containsOnlyKeys("ST-1", "ST-2");
        assertThat(result.get("ST-2")).containsExactly(0.25, 0.75);
    }

    @Test
    void emptyListMeansNoPositions() {
        assertThat(StationPlanService.validatePositions(null)).isEmpty();
        assertThat(StationPlanService.validatePositions(List.of())).isEmpty();
    }

    @Test
    void rejectsCoordinatesOutsidePlan() {
        for (double bad : new double[]{-0.0001, 1.0001, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> StationPlanService.validatePositions(List.of(new StationPlanPositionDTO("ST-1", bad, 0.5))))
                    .isInstanceOf(StationPlanException.class)
                    .hasMessageContaining("от 0 до 1");
        }
        assertThatThrownBy(() -> StationPlanService.validatePositions(List.of(new StationPlanPositionDTO("ST-1", 0.5, null))))
                .isInstanceOf(StationPlanException.class);
    }

    @Test
    void rejectsDuplicateAndBlankStations() {
        assertThatThrownBy(() -> StationPlanService.validatePositions(List.of(
                new StationPlanPositionDTO("ST-1", 0.1, 0.1),
                new StationPlanPositionDTO("ST-1", 0.2, 0.2))))
                .isInstanceOf(StationPlanException.class)
                .hasMessageContaining("дважды");
        assertThatThrownBy(() -> StationPlanService.validatePositions(List.of(new StationPlanPositionDTO("  ", 0.1, 0.1))))
                .isInstanceOf(StationPlanException.class);
        assertThatThrownBy(() -> StationPlanService.validatePositions(Arrays.asList((StationPlanPositionDTO) null)))
                .isInstanceOf(StationPlanException.class);
    }

    @Test
    void fractionCheck() {
        assertThat(StationPlanService.isFraction(0.0)).isTrue();
        assertThat(StationPlanService.isFraction(1.0)).isTrue();
        assertThat(StationPlanService.isFraction(null)).isFalse();
        assertThat(StationPlanService.isFraction(-1e-9)).isFalse();
    }

    // ==================== Сохранение расстановки ====================

    @Test
    void skipsStationsDeletedOrMovedToAnotherWorkshop() {
        StationPlanDTO result = service.savePositions(WORKSHOP, request(5L,
                new StationPlanPositionDTO("ST-1", 0.1, 0.2),
                new StationPlanPositionDTO("ST-MOVED", 0.3, 0.4),
                new StationPlanPositionDTO("ST-DELETED", 0.5, 0.6)), "director_test");

        List<StationPlanPosition> saved = savedRows();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStationId()).isEqualTo(101L);
        assertThat(saved.get(0).getPlanId()).isEqualTo(PLAN_ID);
        assertThat(saved.get(0).getX()).isEqualTo(0.1);
        assertThat(result.getSkippedStationUids()).containsExactly("ST-MOVED", "ST-DELETED");
        assertThat(result.getVersion()).isEqualTo(6L);
    }

    @Test
    void savesAllWhenNothingSkipped() {
        StationPlanDTO result = service.savePositions(WORKSHOP, request(5L, new StationPlanPositionDTO("ST-1", 0.1, 0.2)), "director_test");
        assertThat(savedRows()).hasSize(1);
        assertThat(result.getSkippedStationUids()).isEmpty();
    }

    @Test
    void staleVersionIsConflictAndNothingChanges() {
        assertThatThrownBy(() -> service.savePositions(WORKSHOP, request(4L, new StationPlanPositionDTO("ST-1", 0.1, 0.2)), "director_test"))
                .isInstanceOfSatisfying(StationPlanException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(positions, never()).deleteByPlan(anyLong());
        verify(positions, never()).saveAllAndFlush(ArgumentMatchers.<StationPlanPosition>anyIterable());
        assertThat(plan.getVersion()).isEqualTo(5L);
    }

    @Test
    void insertsNewRowsInBatchesWithoutMerge() {
        service.savePositions(WORKSHOP, request(5L, new StationPlanPositionDTO("ST-1", 0.1, 0.2)), "director_test");

        InOrder order = inOrder(positions, session);
        order.verify(positions).deleteByPlan(PLAN_ID);
        order.verify(session).setJdbcBatchSize(StationPlanService.INSERT_BATCH);
        order.verify(positions).saveAllAndFlush(ArgumentMatchers.<StationPlanPosition>anyIterable());
        // isNew() = true: Spring Data вызовет persist, а не merge с SELECT по ключу
        assertThat(savedRows()).allSatisfy(row -> assertThat(row.isNew()).isTrue());
    }

    // ==================== Картинка ====================

    @Test
    void imageUrlChangesOnlyWithImageFile() {
        String before = service.get(WORKSHOP).getImage().getUrl();
        assertThat(before).endsWith("/image?v=" + StationPlanStorage.tag(plan.getImagePath()));

        StationPlanDTO saved = service.savePositions(WORKSHOP, request(5L, new StationPlanPositionDTO("ST-1", 0.1, 0.2)), "director_test");
        assertThat(saved.getVersion()).isEqualTo(6L);
        assertThat(saved.getImage().getUrl()).isEqualTo(before);

        plan.setImagePath(storage.save(WORKSHOP, new byte[]{4, 5, 6}, "png"));
        assertThat(service.get(WORKSHOP).getImage().getUrl()).isNotEqualTo(before);
    }

    @Test
    void imageFileCarriesTagForEtag() {
        StationPlanService.ImageFile file = service.image(WORKSHOP);
        assertThat(file.tag()).isEqualTo(StationPlanStorage.tag(plan.getImagePath())).matches("[0-9a-f-]{36}");
        assertThat(file.path()).isEqualTo(storage.resolve(plan.getImagePath()));
    }

    // ==================== Файлы-сироты ====================

    @Test
    void removesOnlyOldUnreferencedPlanFiles() throws Exception {
        String current = plan.getImagePath();
        String replaced = old(storage.save(WORKSHOP, new byte[]{1}, "png"));
        String uploading = storage.save(WORKSHOP, new byte[]{2}, "webp");
        String deletedWorkshop = old(storage.save(2, new byte[]{3}, "jpg"));
        Path foreign = Files.writeString(dir.resolve("1").resolve("readme.txt"), "не план");
        Files.setLastModifiedTime(foreign, FileTime.from(Instant.now().minus(Duration.ofDays(10))));
        old(current);
        when(plans.findImagePaths()).thenReturn(List.of(current));
        when(plans.workshopExists(2L)).thenReturn(false);

        int removed = service.removeOrphanFiles(Instant.now().minus(StationPlanService.ORPHAN_MIN_AGE));

        assertThat(removed).isEqualTo(2);
        assertThat(storage.exists(current)).isTrue();
        assertThat(storage.exists(uploading)).as("свежий файл может принадлежать идущей загрузке").isTrue();
        assertThat(storage.exists(replaced)).isFalse();
        assertThat(storage.exists(deletedWorkshop)).isFalse();
        assertThat(foreign).exists();
        assertThat(dir.resolve("2")).as("каталог удалённого цеха").doesNotExist();
        assertThat(dir.resolve("1")).exists();
    }

    @Test
    void keepsFilesWhenDatabaseReferencesNoFiles() throws Exception {
        // Новая или другая база с тем же каталогом: цеха в ней есть (сиды V1), картинок планов нет
        String other = old(plan.getImagePath());
        String orphan = old(storage.save(3, new byte[]{1}, "png"));
        when(plans.findImagePaths()).thenReturn(List.of());
        when(plans.workshopExists(anyLong())).thenReturn(true);

        assertThat(service.removeOrphanFiles(Instant.now())).isZero();
        assertThat(storage.exists(other)).isTrue();
        assertThat(storage.exists(orphan)).isTrue();
    }

    @Test
    void keepsFilesWhenReferencedFilesAreMissing() throws Exception {
        // База ссылается на файл, которого нет: каталог не её (или сервер запущен не из того каталога)
        String present = old(plan.getImagePath());
        String orphan = old(storage.save(3, new byte[]{1}, "png"));
        when(plans.findImagePaths()).thenReturn(List.of(present, WORKSHOP + "/00000000-0000-0000-0000-000000000000.png"));

        assertThat(service.removeOrphanFiles(Instant.now())).isZero();
        assertThat(storage.exists(orphan)).isTrue();
        assertThat(dir.resolve("3")).exists();
    }

    @Test
    void startupCleanupIsOffByDefault() throws Exception {
        String orphan = old(storage.save(3, new byte[]{1}, "png"));
        when(plans.findImagePaths()).thenReturn(List.of(plan.getImagePath()));

        service.cleanupOnStartup();

        verify(plans, never()).findImagePaths();
        assertThat(storage.exists(orphan)).isTrue();
    }

    @Test
    void startupCleanupRemovesOrphansWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(service, "orphanCleanup", true);
        String orphan = old(storage.save(WORKSHOP, new byte[]{1}, "png"));
        when(plans.findImagePaths()).thenReturn(List.of(plan.getImagePath()));

        service.cleanupOnStartup();

        assertThat(storage.exists(orphan)).isFalse();
        assertThat(storage.exists(plan.getImagePath())).isTrue();
    }

    @Test
    void startupCleanupNeverBreaksStartup() {
        ReflectionTestUtils.setField(service, "orphanCleanup", true);
        when(plans.findImagePaths()).thenThrow(new IllegalStateException("база недоступна"));
        assertThatCode(service::cleanupOnStartup).doesNotThrowAnyException();
    }

    // ==================== Вспомогательное ====================

    private String old(String relative) throws Exception {
        Files.setLastModifiedTime(storage.resolve(relative), FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        return relative;
    }

    private List<StationPlanPosition> savedRows() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<StationPlanPosition>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(positions).saveAllAndFlush(captor.capture());
        return list(captor.getValue());
    }

    private static StationPlanPositionsRequest request(long version, StationPlanPositionDTO... items) {
        StationPlanPositionsRequest request = new StationPlanPositionsRequest();
        request.setVersion(version);
        request.setPositions(List.of(items));
        return request;
    }

    private static StationPlanPositionRepository.StationRef station(long id, String uid, long workshopId) {
        return new StationPlanPositionRepository.StationRef() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getUid() {
                return uid;
            }

            @Override
            public Long getWorkshopId() {
                return workshopId;
            }
        };
    }

    private static <T> List<T> list(Iterable<T> items) {
        List<T> result = new ArrayList<>();
        items.forEach(result::add);
        return result;
    }
}
