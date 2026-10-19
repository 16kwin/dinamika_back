package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.GppDocumentDTO;
import com.example.dinamika_back.dto.GppRowDTO;
import com.example.dinamika_back.dto.GppSaveRequest;
import com.example.dinamika_back.model.GppDocument;
import com.example.dinamika_back.model.GppRow;
import com.example.dinamika_back.model.GppRowDependency;
import com.example.dinamika_back.model.User;
import com.example.dinamika_back.repository.GppDocumentRepository;
import com.example.dinamika_back.repository.GppRowDependencyRepository;
import com.example.dinamika_back.repository.GppRowRepository;
import com.example.dinamika_back.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Документ ГПП: проверка запроса сохранения (дерево, виды элементов, даты, зависимости, циклы),
 * версия документа и полная замена строк.
 */
class GppServiceTest {

    // Изделие 1 с референса: изделие → детали 001…003 (по цепочке) → материалы
    private final String product = uid();
    private final String part1 = uid();
    private final String mat1 = uid();
    private final String mat2 = uid();
    private final String part2 = uid();
    private final String mat3 = uid();
    private final String part3 = uid();
    private final String mat4 = uid();

    private GppDocumentRepository documents;
    private GppRowRepository rows;
    private GppRowDependencyRepository dependencies;
    private UserRepository users;
    private GppService service;
    private Session session;
    private GppDocument doc;

    @BeforeEach
    void setUp() {
        documents = mock(GppDocumentRepository.class);
        rows = mock(GppRowRepository.class);
        dependencies = mock(GppRowDependencyRepository.class);
        users = mock(UserRepository.class);
        service = new GppService(documents, rows, dependencies, users);
        EntityManager entityManager = mock(EntityManager.class);
        session = mock(Session.class);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        doc = new GppDocument();
        doc.setUid(UUID.randomUUID());
        doc.setTitle("График подготовки производства (ГПП по предприятию)");
        doc.setGppType("GENERAL");
        doc.setAutoCalc(true);
        doc.setPeriodFrom(LocalDate.of(2027, 1, 1));
        doc.setPeriodTo(LocalDate.of(2027, 7, 31));
        doc.setVersion(3L);
        doc.setCreatedAt(LocalDateTime.now());
        when(documents.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(doc));
        when(documents.findFirstForUpdateByOrderByCreatedAtAsc()).thenReturn(Optional.of(doc));
        when(documents.saveAndFlush(any(GppDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rows.saveAllAndFlush(ArgumentMatchers.<GppRow>anyIterable())).thenAnswer(inv -> list(inv.getArgument(0)));
        when(dependencies.saveAllAndFlush(ArgumentMatchers.<GppRowDependency>anyIterable())).thenAnswer(inv -> list(inv.getArgument(0)));
        when(users.findTop1ByUsername("director_test")).thenReturn(Optional.of(user("director_test", "Петров", "Пётр", "Петрович")));
    }

    // ==================== Сохранение ====================

    @Test
    void savesValidTreeParentsFirstAndBumpsVersion() {
        List<GppRowDTO> source = sampleRows();
        Collections.reverse(source);
        // Позиции с пропусками: сервер перенумерует их подряд
        source.forEach(row -> row.setPosition(row.getPosition() * 10));

        GppDocumentDTO result = service.save(request(3L, source), "director_test");

        InOrder order = inOrder(rows, dependencies, documents, session);
        // Очередь на блокировку ограничена по времени; вставка — пакетами JDBC
        order.verify(documents).setLockTimeout(GppService.LOCK_TIMEOUT);
        order.verify(documents).findFirstForUpdateByOrderByCreatedAtAsc();
        order.verify(rows).deleteByDoc(doc.getUid());
        order.verify(session).setJdbcBatchSize(GppService.INSERT_BATCH);
        order.verify(rows).saveAllAndFlush(ArgumentMatchers.<GppRow>anyIterable());
        order.verify(dependencies).saveAllAndFlush(ArgumentMatchers.<GppRowDependency>anyIterable());
        order.verify(documents).saveAndFlush(doc);

        assertThat(result.getVersion()).isEqualTo(4L);
        assertThat(result.isCanEdit()).isTrue();
        assertThat(result.getUpdatedBy()).isEqualTo("Петров Пётр Петрович");
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getGppType()).isEqualTo("GENERAL");
        assertThat(result.getPeriodFrom()).isEqualTo("2027-01-01");
        assertThat(result.getRows()).extracting(GppRowDTO::getUid)
                .containsExactly(product, part1, mat1, mat2, part2, mat3, part3, mat4);
        assertThat(result.getRows()).extracting(GppRowDTO::getPosition)
                .containsExactly(0, 0, 0, 1, 1, 0, 2, 0);
        GppRowDTO second = result.getRows().get(4);
        assertThat(second.getDependsOn()).containsExactly(part1);
        assertThat(second.getStart()).isEqualTo("2027-02-03");
        assertThat(second.getQuantity()).isEqualByComparingTo("200");
        assertThat(second.getQuantity().toPlainString()).isEqualTo("200");
        assertThat(second.getCollapsed()).isTrue();
        assertThat(result.getRows().get(2).getStart()).isNull();
        assertThat(doc.getVersion()).isEqualTo(4L);
    }

    @Test
    void staleVersionIsConflict() {
        assertThatThrownBy(() -> service.save(request(2L, sampleRows()), "director_test"))
                .isInstanceOf(GppException.class)
                .satisfies(status(HttpStatus.CONFLICT))
                .hasMessageContaining("другой пользователь");
        verify(rows, never()).deleteByDoc(any());
        verify(rows, never()).saveAllAndFlush(ArgumentMatchers.<GppRow>anyIterable());
        assertThat(doc.getVersion()).isEqualTo(3L);
    }

    @Test
    void invalidRequestDoesNotTouchDocument() {
        List<GppRowDTO> source = sampleRows();
        source.get(1).setParentUid(null);
        assertThatThrownBy(() -> service.save(request(3L, source), "director_test"))
                .isInstanceOf(GppException.class)
                .satisfies(status(HttpStatus.BAD_REQUEST));
        verify(documents, never()).setLockTimeout(any());
        verify(documents, never()).findFirstForUpdateByOrderByCreatedAtAsc();
        verify(rows, never()).deleteByDoc(any());
    }

    @Test
    void emptyDocumentIsAllowed() {
        GppDocumentDTO result = service.save(request(3L, null), "someone");
        assertThat(result.getRows()).isEmpty();
        assertThat(result.getUpdatedBy()).isEqualTo("someone");
    }

    // ==================== Чтение ====================

    @Test
    void getReturnsTreeOrderWithDependencies() {
        UUID p = UUID.fromString(product);
        UUID a = UUID.fromString(part1);
        UUID b = UUID.fromString(part2);
        UUID m = UUID.fromString(mat1);
        List<GppRow> stored = new ArrayList<>(List.of(
                stored(m, a, 0, GppRow.KIND_MATERIAL, "140.000"),
                stored(b, p, 1, GppRow.KIND_PART, "200.000"),
                stored(a, p, 0, GppRow.KIND_PART, "100.500"),
                stored(p, null, 0, GppRow.KIND_PRODUCT, "100.000")));
        when(rows.findByDocUid(doc.getUid())).thenReturn(stored);
        when(dependencies.findByDoc(doc.getUid())).thenReturn(List.of(new GppRowDependency(b, a, 0)));

        GppDocumentDTO result = service.get(false);

        assertThat(result.isCanEdit()).isFalse();
        assertThat(result.getVersion()).isEqualTo(3L);
        assertThat(result.getUpdatedAt()).isNull();
        assertThat(result.getRows()).extracting(GppRowDTO::getUid).containsExactly(product, part1, mat1, part2);
        assertThat(result.getRows().get(0).getQuantity().toPlainString()).isEqualTo("100");
        assertThat(result.getRows().get(1).getQuantity().toPlainString()).isEqualTo("100.5");
        assertThat(result.getRows().get(3).getDependsOn()).containsExactly(part1);
        assertThat(result.getRows().get(0).getDependsOn()).isEmpty();
    }

    @Test
    void missingDocumentIsNotFound() {
        when(documents.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(true))
                .isInstanceOf(GppException.class)
                .satisfies(status(HttpStatus.NOT_FOUND));
    }

    @Test
    void getReadsOneSnapshot() throws NoSuchMethodException {
        // Шапка, строки и зависимости — три запроса; при READ COMMITTED чужое сохранение между ними смешало бы версии
        Transactional tx = GppService.class.getMethod("get", boolean.class).getAnnotation(Transactional.class);
        assertThat(tx.readOnly()).isTrue();
        assertThat(tx.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    // ==================== Проверка запроса ====================

    @Test
    void validateNormalizesValues() {
        List<GppRowDTO> source = sampleRows();
        source.get(0).setColor("#bfe3ff");
        source.get(0).setQuantity(new BigDecimal("100.000"));
        source.get(2).setUnit("  ");
        source.get(4).setDependsOn(List.of(part1, part1));

        GppService.Plan plan = GppService.validate(request(1L, source));

        GppRow first = plan.rows().get(0);
        assertThat(first.getColor()).isEqualTo("#BFE3FF");
        assertThat(first.getQuantity().toPlainString()).isEqualTo("100");
        assertThat(plan.rows().get(2).getUnit()).isEqualTo("шт");
        assertThat(plan.dependencies()).hasSize(2);
        assertThat(plan.periodTo()).isEqualTo(LocalDate.of(2027, 7, 31));
    }

    @Test
    void rejectsParentCycle() {
        List<GppRowDTO> source = sampleRows();
        // Деталь 002 внутри детали 003, а 003 — внутри 002
        source.get(4).setParentUid(part3);
        source.get(6).setParentUid(part2);
        assertBad(source, "по кругу");
    }

    @Test
    void rejectsSelfParent() {
        List<GppRowDTO> source = sampleRows();
        source.get(4).setParentUid(part2);
        assertBad(source, "сама в себя");
    }

    @Test
    void rejectsDependencyCycle() {
        List<GppRowDTO> source = sampleRows();
        // 001 ждёт 003, 003 ждёт 002, 002 ждёт 001
        source.get(1).setDependsOn(List.of(part3));
        assertBad(source, "цикл");
    }

    @Test
    void rejectsSelfDependency() {
        List<GppRowDTO> source = sampleRows();
        source.get(1).setDependsOn(List.of(part1));
        assertBad(source, "сама от себя");
    }

    @Test
    void rejectsMaterialWithDates() {
        List<GppRowDTO> source = sampleRows();
        source.get(2).setStart("2027-01-10");
        assertBad(source, "не может быть дат");
        source = sampleRows();
        source.get(2).setDurationDays(5);
        assertBad(source, "не может быть дат");
    }

    @Test
    void rejectsMaterialWithChildren() {
        List<GppRowDTO> source = sampleRows();
        source.add(material(uid(), mat1, 0, "X1", "Вложенный материал"));
        assertBad(source, "не может быть вложенных строк");
    }

    @Test
    void rejectsPartAtRoot() {
        List<GppRowDTO> source = sampleRows();
        source.get(1).setParentUid(null);
        assertBad(source, "должна входить в изделие");
    }

    @Test
    void rejectsNestedProduct() {
        List<GppRowDTO> source = sampleRows();
        source.add(row(uid(), part1, 5, "PRODUCT", "000", "Р03.900", "Вложенное изделие", null, null, null));
        assertBad(source, "только на верхнем уровне");
    }

    @Test
    void rejectsFinishBeforeStart() {
        List<GppRowDTO> source = sampleRows();
        source.get(1).setFinish("2027-01-03");
        assertBad(source, "раньше начала");
    }

    @Test
    void acceptsOneDayTask() {
        List<GppRowDTO> source = sampleRows();
        source.get(1).setFinish("2027-01-04");
        source.get(1).setDurationDays(1);
        assertThat(GppService.validate(request(1L, source)).rows()).hasSize(8);
    }

    @Test
    void rejectsForeignParent() {
        List<GppRowDTO> source = sampleRows();
        source.get(1).setParentUid(uid());
        assertBad(source, "Родитель строки «Р03.200.000.12.01 Вал привода» не найден");
    }

    @Test
    void rejectsDuplicateUid() {
        List<GppRowDTO> source = sampleRows();
        source.add(material(mat1, part1, 2, "CTJ66", "Повтор"));
        assertBad(source, "дважды");
    }

    @Test
    void rejectsBadDependencies() {
        List<GppRowDTO> source = sampleRows();
        source.get(4).setDependsOn(List.of(uid()));
        assertBad(source, "Предшественник строки");

        source = sampleRows();
        source.get(4).setDependsOn(List.of(mat1));
        assertBad(source, "не может быть предшественником");

        source = sampleRows();
        source.get(2).setDependsOn(List.of(part1));
        assertBad(source, "не может быть зависимостей");
    }

    @Test
    void dependenciesOnlyBetweenPartsOfOneProduct() {
        // Фронтенд учитывает только связи «деталь → деталь того же изделия»: другие были бы невидимы и неснимаемы
        String product2 = uid();
        String part21 = uid();
        List<GppRowDTO> source = withSecondProduct(product2, part21);
        source.get(source.size() - 1).setDependsOn(List.of(part1));
        assertBad(source, "Деталь «Р03.200.000.12.01 Вал привода» другого изделия не может быть предшественником");

        source = withSecondProduct(product2, part21);
        source.get(4).setDependsOn(List.of(product));
        assertBad(source, "Изделие «Р03.200.000.12 Вал приводной в сборе» не может быть предшественником");

        source = withSecondProduct(product2, part21);
        source.get(source.size() - 2).setDependsOn(List.of(product));
        assertBad(source, "У изделия «Р03.300.000.05 Редуктор» не может быть зависимостей");

        // Вложенная деталь (деталь в детали) может ждать деталь того же изделия с другого уровня
        source = withSecondProduct(product2, part21);
        String nested = uid();
        GppRowDTO sub = row(nested, part1, 5, "PART", "001", "Р03.200.000.12.01.01", "Втулка", "2027-04-19", "2027-04-20", 2);
        sub.setDependsOn(List.of(part3));
        source.add(sub);
        assertThat(GppService.validate(request(1L, source)).dependencies()).hasSize(3);
    }

    @Test
    void limitsDependencies() {
        // Не больше MAX_PREDECESSORS у строки
        List<GppRowDTO> source = new ArrayList<>(List.of(
                row(product, null, 0, "PRODUCT", "000", "Р03.200", "Изделие", "2027-01-04", "2027-01-04", 1)));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i <= GppService.MAX_PREDECESSORS + 1; i++) {
            String uid = uid();
            parts.add(uid);
            source.add(row(uid, product, i, "PART", null, null, "Деталь " + i, null, null, 1));
        }
        source.get(source.size() - 1).setDependsOn(parts.subList(0, GppService.MAX_PREDECESSORS));
        assertThat(GppService.validate(request(1L, source)).dependencies()).hasSize(GppService.MAX_PREDECESSORS);
        source.get(source.size() - 1).setDependsOn(parts.subList(0, GppService.MAX_PREDECESSORS + 1));
        assertBad(source, "больше " + GppService.MAX_PREDECESSORS + " предшественников");

        // Не больше MAX_DEPENDENCIES во всём документе: детали с №100 ждут по 100 предыдущих
        int per = GppService.MAX_PREDECESSORS;
        int dependent = GppService.MAX_DEPENDENCIES / per;
        List<GppRowDTO> many = new ArrayList<>(List.of(
                row(product, null, 0, "PRODUCT", "000", "Р03.200", "Изделие", "2027-01-04", "2027-01-04", 1)));
        List<String> uids = new ArrayList<>();
        for (int i = 0; i < per + dependent + 1; i++) {
            String uid = uid();
            uids.add(uid);
            GppRowDTO part = row(uid, product, i, "PART", null, null, "Деталь " + i, null, null, 1);
            if (i >= per && i < per + dependent) part.setDependsOn(new ArrayList<>(uids.subList(i - per, i)));
            many.add(part);
        }
        assertThat(GppService.validate(request(1L, many)).dependencies()).hasSize(GppService.MAX_DEPENDENCIES);
        many.get(many.size() - 1).setDependsOn(List.of(uids.get(0)));
        assertBad(many, "больше " + GppService.MAX_DEPENDENCIES + " зависимостей");
    }

    @Test
    void hugeQuantityIsRejectedWithoutExpanding() throws Exception {
        // 1e999999999 — 11 символов JSON; раскрытие такого числа заняло бы минуты или упало бы с ArithmeticException
        BigDecimal fromJson = new ObjectMapper().readValue("{\"quantity\": 1e999999999}", GppRowDTO.class).getQuantity();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (BigDecimal huge : List.of(fromJson, new BigDecimal("1e30000000"), new BigDecimal("1E+12"),
                    new BigDecimal("999999999999.9991"))) {
                List<GppRowDTO> source = sampleRows();
                source.get(1).setQuantity(huge);
                assertBad(source, "Слишком большое количество строки «Р03.200.000.12.01 Вал привода»: не больше 999 999 999 999,999");
            }
            List<GppRowDTO> source = sampleRows();
            source.get(1).setQuantity(new BigDecimal("1e-999999999"));
            assertBad(source, "не больше 3 знаков после запятой");
        });
    }

    @Test
    void quantityIsNormalized() {
        List<GppRowDTO> source = sampleRows();
        source.get(1).setQuantity(new BigDecimal("999999999999.999"));
        source.get(2).setQuantity(new BigDecimal("1.20E+3"));
        source.get(3).setQuantity(new BigDecimal("0E-999999999"));
        source.get(4).setQuantity(new BigDecimal("2.5000"));
        List<GppRow> plan = GppService.validate(request(1L, source)).rows();
        assertThat(plan.get(1).getQuantity().toPlainString()).isEqualTo("999999999999.999");
        assertThat(plan.get(2).getQuantity().toPlainString()).isEqualTo("1200");
        assertThat(plan.get(3).getQuantity().toPlainString()).isEqualTo("0");
        assertThat(plan.get(4).getQuantity().toPlainString()).isEqualTo("2.5");
    }

    @Test
    void rejectsControlAndBidiCharacters() {
        // NUL отверг бы PostgreSQL общим сообщением без строки; U+202E показывает код задом наперёд
        List<GppRowDTO> source = sampleRows();
        source.get(1).setName("Вал\u0000привода");
        assertBad(source, "Наименование строки «Р03.200.000.12.01 Вал\uFFFDпривода» содержит недопустимые символы");

        source = sampleRows();
        source.get(2).setCode("CTJ\u202E66");
        assertBad(source, "Код строки «CTJ\uFFFD66 Пластина резьбовая сменная твердосплавная» содержит недопустимые символы");

        source = sampleRows();
        source.get(2).setUnit("ш\tт");
        assertBad(source, "Единица измерения строки");

        source = sampleRows();
        source.get(1).setOpNumber("0\u206601");
        assertBad(source, "Номер операции строки");

        source = sampleRows();
        source.get(1).setName("Вал\u0085привода");
        assertBad(source, "содержит недопустимые символы");

        // Пробелы и переводы строк по краям просто обрезаются, обычный текст (в т. ч. «№», «Ø», «±») проходит
        source = sampleRows();
        source.get(1).setName("\n Вал привода Ø40 ±0,1 №2\t");
        assertThat(GppService.validate(request(1L, source)).rows().get(1).getName()).isEqualTo("Вал привода Ø40 ±0,1 №2");
    }

    @Test
    void rejectsBadFields() {
        Consumer<Consumer<GppRowDTO>> check = change -> {
            List<GppRowDTO> source = sampleRows();
            change.accept(source.get(1));
            assertThatThrownBy(() -> GppService.validate(request(1L, source)))
                    .isInstanceOf(GppException.class)
                    .satisfies(status(HttpStatus.BAD_REQUEST));
        };
        check.accept(row -> row.setUid("не-uuid"));
        check.accept(row -> row.setUid(null));
        check.accept(row -> row.setName("   "));
        check.accept(row -> row.setName("x".repeat(501)));
        check.accept(row -> row.setCode("x".repeat(101)));
        check.accept(row -> row.setOpNumber("12345678901"));
        check.accept(row -> row.setKind("DETAIL"));
        check.accept(row -> row.setQuantity(null));
        check.accept(row -> row.setQuantity(new BigDecimal("-1")));
        check.accept(row -> row.setQuantity(new BigDecimal("1.2345")));
        check.accept(row -> row.setStart("2027-02-30"));
        check.accept(row -> row.setStart("04.01.2027"));
        check.accept(row -> row.setDurationDays(0));
        check.accept(row -> row.setDurationDays(3651));
        check.accept(row -> row.setColor("red"));
        check.accept(row -> row.setPosition(-1));
        check.accept(row -> row.setDependsOn(List.of("x")));
    }

    @Test
    void rejectsBadHeader() {
        assertThatThrownBy(() -> GppService.validate(null)).isInstanceOf(GppException.class);
        assertBadHeader(new GppSaveRequest(null, "GENERAL", true, "2027-01-01", "2027-07-31", List.of()), "версия");
        assertBadHeader(new GppSaveRequest(1L, "PARALLEL", true, "2027-01-01", "2027-07-31", List.of()), "тип ГПП");
        assertBadHeader(new GppSaveRequest(1L, "GENERAL", null, "2027-01-01", "2027-07-31", List.of()), "автоматического");
        assertBadHeader(new GppSaveRequest(1L, "GENERAL", true, "2027-08-01", "2027-07-31", List.of()), "раньше начала");
        assertBadHeader(new GppSaveRequest(1L, "GENERAL", true, null, "2027-07-31", List.of()), "периода");
        List<GppRowDTO> many = new ArrayList<>();
        for (int i = 0; i <= GppService.MAX_ROWS; i++) many.add(material(uid(), null, i, null, "Материал " + i));
        assertBadHeader(new GppSaveRequest(1L, "GENERAL", true, "2027-01-01", "2027-07-31", many), "5000");
    }

    // ==================== Данные ====================

    private List<GppRowDTO> sampleRows() {
        List<GppRowDTO> source = new ArrayList<>();
        source.add(row(product, null, 0, "PRODUCT", "000", "Р03.200.000.12", "Вал приводной в сборе", "2027-01-04", "2027-04-18", 20));
        source.add(row(part1, product, 0, "PART", "001", "Р03.200.000.12.01", "Вал привода", "2027-01-04", "2027-02-02", 30));
        source.add(material(mat1, part1, 0, "CTJ66", "Пластина резьбовая сменная твердосплавная"));
        source.add(material(mat2, part1, 1, "AOKT060208PEER-VM-HR5120", "Пластина фрезерная"));
        GppRowDTO second = row(part2, product, 1, "PART", "002", "Р03.200.000.12.02", "Корпус наружного шарнира", "2027-02-03", "2027-03-09", 35);
        second.setQuantity(new BigDecimal("200"));
        second.setDependsOn(List.of(part1));
        second.setCollapsed(true);
        source.add(second);
        source.add(material(mat3, part2, 0, "DCMT11T304-PM", "Пластина токарная"));
        GppRowDTO third = row(part3, product, 2, "PART", "003", "Р03.200.000.12.03", "Сепаратор", "2027-03-10", "2027-04-18", 40);
        third.setDependsOn(List.of(part2));
        source.add(third);
        source.add(material(mat4, part3, 0, "CKJNL2525M16", "Державка проходная для наружного точения"));
        return source;
    }

    /** Изделие с референса и второе изделие с одной деталью (последние две строки) */
    private List<GppRowDTO> withSecondProduct(String product2, String part21) {
        List<GppRowDTO> source = sampleRows();
        source.add(row(product2, null, 1, "PRODUCT", "000", "Р03.300.000.05", "Редуктор", "2027-01-04", "2027-02-02", 10));
        source.add(row(part21, product2, 0, "PART", "001", "Р03.300.000.05.01", "Корпус редуктора", "2027-01-04", "2027-02-02", 30));
        return source;
    }

    private static GppRowDTO row(String uid, String parent, int position, String kind, String op, String code, String name,
                                 String start, String finish, Integer duration) {
        return GppRowDTO.builder()
                .uid(uid).parentUid(parent).position(position).opNumber(op).code(code).name(name).kind(kind)
                .quantity(new BigDecimal("100")).unit("шт").start(start).finish(finish).durationDays(duration)
                .color("#FFB3B3").dependsOn(List.of()).collapsed(false)
                .build();
    }

    private static GppRowDTO material(String uid, String parent, int position, String code, String name) {
        return GppRowDTO.builder()
                .uid(uid).parentUid(parent).position(position).code(code).name(name).kind("MATERIAL")
                .quantity(new BigDecimal("140")).unit("шт").dependsOn(List.of()).collapsed(false)
                .build();
    }

    private static GppSaveRequest request(long version, List<GppRowDTO> source) {
        return new GppSaveRequest(version, "GENERAL", true, "2027-01-01", "2027-07-31", source);
    }

    private static GppRow stored(UUID uid, UUID parent, int position, String kind, String quantity) {
        GppRow row = new GppRow();
        row.setUid(uid);
        row.setParentUid(parent);
        row.setPosition(position);
        row.setName("Строка " + position);
        row.setKind(kind);
        row.setQuantity(new BigDecimal(quantity));
        row.setUnit("шт");
        row.setCollapsed(false);
        return row;
    }

    private static User user(String username, String last, String first, String middle) {
        User user = new User();
        user.setUsername(username);
        user.setLastName(last);
        user.setFirstName(first);
        user.setMiddleName(middle);
        return user;
    }

    private static void assertBad(List<GppRowDTO> source, String message) {
        assertThatThrownBy(() -> GppService.validate(request(1L, source)))
                .isInstanceOf(GppException.class)
                .satisfies(status(HttpStatus.BAD_REQUEST))
                .hasMessageContaining(message);
    }

    private static void assertBadHeader(GppSaveRequest body, String message) {
        assertThatThrownBy(() -> GppService.validate(body))
                .isInstanceOf(GppException.class)
                .satisfies(status(HttpStatus.BAD_REQUEST))
                .hasMessageContaining(message);
    }

    private static Consumer<Throwable> status(HttpStatus expected) {
        return e -> assertThat(((GppException) e).status()).isEqualTo(expected);
    }

    private static <T> List<T> list(Iterable<T> items) {
        return StreamSupport.stream(items.spliterator(), false).toList();
    }

    private static String uid() {
        return UUID.randomUUID().toString();
    }
}
