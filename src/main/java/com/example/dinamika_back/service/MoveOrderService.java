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

    public static final String STATUS_DRAFT      = "DRAFT";
    public static final String STATUS_POSTED     = "POSTED";
    public static final String STATUS_ACCEPTED   = "ACCEPTED";
    public static final String STATUS_CANCELLED  = "CANCELLED";

    public static final String LINE_NOT_STARTED  = "NOT_STARTED";
    public static final String LINE_SHIPPING     = "SHIPPING";
    public static final String LINE_SHIPPED      = "SHIPPED";
    public static final String LINE_CLOSED       = "CLOSED";
    public static final String LINE_CANCELLED    = "CANCELLED";

    private static final String TRANSFER_DRAFT   = "DRAFT";
    private static final String TRANSFER_POSTED  = "POSTED";
    private static final String TRANSFER_CLOSED  = "CLOSED";

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

    // ==================== GENERATE CODE ====================

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
        syncTransfersForDoc(doc, author);

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

        if (STATUS_POSTED.equals(doc.getStatus())) {
            throw new RuntimeException("Проведённый заказ нельзя редактировать");
        }
        if (STATUS_CANCELLED.equals(doc.getStatus())) {
            throw new RuntimeException("Отменённый заказ нельзя редактировать");
        }

        boolean isDraft = STATUS_DRAFT.equals(doc.getStatus());
        boolean isAccepted = STATUS_ACCEPTED.equals(doc.getStatus());

        if (isDraft) {
            if (request.getDocDatetime() != null) doc.setDocDatetime(request.getDocDatetime());
            if (request.getStockToUid() != null) doc.setStockToUid(request.getStockToUid());
            if (request.getComment() != null) doc.setComment(request.getComment());
            docRepository.save(doc);
        }

        if (request.getLines() != null) {
            List<RegMoveOrderLine> existing = lineRepository.findByDocUid(uid);
            Map<UUID, RegMoveOrderLine> existingMap = existing.stream()
                    .collect(Collectors.toMap(RegMoveOrderLine::getUid, l -> l));

            for (UpdateMoveOrderRequest.UpdateMoveOrderLineRequest lr : request.getLines()) {
                if (lr.getUid() != null && existingMap.containsKey(lr.getUid())) {
                    RegMoveOrderLine line = existingMap.get(lr.getUid());
                    if (isDraft) {
                        updateLineDraft(line, lr, author);
                    } else if (isAccepted) {
                        updateLineAccepted(line, lr, author);
                    }
                    lineRepository.save(line);
                    existingMap.remove(lr.getUid());
                } else {
                    if (!isDraft) {
                        throw new RuntimeException("В акцептованном заказе нельзя добавлять новые строки");
                    }
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

            if (isDraft) {
                for (RegMoveOrderLine orphan : existingMap.values()) {
                    lineRepository.delete(orphan);
                }
            } else if (isAccepted && !existingMap.isEmpty()) {
                throw new RuntimeException("В акцептованном заказе нельзя удалять строки");
            }
        }

        recalcDeadline(doc);

        if (isDraft) {
            syncTransfersForDoc(doc, author);
        }

        return toFlatDto(doc);
    }

    /** Полное редактирование строки — только DRAFT. */
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
        if (lr.getStatus() != null && !Objects.equals(line.getStatus(), lr.getStatus())) {
            changeLineStatus(line, lr.getStatus(), author);
        }
        if (lr.getCancelled() != null && !Objects.equals(line.getCancelled(), lr.getCancelled())) {
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Отмена " + line.getCancelled() + " → " + lr.getCancelled(),
                    "Отменено", String.valueOf(line.getCancelled()), String.valueOf(lr.getCancelled()), author);
            line.setCancelled(lr.getCancelled());
        }
    }

    /**
     * Ограниченное редактирование в ACCEPTED:
     * — quantity: 1 <= newQty <= acceptedQuantity;
     * — comment: свободно;
     * — cancelled: свободно.
     * Всё остальное — запрещено.
     */
    private void updateLineAccepted(RegMoveOrderLine line, UpdateMoveOrderRequest.UpdateMoveOrderLineRequest lr, String author) {
        if (lr.getMaterialUid() != null && (line.getMaterial() == null
                || !Objects.equals(line.getMaterial().getUid(), lr.getMaterialUid()))) {
            throw new RuntimeException("В акцептованном заказе нельзя менять номенклатуру");
        }
        if (lr.getStockFromUid() != null && !Objects.equals(line.getStockFromUid(), lr.getStockFromUid())) {
            throw new RuntimeException("В акцептованном заказе нельзя менять склад-отправитель");
        }
        if (lr.getPriority() != null && !Objects.equals(line.getPriority(), lr.getPriority())) {
            throw new RuntimeException("В акцептованном заказе нельзя менять приоритет");
        }
        if (lr.getDueDate() != null && !Objects.equals(line.getDueDate(), lr.getDueDate())) {
            throw new RuntimeException("В акцептованном заказе нельзя менять срок исполнения");
        }
        if (lr.getStatus() != null && !Objects.equals(line.getStatus(), lr.getStatus())) {
            throw new RuntimeException("В акцептованном заказе нельзя менять статус строки вручную");
        }

        if (lr.getQuantity() != null && !Objects.equals(line.getQuantity(), lr.getQuantity())) {
            int newQty = lr.getQuantity() != null ? lr.getQuantity() : 0;
            int currentQty = line.getQuantity() != null ? line.getQuantity() : 0;
            Integer acceptedQtyObj = line.getAcceptedQuantity();
            int acceptedQty = acceptedQtyObj != null ? acceptedQtyObj : currentQty;

            if (newQty < 1) {
                throw new RuntimeException("Количество должно быть не меньше 1 (для отмены строки используйте флаг «Отменено»)");
            }
            if (newQty > acceptedQty) {
                throw new RuntimeException("В акцептованном заказе количество можно только уменьшать (максимум " + acceptedQty + ")");
            }
            logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                    "Строка: Количество " + currentQty + " → " + newQty,
                    "Количество", String.valueOf(currentQty), String.valueOf(newQty), author);
            line.setQuantity(newQty);
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

    private void changeLineStatus(RegMoveOrderLine line, String newStatus, String author) {
        String oldStatus = line.getStatus();
        logEvent(line.getDoc().getUid(), line.getUid(), "UPDATE",
                "Строка: Статус " + oldStatus + " → " + newStatus,
                "Статус", oldStatus, newStatus, author);
        line.setStatus(newStatus);

        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case LINE_SHIPPING -> line.setShippingAt(now);
            case LINE_SHIPPED  -> line.setShippedAt(now);
            case LINE_CLOSED   -> line.setClosedAt(now);
        }
    }

    // ==================== POST (DRAFT → POSTED, «Проведён») ====================

    @Transactional
    public MoveOrderFlatDto post(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (!STATUS_DRAFT.equals(doc.getStatus())) {
            throw new RuntimeException("Провести можно только черновик");
        }

        List<RegMoveOrderLine> lines = lineRepository.findByDocUid(uid);
        boolean hasActive = lines.stream().anyMatch(l -> !Boolean.TRUE.equals(l.getCancelled()));
        if (!hasActive) {
            throw new RuntimeException("Нет активных строк для проведения");
        }

        syncTransfersForDoc(doc, author);

        List<DocTransfer> transfers = docTransferRepository
                .findByDocBaseUidAndDocBaseType(doc.getUid(), DOC_BASE_TYPE_MOVE_ORDER);
        if (transfers.isEmpty()) {
            throw new RuntimeException("По заказу не создано ни одного перемещения");
        }

        doc.setStatus(STATUS_POSTED);
        docRepository.save(doc);

        logEvent(doc.getUid(), null, "POST",
                "Проведение заказа: код " + doc.getCode(),
                null, null, null, author);

        return toFlatDto(doc);
    }

    // ==================== ACCEPT (POSTED → ACCEPTED, «Акцептован») ====================

    @Transactional
    public MoveOrderFlatDto accept(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (!STATUS_POSTED.equals(doc.getStatus())) {
            throw new RuntimeException("Акцептовать можно только проведённый заказ");
        }

        List<RegMoveOrderLine> lines = lineRepository.findByDocUid(uid);
        List<RegMoveOrderLine> activeLines = lines.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getCancelled()))
                .collect(Collectors.toList());
        if (activeLines.isEmpty()) {
            throw new RuntimeException("Нет активных строк для акцепта");
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

            // Акцептированное количество = текущее
            l.setAcceptedQuantity(l.getQuantity());

            lineRepository.save(l);
        }

        doc.setStatus(STATUS_ACCEPTED);
        docRepository.save(doc);

        logEvent(doc.getUid(), null, "ACCEPT",
                "Акцепт заказа: код " + doc.getCode(),
                null, null, null, author);

        return toFlatDto(doc);
    }

    // ==================== DELETE ====================

    @Transactional
    public void delete(UUID uid, String author) {
        DocMoveOrder doc = docRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + uid));

        if (STATUS_POSTED.equals(doc.getStatus())) {
            throw new RuntimeException("Проведённый заказ нельзя удалить");
        }
        if (STATUS_ACCEPTED.equals(doc.getStatus())) {
            throw new RuntimeException("Акцептованный заказ нельзя удалить");
        }

        List<DocTransfer> transfers = docTransferRepository
                .findByDocBaseUidAndDocBaseType(doc.getUid(), DOC_BASE_TYPE_MOVE_ORDER);
        for (DocTransfer t : transfers) {
            List<RegTransferLine> tls = transferLineRepository.findByTransferUid(t.getUid());
            transferLineRepository.deleteAll(tls);
            docTransferRepository.delete(t);
        }

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

    // ==================== SYNC TRANSFERS ====================

    private void syncTransfersForDoc(DocMoveOrder doc, String author) {
        if (doc == null || doc.getUid() == null) return;

        String stockTo = doc.getStockToUid();
        if (stockTo == null || stockTo.isBlank()) return;

        List<RegMoveOrderLine> allLines = lineRepository.findByDocUid(doc.getUid());
        List<RegMoveOrderLine> activeLines = allLines.stream()
                .filter(l -> !Boolean.TRUE.equals(l.getCancelled()))
                .filter(l -> l.getStockFromUid() != null && !l.getStockFromUid().isBlank())
                .collect(Collectors.toList());

        List<DocTransfer> existing = docTransferRepository
                .findByDocBaseUidAndDocBaseType(doc.getUid(), DOC_BASE_TYPE_MOVE_ORDER);
        Map<String, DocTransfer> existingByFrom = new LinkedHashMap<>();
        for (DocTransfer t : existing) {
            existingByFrom.put(t.getStockFromUid(), t);
        }

        Map<String, List<RegMoveOrderLine>> groupedByFrom = activeLines.stream()
                .collect(Collectors.groupingBy(RegMoveOrderLine::getStockFromUid, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, DocTransfer> e : existingByFrom.entrySet()) {
            String stockFrom = e.getKey();
            DocTransfer t = e.getValue();
            if (!groupedByFrom.containsKey(stockFrom)) {
                List<RegTransferLine> tls = transferLineRepository.findByTransferUid(t.getUid());
                transferLineRepository.deleteAll(tls);
                docTransferRepository.delete(t);
            }
        }

        for (Map.Entry<String, List<RegMoveOrderLine>> e : groupedByFrom.entrySet()) {
            String stockFrom = e.getKey();
            List<RegMoveOrderLine> group = e.getValue();

            DocTransfer transfer = existingByFrom.get(stockFrom);
            if (transfer == null) {
                transfer = DocTransfer.builder()
                        .uid(UUID.randomUUID())
                        .code(nextTransferCode())
                        .docDatetime(LocalDateTime.now())
                        .docBaseUid(doc.getUid())
                        .docBaseType(DOC_BASE_TYPE_MOVE_ORDER)
                        .stockFromUid(stockFrom)
                        .stockToUid(stockTo)
                        .status(TRANSFER_DRAFT)
                        .build();
                transfer = docTransferRepository.save(transfer);
            } else {
                if (!Objects.equals(transfer.getStockToUid(), stockTo)) {
                    transfer.setStockToUid(stockTo);
                    docTransferRepository.save(transfer);
                }
            }

            List<RegTransferLine> tLines = transferLineRepository.findByTransferUid(transfer.getUid());
            Map<UUID, RegTransferLine> tByMaterial = new LinkedHashMap<>();
            for (RegTransferLine tl : tLines) {
                if (tl.getMaterial() != null) tByMaterial.put(tl.getMaterial().getUid(), tl);
            }

            Set<UUID> seen = new HashSet<>();
            for (RegMoveOrderLine l : group) {
                if (l.getMaterial() == null) continue;
                UUID mUid = l.getMaterial().getUid();
                seen.add(mUid);

                RegTransferLine tl = tByMaterial.get(mUid);
                int qty = l.getQuantity() != null ? l.getQuantity() : 0;
                if (tl == null) {
                    RegTransferLine created = RegTransferLine.builder()
                            .uid(UUID.randomUUID())
                            .transfer(transfer)
                            .material(l.getMaterial())
                            .quantity(qty)
                            .build();
                    transferLineRepository.save(created);
                } else if (!Objects.equals(tl.getQuantity(), qty)) {
                    tl.setQuantity(qty);
                    transferLineRepository.save(tl);
                }

                if (l.getTransfer() == null || !Objects.equals(l.getTransfer().getUid(), transfer.getUid())) {
                    l.setTransfer(transfer);
                    lineRepository.save(l);
                }
            }

            for (Map.Entry<UUID, RegTransferLine> te : tByMaterial.entrySet()) {
                if (!seen.contains(te.getKey())) {
                    transferLineRepository.delete(te.getValue());
                }
            }

            List<RegTransferLine> after = transferLineRepository.findByTransferUid(transfer.getUid());
            if (after.isEmpty()) {
                docTransferRepository.delete(transfer);
            }
        }

        Set<UUID> aliveTransferUids = docTransferRepository
                .findByDocBaseUidAndDocBaseType(doc.getUid(), DOC_BASE_TYPE_MOVE_ORDER)
                .stream().map(DocTransfer::getUid).collect(Collectors.toSet());
        for (RegMoveOrderLine l : allLines) {
            if (l.getTransfer() != null && !aliveTransferUids.contains(l.getTransfer().getUid())) {
                l.setTransfer(null);
                lineRepository.save(l);
            }
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

    /**
     * «Доступно к перемещению» = свободный остаток на складе-отправителе
     * минус критический остаток КУО для пары (station, material).
     * Если КУО не задан (criticalStock == null) — возвращаем свободный остаток.
     * Это то же значение, что показывает попап «Доступность номенклатуры»
     * в колонке «К перемещению» (toMoveMax).
     */
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
                            .map(e -> buildFio(e))
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
        boolean isAccepted = STATUS_ACCEPTED.equals(doc.getStatus());

        List<MoveOrderLineDto> lineDtos = lines.stream()
                .map(l -> toLineDto(l, isAccepted))
                .collect(Collectors.toList());

        return MoveOrderFlatDto.builder()
                .uid(doc.getUid())
                .code(doc.getCode())
                .docDatetime(doc.getDocDatetime())
                .stockToUid(doc.getStockToUid())
                .stockToName(resolveStockName(doc.getStockToUid()))
                .deadline(doc.getDeadline())
                .comment(doc.getComment())
                .status(doc.getStatus())
                .lines(lineDtos)
                .build();
    }

    private MoveOrderLineDto toLineDto(RegMoveOrderLine l, boolean isAccepted) {
        SprMaterial m = l.getMaterial();

        String materialName;
        String materialArticle;
        Integer materialCode;
        Integer available;

        if (isAccepted) {
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