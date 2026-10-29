// TransferDocumentService.java
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
public class TransferDocumentService {

    private final DocTransferRepository docRepository;
    private final RegTransferLineRepository lineRepository;
    private final TransferEventLogRepository eventLogRepository;
    private final UserTransferColumnSettingsRepository columnSettingsRepository;
    private final SprMaterialRepository materialRepository;
    private final StationRepository stationRepository;
    private final RegStockLevelControlRepository regStockLevelControlRepository;
    private final StockRegisterService stockRegisterService;

    // Для синхронизации заказа
    private final DocMoveOrderRepository docMoveOrderRepository;
    private final RegMoveOrderLineRepository moveOrderLineRepository;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static final String BASE_MANUAL     = "MANUAL";
    public static final String BASE_REFILL     = "REFILL";
    public static final String BASE_MOVE_ORDER = "MOVE_ORDER";

    public static final String STATUS_DRAFT       = "DRAFT";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SHIPPING    = "SHIPPING";
    public static final String STATUS_SHIPPED     = "SHIPPED";
    public static final String STATUS_RECEIVED    = "RECEIVED";
    public static final String STATUS_LOADING     = "LOADING";
    public static final String STATUS_POSTED      = "POSTED";
    public static final String STATUS_CANCELLED   = "CANCELLED";

    // Статусы заказа
    private static final String MO_STATUS_APPROVED    = "APPROVED";
    private static final String MO_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String MO_STATUS_CLOSED      = "CLOSED";

    // Статусы строк заказа
    private static final String MO_LINE_NOT_STARTED = "NOT_STARTED";
    private static final String MO_LINE_IN_PROGRESS = "IN_PROGRESS";
    private static final String MO_LINE_SHIPPED     = "SHIPPED";
    private static final String MO_LINE_RECEIVED    = "RECEIVED";
    private static final String MO_LINE_CLOSED      = "CLOSED";
    private static final String MO_LINE_CANCELLED   = "CANCELLED";

    private static final List<String> ALL_COLUMNS_ORDER = List.of(
            "code", "docDatetime", "stockFromName", "stockToName",
            "baseType", "priority", "dueDate", "status"
    );
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(
            List.of("code", "docDatetime", "stockFromName", "stockToName", "status")
    );

    // ==================== LIST ====================

    public TransferListResponse getAllWithSettings(Integer userId) {
        String columnsJson = columnSettingsRepository.findByUserId(userId)
                .map(UserTransferColumnSettings::getColumnsJson).orElse(null);

        Set<String> visible = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        Map<String, Double> widths = new HashMap<>();
        Set<String> required = new LinkedHashSet<>(REQUIRED_COLUMNS);

        if (columnsJson != null && !columnsJson.isEmpty() && !columnsJson.equals("{}")) {
            parseColumnSettings(columnsJson, visible, widths, required);
        }

        List<String> ordered = ALL_COLUMNS_ORDER.stream()
                .filter(visible::contains).collect(Collectors.toList());

        List<TransferFlatDto> data = docRepository.findAllByOrderByDocDatetimeDesc()
                .stream().map(this::toFlatDto).collect(Collectors.toList());

        return TransferListResponse.builder()
                .columns(ordered)
                .data(data)
                .columnWidths(widths)
                .requiredColumns(new ArrayList<>(required))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void parseColumnSettings(String json, Set<String> visible,
                                     Map<String, Double> widths, Set<String> required) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            visible.clear();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = e.getKey();
                Object v = e.getValue();
                if (v instanceof Boolean b) {
                    if (b) visible.add(key);
                } else if (v instanceof Map<?, ?> m) {
                    Object vis = m.get("visible");
                    Object w = m.get("width");
                    Object req = m.get("required");
                    if (vis instanceof Boolean b && b) visible.add(key);
                    if (w instanceof Number n) widths.put(key, n.doubleValue());
                    if (req instanceof Boolean b && b) required.add(key);
                }
            }
        } catch (Exception ex) {
            visible.clear();
            visible.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== GET ====================

    public TransferDetailDto getByUid(UUID uid) {
        DocTransfer doc = docRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Перемещение не найдено: " + uid));
        return toDetailDto(doc);
    }

    public Integer generateCode() {
        Integer max = docRepository.findMaxCode();
        return max != null ? max + 1 : 1;
    }

    // ==================== CREATE ====================

    @Transactional
    public TransferDetailDto create(CreateTransferRequest request, String author) {
        UUID uid = request.getUid() != null ? request.getUid() : UUID.randomUUID();
        Optional<DocTransfer> existing = docRepository.findById(uid);
        if (existing.isPresent()) return toDetailDto(existing.get());

        String baseType = request.getBaseType() != null ? request.getBaseType() : BASE_MANUAL;

        validateStocks(request.getStockFromUid(), request.getStockToUid(), baseType);

        DocTransfer doc = DocTransfer.builder()
                .uid(uid)
                .code(generateCode())
                .docDatetime(request.getDocDatetime() != null ? request.getDocDatetime() : LocalDateTime.now())
                .stockFromUid(request.getStockFromUid())
                .stockToUid(request.getStockToUid())
                .docBaseUid(request.getDocBaseUid())
                .docBaseType(baseType)
                .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
                .dueDate(request.getDueDate())
                .operatorUid(request.getOperatorUid())
                .status(STATUS_DRAFT)
                .agreed(BASE_MOVE_ORDER.equals(baseType) ? Boolean.FALSE : null)
                .cancelled(Boolean.FALSE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        doc = docRepository.save(doc);

        if (request.getLines() != null) {
            for (CreateTransferRequest.TransferLineRequest lr : request.getLines()) {
                RegTransferLine line = RegTransferLine.builder()
                        .transfer(doc)
                        .material(lr.getMaterialUid() != null
                                ? materialRepository.getReferenceById(lr.getMaterialUid()) : null)
                        .quantity(lr.getQuantity() != null ? lr.getQuantity() : 0)
                        .cellUid(lr.getCellUid())
                        .batchInfo(lr.getBatchInfo())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                fillSnapshots(line);
                lineRepository.save(line);
            }
        }

        logEvent(doc.getUid(), null, "CREATE",
                "Создание перемещения: код " + doc.getCode(), author);
        return toDetailDto(doc);
    }

    // ==================== UPDATE ====================

    @Transactional
    public TransferDetailDto update(UUID uid, UpdateTransferRequest request, String author) {
        DocTransfer doc = mustGet(uid);

        if (BASE_MOVE_ORDER.equals(doc.getDocBaseType())) {
            throw new RuntimeException("Документ, созданный из заказа на перемещение, не редактируется");
        }
        if (BASE_REFILL.equals(doc.getDocBaseType())) {
            throw new RuntimeException("Документ, созданный из пополнения, нередактируемый");
        }
        if (Boolean.TRUE.equals(doc.getCancelled())) {
            throw new RuntimeException("Отменённый документ нельзя редактировать");
        }
        if (!STATUS_DRAFT.equals(doc.getStatus())) {
            throw new RuntimeException("Редактировать можно только документ в статусе «Записан»");
        }

        String newFrom = request.getStockFromUid() != null ? request.getStockFromUid() : doc.getStockFromUid();
        String newTo = request.getStockToUid() != null ? request.getStockToUid() : doc.getStockToUid();
        validateStocks(newFrom, newTo, doc.getDocBaseType());

        if (request.getDocDatetime() != null) doc.setDocDatetime(request.getDocDatetime());
        if (request.getStockFromUid() != null) doc.setStockFromUid(request.getStockFromUid());
        if (request.getStockToUid() != null) doc.setStockToUid(request.getStockToUid());
        if (request.getPriority() != null) doc.setPriority(request.getPriority());
        if (request.getDueDate() != null) doc.setDueDate(request.getDueDate());
        if (request.getOperatorUid() != null) doc.setOperatorUid(request.getOperatorUid());
        doc.setUpdatedAt(LocalDateTime.now());
        docRepository.save(doc);

        if (request.getLines() != null) {
            List<RegTransferLine> existing = lineRepository.findByTransferUid(uid);
            Map<UUID, RegTransferLine> byUid = existing.stream()
                    .collect(Collectors.toMap(RegTransferLine::getUid, l -> l));

            for (UpdateTransferRequest.LineUpdate lr : request.getLines()) {
                if (lr.getUid() != null && byUid.containsKey(lr.getUid())) {
                    RegTransferLine line = byUid.get(lr.getUid());
                    if (lr.getMaterialUid() != null) {
                        line.setMaterial(materialRepository.getReferenceById(lr.getMaterialUid()));
                    }
                    if (lr.getQuantity() != null) line.setQuantity(lr.getQuantity());
                    if (lr.getCellUid() != null) line.setCellUid(lr.getCellUid());
                    if (lr.getBatchInfo() != null) line.setBatchInfo(lr.getBatchInfo());
                    line.setUpdatedAt(LocalDateTime.now());
                    fillSnapshots(line);
                    lineRepository.save(line);
                    byUid.remove(lr.getUid());
                } else {
                    RegTransferLine line = RegTransferLine.builder()
                            .transfer(doc)
                            .material(lr.getMaterialUid() != null
                                    ? materialRepository.getReferenceById(lr.getMaterialUid()) : null)
                            .quantity(lr.getQuantity() != null ? lr.getQuantity() : 0)
                            .cellUid(lr.getCellUid())
                            .batchInfo(lr.getBatchInfo())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    fillSnapshots(line);
                    lineRepository.save(line);
                }
            }
            for (RegTransferLine orphan : byUid.values()) lineRepository.delete(orphan);
        }

        return toDetailDto(doc);
    }

    // ==================== DELETE ====================

    @Transactional
    public void delete(UUID uid, String author) {
        DocTransfer doc = mustGet(uid);
        if (BASE_MOVE_ORDER.equals(doc.getDocBaseType())) {
            throw new RuntimeException("Документ, созданный из заказа, удаляется вместе с заказом");
        }
        if (!STATUS_DRAFT.equals(doc.getStatus())) {
            throw new RuntimeException("Удалить можно только документ в статусе «Записан»");
        }
        logEvent(doc.getUid(), null, "DELETE",
                "Удаление перемещения: код " + doc.getCode(), author);
        docRepository.delete(doc);
    }

    // ==================== ПЕРЕХОДЫ СТАТУСОВ ====================

    @Transactional
    public TransferDetailDto agree(UUID uid, String author) {
        DocTransfer doc = mustGet(uid);
        if (!BASE_MOVE_ORDER.equals(doc.getDocBaseType())) {
            throw new RuntimeException("Согласование доступно только для документа из заказа на перемещение");
        }
        if (!STATUS_DRAFT.equals(doc.getStatus()) || Boolean.TRUE.equals(doc.getCancelled())) {
            throw new RuntimeException("Согласовать можно только записанный неотменённый документ");
        }
        doc.setAgreed(Boolean.TRUE);
        List<RegTransferLine> lines = lineRepository.findByTransferUid(uid);
        for (RegTransferLine l : lines) {
            l.setAcceptedQuantity(l.getQuantity());
            lineRepository.save(l);
        }
        doc.setUpdatedAt(LocalDateTime.now());
        docRepository.save(doc);
        logEvent(doc.getUid(), null, "AGREE", "Согласование документа", author);
        return toDetailDto(doc);
    }

    /**
     * Отмена: разрешена в DRAFT или IN_PROGRESS (для MANUAL/REFILL — до отгрузки).
     * После «Взять в работу» (IN_PROGRESS) — фиксируем в журнале, что бронь снята.
     * Для MOVE_ORDER — отмена через заказ.
     */
    @Transactional
    public TransferDetailDto cancel(UUID uid, String author) {
        DocTransfer doc = mustGet(uid);

        if (BASE_MOVE_ORDER.equals(doc.getDocBaseType())) {
            throw new RuntimeException("Документ, созданный из заказа, отменяется через заказ на перемещение");
        }
        if (BASE_REFILL.equals(doc.getDocBaseType())) {
            throw new RuntimeException("Документ из пополнения нельзя отменить");
        }
        if (Boolean.TRUE.equals(doc.getCancelled())) {
            throw new RuntimeException("Документ уже отменён");
        }

        boolean canCancelDraft = STATUS_DRAFT.equals(doc.getStatus());
        boolean canCancelInWork = STATUS_IN_PROGRESS.equals(doc.getStatus());
        if (!canCancelDraft && !canCancelInWork) {
            throw new RuntimeException("Отменить можно только до отгрузки (записан или в работе)");
        }

        boolean wasInWork = canCancelInWork;

        doc.setCancelled(Boolean.TRUE);
        doc.setStatus(STATUS_CANCELLED);
        doc.setUpdatedAt(LocalDateTime.now());
        docRepository.save(doc);

        if (wasInWork) {
            logEvent(doc.getUid(), null, "CANCEL_RESERVATION",
                    "Отмена документа: бронь по номенклатуре снята", author);
        } else {
            logEvent(doc.getUid(), null, "CANCEL",
                    "Отмена документа", author);
        }
        return toDetailDto(doc);
    }

    @Transactional
    public TransferDetailDto takeToWork(UUID uid, String author) {
        DocTransfer doc = mustGet(uid);
        if (Boolean.TRUE.equals(doc.getCancelled())) {
            throw new RuntimeException("Отменённый документ нельзя взять в работу");
        }
        if (!STATUS_DRAFT.equals(doc.getStatus())) {
            throw new RuntimeException("Взять в работу можно только записанный документ");
        }
        if (BASE_MOVE_ORDER.equals(doc.getDocBaseType()) && !Boolean.TRUE.equals(doc.getAgreed())) {
            throw new RuntimeException("Документ из заказа нужно сначала согласовать");
        }

        doc.setStatus(STATUS_IN_PROGRESS);
        doc.setUpdatedAt(LocalDateTime.now());
        docRepository.save(doc);

        if (BASE_MOVE_ORDER.equals(doc.getDocBaseType()) && doc.getDocBaseUid() != null) {
            syncMoveOrderOnTransfer(doc, MO_LINE_IN_PROGRESS);
        }

        logEvent(doc.getUid(), null, "TAKE_TO_WORK", "Взятие в работу: " + author, author);
        return toDetailDto(doc);
    }

    @Transactional
    public TransferDetailDto ship(UUID uid, String author) {
        DocTransfer doc = mustGet(uid);
        if (Boolean.TRUE.equals(doc.getCancelled())) {
            throw new RuntimeException("Отменённый документ нельзя отгрузить");
        }
        if (!STATUS_IN_PROGRESS.equals(doc.getStatus())) {
            throw new RuntimeException("Отгрузить можно только из «В работе»");
        }
        doc.setStatus(STATUS_SHIPPED);
        doc.setUpdatedAt(LocalDateTime.now());
        docRepository.save(doc);

        if (BASE_MOVE_ORDER.equals(doc.getDocBaseType()) && doc.getDocBaseUid() != null) {
            syncMoveOrderOnTransfer(doc, MO_LINE_SHIPPED);
        }

        logEvent(doc.getUid(), null, "SHIP", "Отгружено: " + author, author);
        return toDetailDto(doc);
    }

    @Transactional
    public TransferDetailDto receive(UUID uid, String author) {
        DocTransfer doc = mustGet(uid);
        if (Boolean.TRUE.equals(doc.getCancelled())) {
            throw new RuntimeException("Отменённый документ нельзя принять");
        }
        if (!STATUS_SHIPPED.equals(doc.getStatus())) {
            throw new RuntimeException("Принять можно только отгруженный документ");
        }
        doc.setStatus(STATUS_RECEIVED);
        doc.setUpdatedAt(LocalDateTime.now());
        docRepository.save(doc);

        if (BASE_MOVE_ORDER.equals(doc.getDocBaseType()) && doc.getDocBaseUid() != null) {
            syncMoveOrderOnTransfer(doc, MO_LINE_RECEIVED);
        }

        logEvent(doc.getUid(), null, "RECEIVE", "Принято: " + author, author);
        return toDetailDto(doc);
    }

    @Transactional
    public TransferDetailDto load(UUID uid, String author) {
        DocTransfer doc = mustGet(uid);
        if (Boolean.TRUE.equals(doc.getCancelled())) {
            throw new RuntimeException("Отменённый документ нельзя загрузить");
        }
        if (!STATUS_SHIPPED.equals(doc.getStatus()) && !STATUS_RECEIVED.equals(doc.getStatus())) {
            throw new RuntimeException("Загрузить можно только из «Отгружено» / «Принято»");
        }
        doc.setStatus(STATUS_POSTED);
        doc.setClosedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        docRepository.save(doc);

        if (BASE_MOVE_ORDER.equals(doc.getDocBaseType()) && doc.getDocBaseUid() != null) {
            syncMoveOrderOnTransfer(doc, MO_LINE_CLOSED);
        }

        logEvent(doc.getUid(), null, "LOAD", "Загружено (проведён): " + author, author);
        return toDetailDto(doc);
    }

    /**
     * Синхронизация строк и статуса заказа-основания при переходе transfer'а.
     */
    private void syncMoveOrderOnTransfer(DocTransfer doc, String lineStatus) {
        try {
            Optional<DocMoveOrder> moOpt = docMoveOrderRepository.findByUid(doc.getDocBaseUid());
            if (moOpt.isEmpty()) return;
            DocMoveOrder mo = moOpt.get();

            List<RegMoveOrderLine> moLines = moveOrderLineRepository.findByDocUid(mo.getUid());

            for (RegMoveOrderLine ml : moLines) {
                if (ml.getTransfer() != null
                        && Objects.equals(ml.getTransfer().getUid(), doc.getUid())
                        && !MO_LINE_CANCELLED.equals(ml.getStatus())) {
                    if (!Objects.equals(ml.getStatus(), lineStatus)) {
                        ml.setStatus(lineStatus);
                        moveOrderLineRepository.save(ml);
                    }
                }
            }

            List<RegMoveOrderLine> refreshed = moveOrderLineRepository.findByDocUid(mo.getUid());
            recalcMoveOrderStatus(mo, refreshed);
        } catch (Exception ignored) {
        }
    }

    private void recalcMoveOrderStatus(DocMoveOrder mo, List<RegMoveOrderLine> lines) {
        List<RegMoveOrderLine> active = lines.stream()
                .filter(l -> !MO_LINE_CANCELLED.equals(l.getStatus()))
                .collect(Collectors.toList());
        if (active.isEmpty()) return;

        boolean allClosed = active.stream().allMatch(l -> MO_LINE_CLOSED.equals(l.getStatus()));
        if (allClosed) {
            if (!MO_STATUS_CLOSED.equals(mo.getStatus())) {
                mo.setStatus(MO_STATUS_CLOSED);
                docMoveOrderRepository.save(mo);
            }
            return;
        }

        if (!MO_STATUS_IN_PROGRESS.equals(mo.getStatus()) && !MO_STATUS_CLOSED.equals(mo.getStatus())) {
            if (MO_STATUS_APPROVED.equals(mo.getStatus())) {
                mo.setStatus(MO_STATUS_IN_PROGRESS);
                docMoveOrderRepository.save(mo);
            }
        }
    }

    // ==================== EVENTS ====================

    public List<TransferEventLogDto> getEvents(UUID uid) {
        return eventLogRepository.findByDocUidOrderByCreatedAtDesc(uid)
                .stream().map(this::toEventDto).collect(Collectors.toList());
    }

    public List<TransferEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toEventDto).collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private DocTransfer mustGet(UUID uid) {
        return docRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Перемещение не найдено: " + uid));
    }

    private void validateStocks(String stockFromUid, String stockToUid, String baseType) {
        if (BASE_MOVE_ORDER.equals(baseType)) return;
        if (stockFromUid == null || stockFromUid.isBlank()) {
            throw new RuntimeException("Не указан склад-отправитель");
        }
        if (stockToUid == null || stockToUid.isBlank()) {
            throw new RuntimeException("Не указан склад-получатель");
        }
        if (Objects.equals(stockFromUid, stockToUid)) {
            throw new RuntimeException("Склад-отправитель и склад-получатель не должны совпадать");
        }
    }

    private void fillSnapshots(RegTransferLine line) {
        SprMaterial m = line.getMaterial();
        if (m != null) {
            line.setMaterialNameSnapshot(m.getNameMaterial());
            line.setMaterialArticleSnapshot(m.getArticle());
            line.setMaterialCodeSnapshot(m.getCodeMaterial());
        }
    }

    private Integer getAvailableLive(String stockUid, UUID materialUid) {
        if (stockUid == null || materialUid == null) return null;
        Integer free = stockRegisterService.getAvailableQuantitySafe(materialUid, stockUid);
        if (free == null) return null;
        Integer critical = regStockLevelControlRepository
                .findByStationUidAndMaterialUid(stockUid, materialUid)
                .map(RegStockLevelControl::getCriticalStock)
                .orElse(null);
        return critical != null ? Math.max(0, free - critical) : free;
    }

    private String resolveStockName(String stockUid) {
        if (stockUid == null || stockUid.isBlank()) return null;
        if ("MAIN".equals(stockUid)) return "Основной склад";
        return stationRepository.findByUid(stockUid)
                .map(Station::getName).orElse(stockUid);
    }

    private String resolveCellName(UUID cellUid) {
        if (cellUid == null) return null;
        return cellUid.toString();
    }

    private String buildDocBaseLabel(DocTransfer d) {
        if (d.getDocBaseUid() == null || d.getDocBaseType() == null) return null;
        String typeLabel = switch (d.getDocBaseType()) {
            case BASE_MOVE_ORDER -> "Заказ на перемещение";
            case BASE_REFILL -> "Пополнение";
            default -> null;
        };
        if (typeLabel == null) return null;
        return typeLabel + " (uid: " + d.getDocBaseUid() + ")";
    }

    private void logEvent(UUID docUid, UUID lineUid, String type, String desc, String author) {
        eventLogRepository.save(TransferEventLog.builder()
                .docUid(docUid)
                .lineUid(lineUid)
                .eventType(type)
                .eventDescription(desc)
                .author(author)
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private TransferFlatDto toFlatDto(DocTransfer d) {
        return TransferFlatDto.builder()
                .uid(d.getUid())
                .code(d.getCode())
                .docDatetime(d.getDocDatetime())
                .stockFromUid(d.getStockFromUid())
                .stockFromName(resolveStockName(d.getStockFromUid()))
                .stockToUid(d.getStockToUid())
                .stockToName(resolveStockName(d.getStockToUid()))
                .baseType(d.getDocBaseType())
                .docBaseUid(d.getDocBaseUid())
                .priority(d.getPriority())
                .dueDate(d.getDueDate())
                .operatorUid(d.getOperatorUid())
                .status(d.getStatus())
                .agreed(d.getAgreed())
                .cancelled(d.getCancelled())
                .build();
    }

    private TransferDetailDto toDetailDto(DocTransfer d) {
        List<RegTransferLine> lines = lineRepository.findByTransferUid(d.getUid());
        List<TransferLineDto> lineDtos = lines.stream().map(l -> {
            SprMaterial m = l.getMaterial();
            Integer available = (m != null && d.getStockFromUid() != null)
                    ? getAvailableLive(d.getStockFromUid(), m.getUid()) : null;
            return TransferLineDto.builder()
                    .uid(l.getUid())
                    .docUid(d.getUid())
                    .materialUid(m != null ? m.getUid() : null)
                    .materialName(m != null ? m.getNameMaterial() : l.getMaterialNameSnapshot())
                    .materialArticle(m != null ? m.getArticle() : l.getMaterialArticleSnapshot())
                    .materialCode(m != null ? m.getCodeMaterial() : l.getMaterialCodeSnapshot())
                    .quantity(l.getQuantity())
                    .acceptedQuantity(l.getAcceptedQuantity())
                    .cellUid(l.getCellUid())
                    .cellName(resolveCellName(l.getCellUid()))
                    .available(available)
                    .batchInfo(l.getBatchInfo())
                    .build();
        }).collect(Collectors.toList());

        return TransferDetailDto.builder()
                .uid(d.getUid())
                .code(d.getCode())
                .docDatetime(d.getDocDatetime())
                .stockFromUid(d.getStockFromUid())
                .stockFromName(resolveStockName(d.getStockFromUid()))
                .stockToUid(d.getStockToUid())
                .stockToName(resolveStockName(d.getStockToUid()))
                .baseType(d.getDocBaseType())
                .docBaseUid(d.getDocBaseUid())
                .docBaseLabel(buildDocBaseLabel(d))
                .priority(d.getPriority())
                .dueDate(d.getDueDate())
                .operatorUid(d.getOperatorUid())
                .operatorName(null)
                .status(d.getStatus())
                .agreed(d.getAgreed())
                .cancelled(d.getCancelled())
                .lines(lineDtos)
                .build();
    }

    private TransferEventLogDto toEventDto(TransferEventLog e) {
        return TransferEventLogDto.builder()
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