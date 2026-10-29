// MoveOrderService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MoveOrderService {

    private final DocMoveOrderRepository docRepository;
    private final RegMoveOrderLineRepository lineRepository;
    private final RegTransferLineRepository transferLineRepository;
    private final MoveOrderEventLogRepository eventLogRepository;
    private final MoveOrderColumnSettingsService columnSettingsService;
    private final SprMaterialRepository materialRepository;
    private final StationRepository stationRepository;
    private final DocTransferRepository docTransferRepository;
    private final UserService userService;
    private final EmployeeRepository employeeRepository;
    private final StockRegisterService stockRegisterService;
    private final RegStockLevelControlRepository regStockLevelControlRepository;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> ALL_COLUMNS_ORDER = List.of(
            "code", "docDatetime", "stockToName", "deadline", "comment", "status"
    );
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(
            List.of("code", "docDatetime", "stockToName", "status")
    );

    public static final String STATUS_DRAFT         = "DRAFT";
    public static final String STATUS_ON_APPROVAL   = "ON_APPROVAL";
    public static final String STATUS_APPROVED      = "APPROVED";
    public static final String STATUS_REJECTED      = "REJECTED";
    public static final String STATUS_IN_PROGRESS   = "IN_PROGRESS";
    public static final String STATUS_CANCELLED     = "CANCELLED";
    public static final String STATUS_CLOSED        = "CLOSED";

    public static final String LINE_NOT_STARTED  = "NOT_STARTED";
    public static final String LINE_IN_PROGRESS  = "IN_PROGRESS";
    public static final String LINE_SHIPPED      = "SHIPPED";
    public static final String LINE_RECEIVED     = "RECEIVED";
    public static final String LINE_CLOSED       = "CLOSED";
    public static final String LINE_CANCELLED    = "CANCELLED";

    private static final String TRANSFER_DRAFT     = "DRAFT";
    private static final String TRANSFER_CANCELLED = "CANCELLED";

    private static final String DOC_BASE_TYPE_MOVE_ORDER = "MOVE_ORDER";

    // ==================== LIST ====================

    public MoveOrderListResponse getAllWithSettings(Integer userId) {
        String columnsJson = columnSettingsService.getColumnsJson(userId);
        Set<String> visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>(REQUIRED_COLUMNS);

        if (columnsJson != null && !columnsJson.isEmpty()) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        }

        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<DocMoveOrder> docs = docRepository.findAll();
        List<MoveOrderFlatDto> data = docs.stream()
                .sorted(Comparator.comparing(DocMoveOrder::getDocDatetime).reversed())
                .map(this::toFlatDto)
                .collect(Collectors.toList());

        MoveOrderListResponse response = new MoveOrderListResponse();
        response.setColumns(orderedColumns);
        response.setData(data);
        response.setColumnWidths(columnWidths);
        response.setRequiredColumns(new ArrayList<>(requiredColumns));
        return response;
    }

    @SuppressWarnings("unchecked")
    private void parseColumnSettings(String json, Set<String> visibleColumns,
                                     Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            visibleColumns.clear();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    if ((Boolean) value) visibleColumns.add(key);
                } else if (value instanceof Map) {
                    Map<String, Object> settings = (Map<String, Object>) value;
                    Object visible = settings.get("visible");
                    Object width = settings.get("width");
                    Object required = settings.get("required");
                    if (visible instanceof Boolean && (Boolean) visible) visibleColumns.add(key);
                    if (width instanceof Number) columnWidths.put(key, ((Number) width).doubleValue());
                    if (required instanceof Boolean && (Boolean) required) requiredColumns.add(key);
                }
            }
        } catch (Exception e) {
            visibleColumns.clear();
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== GET BY UID ====================

    public MoveOrderFlatDto getByUid(UUID uid) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ на перемещение не найден: " + uid));
        return toFlatDto(doc);
    }

    public Integer generateCode() {
        Integer max = docRepository.findMaxCode();
        return max != null ? max + 1 : 1;
    }

    // ==================== CREATE ====================

    @Transactional
    public MoveOrderFlatDto create(CreateMoveOrderRequest request, String author) {
        UUID uid = request.getUid() != null ? request.getUid() : UUID.randomUUID();

        Optional<DocMoveOrder> existing = docRepository.findById(uid);
        if (existing.isPresent()) return toFlatDto(existing.get());

        DocMoveOrder doc = DocMoveOrder.builder()
                .uid(uid)
                .code(generateCode())
                .docDatetime(request.getDocDatetime() != null ? request.getDocDatetime() : LocalDateTime.now())
                .stockToUid(request.getStockToUid())
                .comment(request.getComment())
                .useApproval(request.getUseApproval() != null ? request.getUseApproval() : Boolean.TRUE)
                .status(STATUS_DRAFT)
                .build();
        doc = docRepository.save(doc);

        if (request.getLines() != null) {
            for (CreateMoveOrderRequest.MoveOrderLineRequest lr : request.getLines()) {
                RegMoveOrderLine line = RegMoveOrderLine.builder()
                        .uid(UUID.randomUUID())
                        .doc(doc)
                        .material(lr.getMaterialUid() != null ? materialRepository.getReferenceById(lr.getMaterialUid()) : null)
                        .quantity(lr.getQuantity() != null ? lr.getQuantity() : 0)
                        .stockFromUid(lr.getStockFromUid())
                        .priority(lr.getPriority() != null ? lr.getPriority() : "MEDIUM")
                        .dueDate(lr.getDueDate())
                        .comment(lr.getComment())
                        .status(LINE_NOT_STARTED)
                        .cancelled(false)
                        .build();
                lineRepository.save(line);
            }
        }

        recalcDeadline(doc);

        logEvent(doc.getUid(), null, "CREATE",
                "Создание заказа на перемещение: код " + doc.getCode(),
                null, null, null, author);

        return toFlatDto(doc);
    }

    // ==================== UPDATE ====================

    @Transactional
    public MoveOrderFlatDto update(UUID uid, UpdateMoveOrderRequest request, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (STATUS_CANCELLED.equals(doc.getStatus())) {
            throw new RuntimeException("Отменённый заказ нельзя редактировать");
        }
        if (STATUS_CLOSED.equals(doc.getStatus())) {
            throw new RuntimeException("Закрытый заказ нельзя редактировать");
        }
        if (STATUS_IN_PROGRESS.equals(doc.getStatus())) {
            throw new RuntimeException("Заказ в работе нельзя редактировать");
        }
        if (STATUS_ON_APPROVAL.equals(doc.getStatus())) {
            throw new RuntimeException("Заказ на согласовании нельзя редактировать");
        }
        if (STATUS_REJECTED.equals(doc.getStatus())) {
            throw new RuntimeException("Отклонённый заказ нельзя редактировать");
        }

        boolean isDraft = STATUS_DRAFT.equals(doc.getStatus());
        boolean isApproved = STATUS_APPROVED.equals(doc.getStatus());
        if (!isDraft && !isApproved) {
            throw new RuntimeException("Редактировать можно только записанный или согласованный заказ");
        }

        if (isApproved) {
            updateApprovedPartial(doc, request, author);
            recalcDeadline(doc);
            return toFlatDto(doc);
        }

        // DRAFT — полное редактирование
        if (request.getDocDatetime() != null) doc.setDocDatetime(request.getDocDatetime());
        if (request.getStockToUid() != null) doc.setStockToUid(request.getStockToUid());
        if (request.getComment() != null) doc.setComment(request.getComment());
        if (request.getUseApproval() != null) doc.setUseApproval(request.getUseApproval());
        docRepository.save(doc);

        if (request.getLines() != null) {
            List<RegMoveOrderLine> existing = lineRepository.findByDocUid(uid);
            Map<UUID, RegMoveOrderLine> existingMap = existing.stream()
                    .collect(Collectors.toMap(RegMoveOrderLine::getUid, l -> l));

            for (UpdateMoveOrderRequest.UpdateMoveOrderLineRequest lr : request.getLines()) {
                if (lr.getUid() != null && existingMap.containsKey(lr.getUid())) {
                    RegMoveOrderLine line = existingMap.get(lr.getUid());
                    updateLineDraft(line, lr, author);
                    lineRepository.save(line);
                    existingMap.remove(lr.getUid());
                } else {
                    RegMoveOrderLine line = RegMoveOrderLine.builder()
                            .uid(UUID.randomUUID())
                            .doc(doc)
                            .material(lr.getMaterialUid() != null ? materialRepository.getReferenceById(lr.getMaterialUid()) : null)
                            .quantity(lr.getQuantity() != null ? lr.getQuantity() : 0)
                            .stockFromUid(lr.getStockFromUid())
                            .priority(lr.getPriority() != null ? lr.getPriority() : "MEDIUM")
                            .dueDate(lr.getDueDate())
                            .comment(lr.getComment())
                            .status(LINE_NOT_STARTED)
                            .cancelled(false)
                            .build();
                    lineRepository.save(line);
                }
            }

            for (RegMoveOrderLine orphan : existingMap.values()) {
                lineRepository.delete(orphan);
            }
        }

        recalcDeadline(doc);
        return toFlatDto(doc);
    }

    private void updateApprovedPartial(DocMoveOrder doc, UpdateMoveOrderRequest request, String author) {
        if (request.getLines() == null) return;

        List<RegMoveOrderLine> existing = lineRepository.findByDocUid(doc.getUid());
        Map<UUID, RegMoveOrderLine> existingMap = existing.stream()
                .collect(Collectors.toMap(RegMoveOrderLine::getUid, l -> l));

        Set<UUID> seen = new HashSet<>();

        for (UpdateMoveOrderRequest.UpdateMoveOrderLineRequest lr : request.getLines()) {
            if (lr.getUid() == null || !existingMap.containsKey(lr.getUid())) {
                throw new RuntimeException("В согласованном заказе нельзя добавлять новые строки");
            }
            RegMoveOrderLine line = existingMap.get(lr.getUid());
            seen.add(lr.getUid());

            if (lr.getQuantity() != null) {
                int accepted = line.getAcceptedQuantity() != null ? line.getAcceptedQuantity() : line.getQuantity();
                if (lr.getQuantity() > accepted) {
                    throw new RuntimeException("В согласованном заказе количество можно только уменьшать (макс. " + accepted + ")");
                }
                if (lr.getQuantity() < 0) {
                    throw new RuntimeException("Количество не может быть отрицательным");
                }
                if (!Objects.equals(line.getQuantity(), lr.getQuantity())) {
                    logEvent(doc.getUid(), line.getUid(), "UPDATE",
                            "Строка: Количество " + line.getQuantity() + " → " + lr.getQuantity(),
                            "Количество", String.valueOf(line.getQuantity()), String.valueOf(lr.getQuantity()), author);
                    line.setQuantity(lr.getQuantity());
                    syncTransferLineQuantity(doc, line);
                }
            }

            if (lr.getCancelled() != null && !Objects.equals(line.getCancelled(), lr.getCancelled())) {
                logEvent(doc.getUid(), line.getUid(), "UPDATE",
                        "Строка: Отмена " + line.getCancelled() + " → " + lr.getCancelled(),
                        "Отменено", String.valueOf(line.getCancelled()), String.valueOf(lr.getCancelled()), author);
                line.setCancelled(lr.getCancelled());

                if (Boolean.TRUE.equals(lr.getCancelled())) {
                    boolean hasActiveOther = existing.stream()
                            .filter(l -> !Objects.equals(l.getUid(), line.getUid()))
                            .anyMatch(l -> !Boolean.TRUE.equals(l.getCancelled()));

                    if (hasActiveOther) {
                        removeTransferLine(doc, line);
                    } else {
                        cancelLinkedTransfers(doc, author);
                    }
                }
            }

            lineRepository.save(line);
        }

        if (seen.size() != existingMap.size()) {
            throw new RuntimeException("В согласованном заказе нельзя удалять строки");
        }

        boolean hasActive = lineRepository.findByDocUid(doc.getUid()).stream()
                .anyMatch(l -> !Boolean.TRUE.equals(l.getCancelled()));
        if (!hasActive) {
            cancelLinkedTransfers(doc, author);
        }

        docRepository.save(doc);
    }

    private void syncTransferLineQuantity(DocMoveOrder doc, RegMoveOrderLine line) {
        if (line.getTransfer() == null) return;
        List<RegTransferLine> tls = transferLineRepository.findByTransferUid(line.getTransfer().getUid());
        for (RegTransferLine tl : tls) {
            if (tl.getMaterial() != null && line.getMaterial() != null
                    && Objects.equals(tl.getMaterial().getUid(), line.getMaterial().getUid())) {
                tl.setQuantity(line.getQuantity());
                tl.setUpdatedAt(LocalDateTime.now());
                transferLineRepository.save(tl);
            }
        }
    }

    private void removeTransferLine(DocMoveOrder doc, RegMoveOrderLine line) {
        if (line.getTransfer() == null) return;
        List<RegTransferLine> tls = transferLineRepository.findByTransferUid(line.getTransfer().getUid());
        List<RegTransferLine> toDelete = tls.stream()
                .filter(tl -> tl.getMaterial() != null && line.getMaterial() != null
                        && Objects.equals(tl.getMaterial().getUid(), line.getMaterial().getUid()))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            transferLineRepository.deleteAll(toDelete);
        }
    }

    private void cancelLinkedTransfers(DocMoveOrder doc, String author) {
        List<DocTransfer> transfers = docTransferRepository
                .findByDocBaseUidAndDocBaseType(doc.getUid(), DOC_BASE_TYPE_MOVE_ORDER);
        boolean anyCancelled = false;
        for (DocTransfer t : transfers) {
            if (TRANSFER_CANCELLED.equals(t.getStatus())) continue;
            t.setStatus(TRANSFER_CANCELLED);
            t.setCancelled(Boolean.TRUE);
            t.setUpdatedAt(LocalDateTime.now());
            docTransferRepository.save(t);
            anyCancelled = true;
        }
        if (anyCancelled) {
            logEvent(doc.getUid(), null, "TRANSFERS_CANCELLED",
                    "Все связанные документы перемещения отменены (снимок сохранён)",
                    null, null, null, author);
        }
    }

    private boolean hasLinkedTransferInWork(DocMoveOrder doc) {
        List<DocTransfer> transfers = docTransferRepository
                .findByDocBaseUidAndDocBaseType(doc.getUid(), DOC_BASE_TYPE_MOVE_ORDER);
        return transfers.stream().anyMatch(t -> {
            String s = t.getStatus();
            return !TRANSFER_DRAFT.equals(s) && !TRANSFER_CANCELLED.equals(s);
        });
    }

    private void updateLineDraft(RegMoveOrderLine line, UpdateMoveOrderRequest.UpdateMoveOrderLineRequest lr, String author) {
        if (lr.getMaterialUid() != null && (line.getMaterial() == null
                || !Objects.equals(line.getMaterial().getUid(), lr.getMaterialUid()))) {
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Номенклатура изменена",
                    "Номенклатура",
                    line.getMaterial() != null ? line.getMaterial().getUid().toString() : null,
                    lr.getMaterialUid().toString(), author);
            line.setMaterial(materialRepository.getReferenceById(lr.getMaterialUid()));
        }
        if (lr.getQuantity() != null && !Objects.equals(line.getQuantity(), lr.getQuantity())) {
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Количество " + line.getQuantity() + " → " + lr.getQuantity(),
                    "Количество", String.valueOf(line.getQuantity()), String.valueOf(lr.getQuantity()), author);
            line.setQuantity(lr.getQuantity());
        }
        if (lr.getStockFromUid() != null && !Objects.equals(line.getStockFromUid(), lr.getStockFromUid())) {
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Склад-отправитель " + line.getStockFromUid() + " → " + lr.getStockFromUid(),
                    "Склад-отправитель", line.getStockFromUid(), lr.getStockFromUid(), author);
            line.setStockFromUid(lr.getStockFromUid());
        }
        if (lr.getPriority() != null && !Objects.equals(line.getPriority(), lr.getPriority())) {
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Приоритет " + line.getPriority() + " → " + lr.getPriority(),
                    "Приоритет", line.getPriority(), lr.getPriority(), author);
            line.setPriority(lr.getPriority());
        }
        if (lr.getDueDate() != null && !Objects.equals(line.getDueDate(), lr.getDueDate())) {
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Срок исполнения " + line.getDueDate() + " → " + lr.getDueDate(),
                    "Срок исполнения", String.valueOf(line.getDueDate()), String.valueOf(lr.getDueDate()), author);
            line.setDueDate(lr.getDueDate());
        }
        if (lr.getComment() != null && !Objects.equals(line.getComment(), lr.getComment())) {
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Комментарий " + line.getComment() + " → " + lr.getComment(),
                    "Комментарий", line.getComment(), lr.getComment(), author);
            line.setComment(lr.getComment());
        }
        if (lr.getCancelled() != null && !Objects.equals(line.getCancelled(), lr.getCancelled())) {
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Отмена " + line.getCancelled() + " → " + lr.getCancelled(),
                    "Отменено", String.valueOf(line.getCancelled()), String.valueOf(lr.getCancelled()), author);
            line.setCancelled(lr.getCancelled());
        }
    }

    // ==================== SUBMIT FOR APPROVAL (без создания transfer'ов) ====================

    @Transactional
    public MoveOrderFlatDto submitForApproval(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (!STATUS_DRAFT.equals(doc.getStatus())) {
            throw new RuntimeException("На согласование можно отправить только записанный заказ");
        }
        if (!Boolean.TRUE.equals(doc.getUseApproval())) {
            throw new RuntimeException("Режим согласования выключен");
        }

        List<RegMoveOrderLine> lines = lineRepository.findByDocUid(uid);
        boolean hasActive = lines.stream().anyMatch(l -> !Boolean.TRUE.equals(l.getCancelled()));
        if (!hasActive) {
            throw new RuntimeException("Нет активных строк для согласования");
        }

        for (RegMoveOrderLine l : lines) {
            SprMaterial m = l.getMaterial();
            if (m != null) {
                l.setMaterialNameSnapshot(m.getNameMaterial());
                l.setMaterialArticleSnapshot(m.getArticle());
                l.setMaterialCodeSnapshot(m.getCodeMaterial());
            }
            Integer available = null;
            if (m != null && l.getStockFromUid() != null) {
                available = getAvailableLive(l.getStockFromUid(), m.getUid());
            }
            l.setAvailableSnapshot(available);
            l.setAcceptedQuantity(l.getQuantity());
            lineRepository.save(l);
        }

        doc.setStatus(STATUS_ON_APPROVAL);
        docRepository.save(doc);

        logEvent(doc.getUid(), null, "SUBMIT_APPROVAL",
                "Заказ отправлен на согласование: код " + doc.getCode(),
                null, null, null, author);

        return toFlatDto(doc);
    }

    // ==================== APPROVE (создаёт документы перемещения) ====================

    @Transactional
    public MoveOrderFlatDto approve(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (!STATUS_ON_APPROVAL.equals(doc.getStatus())) {
            throw new RuntimeException("Согласовать можно только заказ в статусе «На согласовании»");
        }

        List<RegMoveOrderLine> lines = lineRepository.findByDocUid(uid);
        List<RegMoveOrderLine> activeLines = lines.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getCancelled()))
                .collect(Collectors.toList());
        if (activeLines.isEmpty()) {
            throw new RuntimeException("Нет активных строк для согласования");
        }

        for (RegMoveOrderLine l : lines) {
            SprMaterial m = l.getMaterial();
            if (m != null && l.getStockFromUid() != null) {
                l.setAvailableSnapshot(getAvailableLive(l.getStockFromUid(), m.getUid()));
                lineRepository.save(l);
            }
        }

        doc.setStatus(STATUS_APPROVED);
        docRepository.save(doc);

        // Создаём документы перемещения в состоянии «Записано» (DRAFT)
        createTransfersForApproved(doc, author);

        logEvent(doc.getUid(), null, "APPROVE",
                "Заказ согласован, созданы документы перемещения: код " + doc.getCode(),
                null, null, null, author);

        return toFlatDto(doc);
    }

    // ==================== REJECT (до approve — transfer'ов ещё нет) ====================

    @Transactional
    public MoveOrderFlatDto reject(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (!STATUS_ON_APPROVAL.equals(doc.getStatus())) {
            throw new RuntimeException("Отклонить можно только заказ в статусе «На согласовании»");
        }

        doc.setStatus(STATUS_REJECTED);
        docRepository.save(doc);

        logEvent(doc.getUid(), null, "REJECT",
                "Заказ отклонён: код " + doc.getCode(),
                null, null, null, author);

        return toFlatDto(doc);
    }

    // ==================== TAKE TO WORK (без согласования, создаёт transfer'ы) ====================

    @Transactional
    public MoveOrderFlatDto takeToWork(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (!STATUS_DRAFT.equals(doc.getStatus())) {
            throw new RuntimeException("В работу можно передать только записанный заказ");
        }
        if (Boolean.TRUE.equals(doc.getUseApproval())) {
            throw new RuntimeException("Заказ использует режим согласования — сначала согласуйте");
        }

        List<RegMoveOrderLine> lines = lineRepository.findByDocUid(uid);
        List<RegMoveOrderLine> activeLines = lines.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getCancelled()))
                .collect(Collectors.toList());
        if (activeLines.isEmpty()) {
            throw new RuntimeException("Нет активных строк для передачи в работу");
        }

        for (RegMoveOrderLine l : lines) {
            SprMaterial m = l.getMaterial();
            if (m != null) {
                l.setMaterialNameSnapshot(m.getNameMaterial());
                l.setMaterialArticleSnapshot(m.getArticle());
                l.setMaterialCodeSnapshot(m.getCodeMaterial());
            }
            Integer available = null;
            if (m != null && l.getStockFromUid() != null) {
                available = getAvailableLive(l.getStockFromUid(), m.getUid());
            }
            l.setAvailableSnapshot(available);
            l.setAcceptedQuantity(l.getQuantity());
            lineRepository.save(l);
        }

        doc.setStatus(STATUS_IN_PROGRESS);
        docRepository.save(doc);

        createTransfersForApproved(doc, author);

        logEvent(doc.getUid(), null, "TAKE_TO_WORK",
                "Заказ передан в работу, созданы документы перемещения: код " + doc.getCode(),
                null, null, null, author);

        return toFlatDto(doc);
    }

    // ==================== CANCEL ====================

    @Transactional
    public MoveOrderFlatDto cancel(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (STATUS_CANCELLED.equals(doc.getStatus())) {
            throw new RuntimeException("Заказ уже отменён");
        }
        if (STATUS_CLOSED.equals(doc.getStatus())) {
            throw new RuntimeException("Закрытый заказ нельзя отменить");
        }
        if (STATUS_IN_PROGRESS.equals(doc.getStatus())) {
            throw new RuntimeException("Заказ в работе нельзя отменить");
        }
        if (hasLinkedTransferInWork(doc)) {
            throw new RuntimeException("Нельзя отменить заказ: хотя бы один документ перемещения уже взят в работу");
        }

        // Если есть созданные документы перемещения (из APPROVED) — отменяем их (не удаляем)
        cancelLinkedTransfers(doc, author);

        doc.setStatus(STATUS_CANCELLED);
        docRepository.save(doc);

        logEvent(doc.getUid(), null, "CANCEL",
                "Отмена заказа: код " + doc.getCode(),
                null, null, null, author);

        return toFlatDto(doc);
    }

    // ==================== DELETE ====================

    @Transactional
    public void delete(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (STATUS_IN_PROGRESS.equals(doc.getStatus())) {
            throw new RuntimeException("Заказ в работе нельзя удалить");
        }
        if (STATUS_CLOSED.equals(doc.getStatus())) {
            throw new RuntimeException("Закрытый заказ нельзя удалить");
        }

        rollbackTransfers(doc);

        logEvent(doc.getUid(), null, "DELETE",
                "Удаление заказа: код " + doc.getCode(),
                null, String.valueOf(doc.getCode()), null, author);

        docRepository.delete(doc);
    }

    // ==================== EVENTS ====================

    public List<MoveOrderEventLogDto> getEvents(UUID uid) {
        return eventLogRepository.findByDocUidOrderByCreatedAtDesc(uid).stream()
                .map(this::toEventDto)
                .collect(Collectors.toList());
    }

    public List<MoveOrderEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDto)
                .collect(Collectors.toList());
    }

    // ==================== TRANSFERS ====================

    private void createTransfersForApproved(DocMoveOrder doc, String author) {
        if (doc == null || doc.getUid() == null) return;

        String stockTo = doc.getStockToUid();
        if (stockTo == null || stockTo.isBlank()) return;

        List<DocTransfer> existing = docTransferRepository
                .findByDocBaseUidAndDocBaseType(doc.getUid(), DOC_BASE_TYPE_MOVE_ORDER);
        if (!existing.isEmpty()) return;

        List<RegMoveOrderLine> allLines = lineRepository.findByDocUid(doc.getUid());
        List<RegMoveOrderLine> activeLines = allLines.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getCancelled()))
                .filter(l -> l.getStockFromUid() != null && !l.getStockFromUid().isBlank())
                .collect(Collectors.toList());

        Map<String, List<RegMoveOrderLine>> groupedByFrom = activeLines.stream()
                .collect(Collectors.groupingBy(RegMoveOrderLine::getStockFromUid, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<RegMoveOrderLine>> e : groupedByFrom.entrySet()) {
            String stockFrom = e.getKey();
            List<RegMoveOrderLine> group = e.getValue();

            DocTransfer transfer = DocTransfer.builder()
                    .uid(UUID.randomUUID())
                    .code(nextTransferCode())
                    .docDatetime(LocalDateTime.now())
                    .docBaseUid(doc.getUid())
                    .docBaseType(DOC_BASE_TYPE_MOVE_ORDER)
                    .stockFromUid(stockFrom)
                    .stockToUid(stockTo)
                    .status(TRANSFER_DRAFT)
                    .priority("MEDIUM")
                    .dueDate(doc.getDeadline())
                    .agreed(Boolean.TRUE)
                    .cancelled(Boolean.FALSE)
                    .operatorUid(null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            transfer = docTransferRepository.save(transfer);

            for (RegMoveOrderLine l : group) {
                if (l.getMaterial() == null) continue;
                int qty = l.getQuantity() != null ? l.getQuantity() : 0;
                RegTransferLine tl = RegTransferLine.builder()
                        .uid(UUID.randomUUID())
                        .transfer(transfer)
                        .material(l.getMaterial())
                        .quantity(qty)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                transferLineRepository.save(tl);

                l.setTransfer(transfer);
                lineRepository.save(l);
            }
        }

        logEvent(doc.getUid(), null, "TRANSFERS_CREATED",
                "Созданы документы перемещения по заказу: код " + doc.getCode(),
                null, null, null, author);
    }

    private void rollbackTransfers(DocMoveOrder doc) {
        if (doc == null || doc.getUid() == null) return;

        List<DocTransfer> transfers = docTransferRepository
                .findByDocBaseUidAndDocBaseType(doc.getUid(), DOC_BASE_TYPE_MOVE_ORDER);

        List<RegMoveOrderLine> lines = lineRepository.findByDocUid(doc.getUid());
        for (RegMoveOrderLine l : lines) {
            if (l.getTransfer() != null) {
                l.setTransfer(null);
                lineRepository.save(l);
            }
        }

        for (DocTransfer t : transfers) {
            List<RegTransferLine> tls = transferLineRepository.findByTransferUid(t.getUid());
            transferLineRepository.deleteAll(tls);
            docTransferRepository.delete(t);
        }
    }

    // ==================== PRIVATE ====================

    private void recalcDeadline(DocMoveOrder doc) {
        List<RegMoveOrderLine> lines = lineRepository.findByDocUid(doc.getUid());
        LocalDateTime maxDue = lines.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getCancelled()))
                .map(RegMoveOrderLine::getDueDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        doc.setDeadline(maxDue);
        docRepository.save(doc);
    }

    private Integer nextTransferCode() {
        Integer max = docTransferRepository.findMaxCode();
        return max != null ? max + 1 : 1;
    }

    private void logEvent(UUID docUid, UUID lineUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        MoveOrderEventLog log = MoveOrderEventLog.builder()
                .uid(UUID.randomUUID())
                .docUid(docUid)
                .lineUid(lineUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(log);
    }

    private String resolveStockName(String stockUid) {
        if (stockUid == null || stockUid.isBlank()) return null;
        if ("MAIN".equals(stockUid)) return "Основной склад";
        return stationRepository.findByUid(stockUid)
                .map(Station::getName)
                .orElse(stockUid);
    }

    private Integer getAvailableLive(String stockUid, UUID materialUid) {
        if (stockUid == null || materialUid == null) return null;

        Integer free = stockRegisterService.getAvailableQuantitySafe(materialUid, stockUid);
        if (free == null) return null;

        Integer critical = null;
        try {
            critical = regStockLevelControlRepository
                    .findByStationUidAndMaterialUid(stockUid, materialUid)
                    .map(RegStockLevelControl::getCriticalStock)
                    .orElse(null);
        } catch (Exception ignored) {
            critical = null;
        }

        return critical != null ? Math.max(0, free - critical) : free;
    }

    private String resolveResponsible(String stockUid) {
        if (stockUid == null || "MAIN".equals(stockUid)) return null;
        return stationRepository.findByUid(stockUid)
                .map(station -> {
                    Long workshopId = station.getWorkshop() != null ? station.getWorkshop().getId() : null;
                    if (workshopId == null) return null;
                    return employeeRepository.findAll().stream()
                            .filter(e -> e.getWorkshop() != null && e.getWorkshop().getId().equals(workshopId))
                            .findFirst()
                            .map(this::buildFio)
                            .orElse(null);
                })
                .orElse(null);
    }

    private String buildFio(Employee e) {
        StringBuilder sb = new StringBuilder();
        if (e.getLastName() != null) sb.append(e.getLastName());
        if (e.getFirstName() != null) sb.append(sb.length() > 0 ? " " : "").append(e.getFirstName());
        if (e.getMiddleName() != null) sb.append(sb.length() > 0 ? " " : "").append(e.getMiddleName());
        return sb.toString();
    }

    private MoveOrderFlatDto toFlatDto(DocMoveOrder doc) {
        List<RegMoveOrderLine> lines = lineRepository.findByDocUid(doc.getUid());
        boolean isApproved = STATUS_APPROVED.equals(doc.getStatus())
                || STATUS_IN_PROGRESS.equals(doc.getStatus())
                || STATUS_CLOSED.equals(doc.getStatus());

        List<MoveOrderLineDto> lineDtos = lines.stream()
                .map(l -> toLineDto(l, isApproved))
                .collect(Collectors.toList());

        return MoveOrderFlatDto.builder()
                .uid(doc.getUid())
                .code(doc.getCode())
                .docDatetime(doc.getDocDatetime())
                .stockToUid(doc.getStockToUid())
                .stockToName(resolveStockName(doc.getStockToUid()))
                .deadline(doc.getDeadline())
                .comment(doc.getComment())
                .useApproval(doc.getUseApproval())
                .status(doc.getStatus())
                .lines(lineDtos)
                .build();
    }

    private MoveOrderLineDto toLineDto(RegMoveOrderLine l, boolean isApproved) {
        SprMaterial m = l.getMaterial();

        String materialName;
        String materialArticle;
        Integer materialCode;
        Integer available;

        if (isApproved) {
            materialName = l.getMaterialNameSnapshot();
            materialArticle = l.getMaterialArticleSnapshot();
            materialCode = l.getMaterialCodeSnapshot();
            available = l.getAvailableSnapshot();
        } else {
            materialName = m != null ? m.getNameMaterial() : null;
            materialArticle = m != null ? m.getArticle() : null;
            materialCode = m != null ? m.getCodeMaterial() : null;
            available = (m != null && l.getStockFromUid() != null)
                    ? getAvailableLive(l.getStockFromUid(), m.getUid())
                    : null;
        }

        return MoveOrderLineDto.builder()
                .uid(l.getUid())
                .docUid(l.getDoc() != null ? l.getDoc().getUid() : null)
                .materialUid(m != null ? m.getUid() : null)
                .materialName(materialName)
                .materialArticle(materialArticle)
                .materialCode(materialCode)
                .quantity(l.getQuantity())
                .acceptedQuantity(l.getAcceptedQuantity())
                .stockFromUid(l.getStockFromUid())
                .stockFromName(resolveStockName(l.getStockFromUid()))
                .available(available)
                .priority(l.getPriority())
                .dueDate(l.getDueDate())
                .comment(l.getComment())
                .status(l.getStatus())
                .shippingAt(l.getShippingAt())
                .shippedAt(l.getShippedAt())
                .loadingAt(l.getLoadingAt())
                .closedAt(l.getClosedAt())
                .cancelled(l.getCancelled())
                .transferUid(l.getTransfer() != null ? l.getTransfer().getUid() : null)
                .transferCode(l.getTransfer() != null ? l.getTransfer().getCode() : null)
                .responsibleName(resolveResponsible(l.getStockFromUid()))
                .build();
    }

    private MoveOrderEventLogDto toEventDto(MoveOrderEventLog e) {
        return MoveOrderEventLogDto.builder()
                .uid(e.getUid())
                .docUid(e.getDocUid())
                .lineUid(e.getLineUid())
                .eventType(e.getEventType())
                .eventDescription(e.getEventDescription())
                .fieldName(e.getFieldName())
                .oldValue(e.getOldValue())
                .newValue(e.getNewValue())
                .author(e.getAuthor())
                .source(e.getSource())
                .createdAt(e.getCreatedAt())
                .build();
    }
}