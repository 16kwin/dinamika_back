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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Документ «График подготовки производства (ГПП по предприятию)»: дерево изделие → детали → материалы
 * и зависимости «окончание → начало» для диаграммы Ганта.
 *
 * <p>Даты считает фронтенд (режимы «Общий по предприятию» и «Последовательный», автоматический расчёт),
 * сервер проверяет структуру и непротиворечивость и хранит. Сохранение — полная замена строк в одной
 * транзакции с версией документа: если документ успели изменить, ответ 409 и клиент перезагружает график.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GppService {

    /** Защита от случайно огромного запроса */
    static final int MAX_ROWS = 5000;
    static final int MAX_DURATION_DAYS = 3650;
    /** Предшественников у одной строки и всего в документе: защита от огромного графа зависимостей */
    static final int MAX_PREDECESSORS = 100;
    static final int MAX_DEPENDENCIES = 20000;
    /** Строки и зависимости вставляются пакетами JDBC такого размера, а не по запросу на каждую */
    static final int INSERT_BATCH = 500;
    /** Сколько сохранение ждёт своей очереди (блокировку документа); дальше — ответ «повторите позже» */
    static final String LOCK_TIMEOUT = "10s";
    static final String DEFAULT_UNIT = "шт";

    private static final int MAX_NAME = 500;
    private static final int MAX_CODE = 100;
    private static final int MAX_OP_NUMBER = 10;
    private static final int MAX_UNIT = 20;
    private static final int MAX_USER_NAME = 255;
    /** Предел NUMERIC(15,3) */
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999.999");
    /** Разумные границы дат графика: дальше — почти наверняка опечатка в годе */
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2100;

    private static final Set<String> GPP_TYPES = Set.of(GppDocument.TYPE_GENERAL, GppDocument.TYPE_SEQUENTIAL);
    private static final Set<String> KINDS = Set.of(GppRow.KIND_PRODUCT, GppRow.KIND_PART, GppRow.KIND_MATERIAL);
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    /**
     * Управляющие символы и символы направления текста (U+202A–U+202E, U+2066–U+2069, LRM/RLM/ALM):
     * NUL не примет база, остальные ломают вывод и позволяют подменить видимый код (текст задом наперёд)
     */
    private static final Pattern FORBIDDEN_CHARS = Pattern.compile("[\\p{Cc}\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");
    /** Ключ верхнего уровня для счётчиков братьев: merge в HashMap не принимает null-ключ */
    private static final UUID ROOT = new UUID(0L, 0L);

    private final GppDocumentRepository documentRepository;
    private final GppRowRepository rowRepository;
    private final GppRowDependencyRepository dependencyRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Проверенный запрос сохранения: строки в порядке обхода дерева (родитель раньше детей — в таком
     * порядке их и вставляем) с перенумерованными position и зависимости строк.
     */
    record Plan(String gppType, boolean autoCalc, LocalDate periodFrom, LocalDate periodTo,
                List<GppRow> rows, List<GppRowDependency> dependencies) {
    }

    // ==================== Чтение ====================

    /**
     * Документ предприятия; canEdit — может ли текущий пользователь его сохранять. Шапка, строки и зависимости
     * читаются тремя запросами: REPEATABLE READ даёт им один снимок базы, иначе сохранение, завершившееся между
     * ними, смешало бы в ответе версию одного состояния со строками или зависимостями другого
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GppDocumentDTO get(boolean canEdit) {
        GppDocument doc = documentRepository.findFirstByOrderByCreatedAtAsc().orElseThrow(GppService::missing);
        return toDto(doc, rowRepository.findByDocUid(doc.getUid()), dependencyRepository.findByDoc(doc.getUid()), canEdit);
    }

    // ==================== Сохранение ====================

    /** Полная замена строк документа; version в запросе должна совпасть с текущей, иначе 409 */
    @Transactional
    public GppDocumentDTO save(GppSaveRequest body, String username) {
        Plan plan = validate(body);

        // Очередь на блокировку документа не бесконечна: дольше LOCK_TIMEOUT — ошибка блокировки (ответ 503)
        documentRepository.setLockTimeout(LOCK_TIMEOUT);
        GppDocument doc = documentRepository.findFirstForUpdateByOrderByCreatedAtAsc().orElseThrow(GppService::missing);
        if (!doc.getVersion().equals(body.getVersion())) {
            String who = doc.getUpdatedBy() == null ? "" : " (" + doc.getUpdatedBy() + ")";
            throw GppException.conflict("График уже изменил другой пользователь" + who
                    + ". Обновите документ и повторите правку.");
        }

        // Старые строки удаляются одним запросом, их зависимости — каскадом; новые вставляются от родителей к детям
        rowRepository.deleteByDoc(doc.getUid());
        // Строки и зависимости новые (Persistable): вставка пакетами, порядок «родитель раньше детей» сохраняется
        entityManager.unwrap(Session.class).setJdbcBatchSize(INSERT_BATCH);
        plan.rows().forEach(row -> row.setDocUid(doc.getUid()));
        List<GppRow> rows = rowRepository.saveAllAndFlush(plan.rows());
        List<GppRowDependency> dependencies = dependencyRepository.saveAllAndFlush(plan.dependencies());

        doc.setGppType(plan.gppType());
        doc.setAutoCalc(plan.autoCalc());
        doc.setPeriodFrom(plan.periodFrom());
        doc.setPeriodTo(plan.periodTo());
        doc.setVersion(doc.getVersion() + 1);
        doc.setUpdatedBy(displayName(username));
        // Миллисекунды: ответ PUT и следующий GET отдают одно и то же значение
        doc.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        GppDocument saved = documentRepository.saveAndFlush(doc);
        log.info("ГПП сохранён: версия {}, строк {}, зависимостей {} ({})",
                saved.getVersion(), rows.size(), dependencies.size(), username);
        return toDto(saved, rows, dependencies, true);
    }

    // ==================== Проверка запроса ====================

    /**
     * Проверка запроса сохранения: поля строк, uid без повторов, родитель есть в запросе, изделие — только
     * в корне, деталь — под изделием или деталью, у материала нет детей, дат и зависимостей, нет циклов
     * ни по родителям, ни по зависимостям; зависимости — только между деталями одного изделия (их и учитывает
     * расчёт на фронтенде). Возвращает строки в порядке обхода дерева.
     */
    static Plan validate(GppSaveRequest body) {
        if (body == null) throw GppException.badRequest("Пустой запрос");
        if (body.getVersion() == null) throw GppException.badRequest("Не указана версия документа");
        String gppType = body.getGppType() == null ? "" : body.getGppType().trim();
        if (!GPP_TYPES.contains(gppType)) {
            throw GppException.badRequest("Неизвестный тип ГПП: «" + nullToEmpty(body.getGppType()) + "»");
        }
        if (body.getAutoCalc() == null) throw GppException.badRequest("Не указан режим автоматического расчёта");
        LocalDate periodFrom = requireDate(body.getPeriodFrom(), "начала периода выборки");
        LocalDate periodTo = requireDate(body.getPeriodTo(), "конца периода выборки");
        if (periodTo.isBefore(periodFrom)) throw GppException.badRequest("Конец периода выборки раньше начала");

        List<GppRowDTO> source = body.getRows() == null ? List.of() : body.getRows();
        if (source.size() > MAX_ROWS) {
            throw GppException.badRequest("В графике не может быть больше " + MAX_ROWS + " строк");
        }

        // 1. Поля каждой строки; uid без повторов
        Map<UUID, GppRow> rows = new LinkedHashMap<>();
        Map<UUID, String> labels = new HashMap<>();
        Map<UUID, List<UUID>> dependsOn = new LinkedHashMap<>();
        int dependencyCount = 0;
        for (int i = 0; i < source.size(); i++) {
            GppRowDTO dto = source.get(i);
            if (dto == null) throw GppException.badRequest("Пустая строка №" + (i + 1));
            String label = label(dto, i);
            GppRow row = parseRow(dto, label);
            if (rows.putIfAbsent(row.getUid(), row) != null) {
                throw GppException.badRequest("Строка " + label + " указана дважды (uid " + row.getUid() + ")");
            }
            labels.put(row.getUid(), label);
            List<UUID> predecessors = parseDependsOn(dto.getDependsOn(), label);
            dependencyCount += predecessors.size();
            if (dependencyCount > MAX_DEPENDENCIES) {
                throw GppException.badRequest("В графике не может быть больше " + MAX_DEPENDENCIES + " зависимостей");
            }
            dependsOn.put(row.getUid(), predecessors);
        }

        // 2. Родители и вид элемента; 3. без циклов по родителям
        for (GppRow row : rows.values()) checkParent(row, rows, labels);
        checkHierarchyCycles(rows, labels);

        // 4. Зависимости: деталь ждёт другие детали того же изделия, без циклов
        List<GppRowDependency> dependencies = checkDependencies(rows, dependsOn, labels);

        // 5. Порядок обхода дерева; position — подряд с 0 среди братьев
        List<GppRow> ordered = treeOrder(rows.values());
        Map<UUID, Integer> nextPosition = new HashMap<>();
        for (GppRow row : ordered) {
            row.setPosition(nextPosition.merge(row.getParentUid() == null ? ROOT : row.getParentUid(), 1, Integer::sum) - 1);
        }
        return new Plan(gppType, body.getAutoCalc(), periodFrom, periodTo, ordered, dependencies);
    }

    private static GppRow parseRow(GppRowDTO dto, String label) {
        GppRow row = new GppRow();
        row.setUid(parseUuid(dto.getUid(), "идентификатор строки " + label, true));
        row.setParentUid(parseUuid(dto.getParentUid(), "идентификатор родителя строки " + label, false));
        int position = dto.getPosition() == null ? 0 : dto.getPosition();
        if (position < 0) throw GppException.badRequest("Порядковый номер строки " + label + " не может быть отрицательным");
        row.setPosition(position);
        row.setOpNumber(optionalText(dto.getOpNumber(), MAX_OP_NUMBER, "Номер операции строки " + label));
        row.setCode(optionalText(dto.getCode(), MAX_CODE, "Код строки " + label));

        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) throw GppException.badRequest("Не указано наименование строки " + label);
        if (name.length() > MAX_NAME) {
            throw GppException.badRequest("Наименование строки " + label + " длиннее " + MAX_NAME + " символов");
        }
        checkChars(name, "Наименование строки " + label);
        row.setName(name);

        String kind = dto.getKind() == null ? "" : dto.getKind().trim();
        if (!KINDS.contains(kind)) {
            throw GppException.badRequest("Неизвестный вид элемента строки " + label + ": «" + nullToEmpty(dto.getKind()) + "»");
        }
        row.setKind(kind);
        row.setQuantity(checkQuantity(dto.getQuantity(), label));
        String unit = optionalText(dto.getUnit(), MAX_UNIT, "Единица измерения строки " + label);
        row.setUnit(unit == null ? DEFAULT_UNIT : unit);

        LocalDate start = optionalDate(dto.getStart(), "начала строки " + label);
        LocalDate finish = optionalDate(dto.getFinish(), "окончания строки " + label);
        Integer duration = dto.getDurationDays();
        if (GppRow.KIND_MATERIAL.equals(kind) && (start != null || finish != null || duration != null)) {
            throw GppException.badRequest("У материала " + label + " не может быть дат и длительности");
        }
        if (start != null && finish != null && finish.isBefore(start)) {
            throw GppException.badRequest("Окончание строки " + label + " раньше начала");
        }
        if (duration != null && (duration < 1 || duration > MAX_DURATION_DAYS)) {
            throw GppException.badRequest("Длительность строки " + label + " должна быть от 1 до " + MAX_DURATION_DAYS + " дней");
        }
        row.setStartDate(start);
        row.setFinishDate(finish);
        row.setDurationDays(duration);

        String color = dto.getColor() == null ? "" : dto.getColor().trim();
        if (!color.isEmpty() && !COLOR_PATTERN.matcher(color).matches()) {
            throw GppException.badRequest("Цвет строки " + label + " должен быть в формате #RRGGBB");
        }
        row.setColor(color.isEmpty() ? null : color.toUpperCase());
        row.setCollapsed(Boolean.TRUE.equals(dto.getCollapsed()));
        return row;
    }

    /** uid предшественников без повторов, в порядке запроса; не больше MAX_PREDECESSORS */
    private static List<UUID> parseDependsOn(List<String> values, String label) {
        if (values == null || values.isEmpty()) return List.of();
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(parseUuid(value, "идентификатор предшественника строки " + label, true));
            if (result.size() > MAX_PREDECESSORS) {
                throw GppException.badRequest("У строки " + label + " не может быть больше " + MAX_PREDECESSORS + " предшественников");
            }
        }
        return new ArrayList<>(result);
    }

    private static void checkParent(GppRow row, Map<UUID, GppRow> rows, Map<UUID, String> labels) {
        String label = labels.get(row.getUid());
        if (row.getParentUid() == null) {
            if (GppRow.KIND_PART.equals(row.getKind())) {
                throw GppException.badRequest("Деталь " + label + " должна входить в изделие или другую деталь");
            }
            return;
        }
        if (row.getParentUid().equals(row.getUid())) {
            throw GppException.badRequest("Строка " + label + " не может входить сама в себя");
        }
        GppRow parent = rows.get(row.getParentUid());
        if (parent == null) throw GppException.badRequest("Родитель строки " + label + " не найден в документе");
        if (GppRow.KIND_MATERIAL.equals(parent.getKind())) {
            throw GppException.badRequest("У материала " + labels.get(parent.getUid()) + " не может быть вложенных строк");
        }
        if (GppRow.KIND_PRODUCT.equals(row.getKind())) {
            throw GppException.badRequest("Изделие " + label + " может быть только на верхнем уровне");
        }
        // Здесь родитель — изделие или деталь: деталь и материал могут входить в любой из них
    }

    /** Подъём от каждой строки к корню: повтор строки на пути — цикл по родителям */
    private static void checkHierarchyCycles(Map<UUID, GppRow> rows, Map<UUID, String> labels) {
        Set<UUID> rooted = new HashSet<>();
        for (UUID start : rows.keySet()) {
            List<UUID> path = new ArrayList<>();
            Set<UUID> onPath = new HashSet<>();
            UUID current = start;
            while (current != null && !rooted.contains(current)) {
                if (!onPath.add(current)) {
                    List<UUID> cycle = new ArrayList<>(path.subList(path.indexOf(current), path.size()));
                    cycle.add(current);
                    throw GppException.badRequest("Строки входят друг в друга по кругу: " + chain(cycle, labels));
                }
                path.add(current);
                current = rows.get(current).getParentUid();
            }
            rooted.addAll(path);
        }
    }

    private static List<GppRowDependency> checkDependencies(Map<UUID, GppRow> rows, Map<UUID, List<UUID>> dependsOn,
                                                            Map<UUID, String> labels) {
        List<GppRowDependency> result = new ArrayList<>();
        Map<UUID, UUID> products = productsOf(rows);
        for (Map.Entry<UUID, List<UUID>> entry : dependsOn.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            UUID uid = entry.getKey();
            String label = labels.get(uid);
            String kind = rows.get(uid).getKind();
            if (GppRow.KIND_MATERIAL.equals(kind)) {
                throw GppException.badRequest("У материала " + label + " не может быть зависимостей");
            }
            if (!GppRow.KIND_PART.equals(kind)) {
                throw GppException.badRequest("У изделия " + label + " не может быть зависимостей: предшественников указывают у деталей");
            }
            int position = 0;
            for (UUID predecessor : entry.getValue()) {
                if (predecessor.equals(uid)) {
                    throw GppException.badRequest("Строка " + label + " не может зависеть сама от себя");
                }
                GppRow before = rows.get(predecessor);
                if (before == null) throw GppException.badRequest("Предшественник строки " + label + " не найден в документе");
                if (GppRow.KIND_MATERIAL.equals(before.getKind())) {
                    throw GppException.badRequest("Материал " + labels.get(predecessor) + " не может быть предшественником строки " + label);
                }
                // Расчёт плана учитывает только детали того же изделия — другие связи были бы невидимы на экране
                if (!GppRow.KIND_PART.equals(before.getKind())) {
                    throw GppException.badRequest("Изделие " + labels.get(predecessor) + " не может быть предшественником строки "
                            + label + ": предшественником может быть только деталь того же изделия");
                }
                if (!products.get(predecessor).equals(products.get(uid))) {
                    throw GppException.badRequest("Деталь " + labels.get(predecessor) + " другого изделия не может быть предшественником строки "
                            + label + ": предшественником может быть только деталь того же изделия");
                }
                result.add(new GppRowDependency(uid, predecessor, position++));
            }
        }
        checkDependencyCycles(dependsOn, labels);
        return result;
    }

    /** Изделие (корень дерева) каждой строки; вызывать после checkParent и checkHierarchyCycles */
    private static Map<UUID, UUID> productsOf(Map<UUID, GppRow> rows) {
        Map<UUID, UUID> products = new HashMap<>();
        for (UUID start : rows.keySet()) {
            List<UUID> path = new ArrayList<>();
            UUID current = start;
            UUID product = null;
            while (product == null) {
                product = products.get(current);
                if (product != null) break;
                path.add(current);
                UUID parent = rows.get(current).getParentUid();
                if (parent == null) product = current;
                else current = parent;
            }
            for (UUID uid : path) products.put(uid, product);
        }
        return products;
    }

    /**
     * Обход в глубину по рёбрам «строка → предшественник» (без рекурсии: строк до 5000).
     * Возврат в строку, которая ещё на пути, — цикл; в сообщении он показан по ходу работ.
     */
    private static void checkDependencyCycles(Map<UUID, List<UUID>> dependsOn, Map<UUID, String> labels) {
        Set<UUID> done = new HashSet<>();
        Set<UUID> onPath = new HashSet<>();
        for (UUID start : dependsOn.keySet()) {
            if (done.contains(start)) continue;
            List<UUID> path = new ArrayList<>();
            Deque<Iterator<UUID>> iterators = new ArrayDeque<>();
            path.add(start);
            onPath.add(start);
            iterators.push(dependsOn.getOrDefault(start, List.of()).iterator());
            while (!iterators.isEmpty()) {
                Iterator<UUID> it = iterators.peek();
                if (!it.hasNext()) {
                    UUID finished = path.remove(path.size() - 1);
                    onPath.remove(finished);
                    done.add(finished);
                    iterators.pop();
                    continue;
                }
                UUID next = it.next();
                if (done.contains(next)) continue;
                if (onPath.contains(next)) {
                    // path: next → … → текущая строка по рёбрам «зависит от»; по ходу работ — в обратном порядке
                    List<UUID> cycle = new ArrayList<>(path.subList(path.indexOf(next), path.size()));
                    Collections.reverse(cycle);
                    cycle.add(cycle.get(0));
                    throw GppException.badRequest("Зависимости образуют цикл: " + chain(cycle, labels));
                }
                path.add(next);
                onPath.add(next);
                iterators.push(dependsOn.getOrDefault(next, List.of()).iterator());
            }
        }
    }

    /**
     * Строки в порядке обхода дерева: родитель, затем его дети по position (при равных — в исходном порядке).
     * Строки, не достижимые от корней (в базе их быть не должно), идут в конце.
     */
    static List<GppRow> treeOrder(Collection<GppRow> rows) {
        Map<UUID, List<GppRow>> children = new HashMap<>();
        List<GppRow> roots = new ArrayList<>();
        for (GppRow row : rows) {
            if (row.getParentUid() == null) roots.add(row);
            else children.computeIfAbsent(row.getParentUid(), key -> new ArrayList<>()).add(row);
        }
        Comparator<GppRow> byPosition = Comparator.comparing(row -> row.getPosition() == null ? 0 : row.getPosition());
        List<GppRow> result = new ArrayList<>(rows.size());
        Set<UUID> visited = new HashSet<>();
        Deque<GppRow> stack = new ArrayDeque<>();
        pushReversed(stack, roots, byPosition);
        while (!stack.isEmpty()) {
            GppRow row = stack.pop();
            if (!visited.add(row.getUid())) continue;
            result.add(row);
            pushReversed(stack, children.getOrDefault(row.getUid(), List.of()), byPosition);
        }
        if (result.size() < rows.size()) {
            for (GppRow row : rows) {
                if (!visited.contains(row.getUid())) result.add(row);
            }
        }
        return result;
    }

    private static void pushReversed(Deque<GppRow> stack, List<GppRow> siblings, Comparator<GppRow> order) {
        List<GppRow> sorted = new ArrayList<>(siblings);
        sorted.sort(order);
        for (int i = sorted.size() - 1; i >= 0; i--) stack.push(sorted.get(i));
    }

    // ==================== Разбор значений ====================

    /**
     * «Р03.200.000.12 Вал приводной в сборе» — так строку видит пользователь; без текста — номер в запросе.
     * Недопустимые символы заменены на U+FFFD: сообщение об ошибке читается и показывает, где они
     */
    static String label(GppRowDTO dto, int index) {
        String text = (nullToEmpty(dto.getCode()).trim() + " " + nullToEmpty(dto.getName()).trim()).trim();
        text = FORBIDDEN_CHARS.matcher(text).replaceAll("\uFFFD");
        if (text.isEmpty()) return "№" + (index + 1);
        if (text.length() > 80) text = text.substring(0, 79) + "…";
        return "«" + text + "»";
    }

    private static UUID parseUuid(String value, String what, boolean required) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            if (required) throw GppException.badRequest("Не указан " + what);
            return null;
        }
        if (!UUID_PATTERN.matcher(text).matches()) throw GppException.badRequest("Некорректный " + what + ": «" + text + "»");
        return UUID.fromString(text);
    }

    private static LocalDate requireDate(String value, String what) {
        LocalDate date = optionalDate(value, what);
        if (date == null) throw GppException.badRequest("Не указана дата " + what);
        return date;
    }

    /** 'YYYY-MM-DD' или пусто */
    private static LocalDate optionalDate(String value, String what) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return null;
        LocalDate date;
        try {
            if (!DATE_PATTERN.matcher(text).matches()) throw new DateTimeParseException("формат", text, 0);
            date = LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw GppException.badRequest("Некорректная дата " + what + ": «" + text + "», ожидается ГГГГ-ММ-ДД");
        }
        if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
            throw GppException.badRequest("Дата " + what + " вне диапазона " + MIN_YEAR + "–" + MAX_YEAR + " годов");
        }
        return date;
    }

    /** Обрезанный текст или null, если пусто */
    private static String optionalText(String value, int maxLength, String what) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return null;
        if (text.length() > maxLength) throw GppException.badRequest(what + " длиннее " + maxLength + " символов");
        checkChars(text, what);
        return text;
    }

    /** Без управляющих символов и символов направления текста — до обращения к базе, с указанием строки */
    private static void checkChars(String text, String what) {
        if (FORBIDDEN_CHARS.matcher(text).find()) {
            throw GppException.badRequest(what + " содержит недопустимые символы (управляющие или смены направления текста)");
        }
    }

    /** Количество ≥ 0, не больше 3 знаков после запятой; лишние нули отбрасываются (100.000 → 100) */
    private static BigDecimal checkQuantity(BigDecimal quantity, String label) {
        if (quantity == null) throw GppException.badRequest("Не указано количество строки " + label);
        if (quantity.signum() < 0) throw GppException.badRequest("Количество строки " + label + " не может быть отрицательным");
        // Сначала предел: compareTo сравнивает порядки чисел, не раскрывая их, а plain() у 1e999999999 строил бы
        // BigInteger из миллиарда цифр (минуты процессора и сотни МБ памяти) или падал с ArithmeticException
        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw GppException.badRequest("Слишком большое количество строки " + label + ": не больше 999 999 999 999,999");
        }
        if (quantity.stripTrailingZeros().scale() > 3) {
            throw GppException.badRequest("Количество строки " + label + " — не больше 3 знаков после запятой");
        }
        return plain(quantity);
    }

    /** Без лишних нулей и без экспоненты: 100.000 → 100, 0.500 → 0.5 */
    static BigDecimal plain(BigDecimal value) {
        if (value == null) return null;
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static String chain(List<UUID> uids, Map<UUID, String> labels) {
        return uids.stream().map(labels::get).collect(Collectors.joining(" → "));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ==================== Служебное ====================

    private static GppException missing() {
        return GppException.notFound("Документ «График подготовки производства» не найден");
    }

    /** ФИО пользователя для «Изменил», если оно заполнено в карточке, иначе логин */
    private String displayName(String username) {
        if (username == null || username.isBlank()) return null;
        String name = userRepository.findTop1ByUsername(username).map(GppService::fullName).orElse(username);
        return name.length() > MAX_USER_NAME ? name.substring(0, MAX_USER_NAME) : name;
    }

    private static String fullName(User user) {
        String name = String.join(" ", nullToEmpty(user.getLastName()).trim(), nullToEmpty(user.getFirstName()).trim(),
                nullToEmpty(user.getMiddleName()).trim()).replaceAll("\\s+", " ").trim();
        return name.isEmpty() ? user.getUsername() : name;
    }

    private static GppDocumentDTO toDto(GppDocument doc, List<GppRow> rows, List<GppRowDependency> dependencies,
                                        boolean canEdit) {
        Map<UUID, List<String>> dependsOn = new HashMap<>();
        dependencies.stream()
                .sorted(Comparator.comparing(GppRowDependency::getPosition))
                .forEach(dep -> dependsOn.computeIfAbsent(dep.getRowUid(), key -> new ArrayList<>())
                        .add(dep.getDependsOnUid().toString()));
        // При равных position (в базе их быть не должно) порядок не зависит от порядка выборки
        List<GppRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(GppRow::getUid));
        List<GppRowDTO> rowDtos = treeOrder(sorted).stream()
                .map(row -> toDto(row, dependsOn.getOrDefault(row.getUid(), List.of())))
                .toList();
        return new GppDocumentDTO(
                doc.getUid().toString(),
                doc.getTitle(),
                doc.getGppType(),
                Boolean.TRUE.equals(doc.getAutoCalc()),
                doc.getPeriodFrom().toString(),
                doc.getPeriodTo().toString(),
                doc.getVersion(),
                doc.getUpdatedAt(),
                doc.getUpdatedBy(),
                canEdit,
                rowDtos);
    }

    private static GppRowDTO toDto(GppRow row, List<String> dependsOn) {
        return GppRowDTO.builder()
                .uid(row.getUid().toString())
                .parentUid(row.getParentUid() == null ? null : row.getParentUid().toString())
                .position(row.getPosition())
                .opNumber(row.getOpNumber())
                .code(row.getCode())
                .name(row.getName())
                .kind(row.getKind())
                .quantity(plain(row.getQuantity()))
                .unit(row.getUnit())
                .start(row.getStartDate() == null ? null : row.getStartDate().toString())
                .finish(row.getFinishDate() == null ? null : row.getFinishDate().toString())
                .durationDays(row.getDurationDays())
                .color(row.getColor())
                .dependsOn(dependsOn)
                .collapsed(Boolean.TRUE.equals(row.getCollapsed()))
                .build();
    }
}
