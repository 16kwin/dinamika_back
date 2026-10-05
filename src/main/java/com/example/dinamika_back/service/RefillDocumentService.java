// RefillDocumentService.java
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
public class RefillDocumentService {

    private final DocRefillRepository docRepository;
    private final RegRefillCellRepository cellRepository;
    private final RefillEventLogRepository eventLogRepository;
    private final RefillColumnSettingsService columnSettingsService;
    private final StationRepository stationRepository;
    private final DocPatternRepository docPatternRepository;
    private final RegCellsRepository regCellsRepository;
    private final UserRepository userRepository;
    private final StationConfigurationRepository stationConfigurationRepository;
    private final RegStockLevelControlRepository regStockLevelControlRepository;
    private final RegStationCellRepository regStationCellRepository;
    private final SprMaterialRepository materialRepository;
    private final SprCellAssignmentRepository cellAssignmentRepository;
    private final StockRegisterService stockRegisterService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> ALL_COLUMNS_ORDER = List.of(
            "code", "docDatetime", "stationName", "templateName",
            "cellsToLoad", "totalQuantity", "status", "uid"
    );

    // ==================== LIST ====================

    public DocRefillListResponse getAll(Integer userId) {
        String columnsJson = columnSettingsService.getColumnsJson(userId);
        Set<String> visibleColumns = new LinkedHashSet<>(ALL_COLUMNS_ORDER);
        Map<String, Double> columnWidths = new HashMap<>();
        Set<String> requiredColumns = new LinkedHashSet<>();

        if (columnsJson != null && !columnsJson.isEmpty() && !columnsJson.equals("{}")) {
            parseColumnSettings(columnsJson, visibleColumns, columnWidths, requiredColumns);
        }

        List<String> orderedColumns = ALL_COLUMNS_ORDER.stream()
                .filter(visibleColumns::contains)
                .collect(Collectors.toList());

        List<DocRefill> docs = docRepository.findAllWithRelations();
        List<Map<String, Object>> data = docs.stream()
                .map(this::buildRowData)
                .collect(Collectors.toList());

        return DocRefillListResponse.builder()
                .columns(orderedColumns)
                .data(data)
                .columnWidths(columnWidths)
                .requiredColumns(new ArrayList<>(requiredColumns))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void parseColumnSettings(String json, Set<String> visibleColumns,
                                     Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
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

    private Map<String, Object> buildRowData(DocRefill doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", doc.getUid());
        row.put("code", doc.getCode());
        row.put("docDatetime", doc.getDocDatetime());

        Station station = doc.getStation();
        row.put("stationUid", doc.getStationUid());
        row.put("stationName", station != null ? station.getName() : null);

        DocPattern template = doc.getTemplate();
        row.put("templateUid", template != null ? template.getUid() : null);
        row.put("templateName", template != null ? template.getNamePattern() : null);

        User createdBy = doc.getCreatedBy();
        row.put("createdByUserId", createdBy != null ? createdBy.getId() : null);
        row.put("createdByName", buildUserName(createdBy));

        row.put("totalCells", doc.getTotalCells());
        row.put("cellsToLoad", doc.getCellsToLoad());
        row.put("totalQuantity", doc.getTotalQuantity());
        row.put("status", doc.getStatus());
        return row;
    }

    // ==================== GET BY UID ====================

    public DocRefillDto getByUid(UUID uid) {
        DocRefill doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));
        return toDTO(doc);
    }

    // ==================== GENERATE CODE ====================

    public Integer generateCode() {
        Integer max = docRepository.findMaxCode();
        return max != null ? max + 1 : 1;
    }

    // ==================== SNAPSHOT ====================

    public DocRefillDto buildSnapshot(String stationUid, UUID templateUid) {
        Station station = null;
        if (stationUid != null && !stationUid.isBlank()) {
            station = stationRepository.findByUid(stationUid)
                    .orElseThrow(() -> new RuntimeException("Станция не найдена: " + stationUid));
        }

        final UUID effectiveTemplateUid;
        if (templateUid != null) {
            effectiveTemplateUid = templateUid;
        } else if (station != null && station.getActiveTemplate() != null) {
            effectiveTemplateUid = station.getActiveTemplate().getUid();
        } else {
            effectiveTemplateUid = null;
        }

        DocPattern template = null;
        if (effectiveTemplateUid != null) {
            template = docPatternRepository.findById(effectiveTemplateUid).orElse(null);
        }

        DocRefillDto dto = DocRefillDto.builder()
                .code(null)
                .docDatetime(LocalDateTime.now())
                .totalCells(0)
                .cellsToLoad(0)
                .totalQuantity(0)
                .status("NEW")
                .stationUid(station != null ? station.getUid() : null)
                .stationName(station != null ? station.getName() : null)
                .stationCode(station != null ? station.getCode() : null)
                .isTmc(station != null && Boolean.TRUE.equals(station.getIsTmc()))
                .isSgd(station != null && Boolean.TRUE.equals(station.getIsSgd()))
                .isOk(station != null && Boolean.TRUE.equals(station.getIsOk()))
                .accountingLabel(station != null ? buildAccountingLabel(station) : null)
                .templateUid(template != null ? template.getUid() : null)
                .templateName(template != null ? template.getNamePattern() : null)
                .templateNumber(template != null ? template.getNumber() : null)
                .templateCategoryName(template != null && template.getCategory() != null
                        ? template.getCategory().getName() : null)
                .build();

        UUID configUid = null;
        if (station != null && station.getConfiguration() != null) {
            configUid = station.getConfiguration().getUid();
        } else if (template != null && template.getStationConfiguration() != null) {
            configUid = template.getStationConfiguration().getUid();
        }
        if (configUid != null) {
            final UUID cfgUid = configUid;
            stationConfigurationRepository.findById(cfgUid).ifPresent(cfg -> {
                dto.setConfigurationUid(cfg.getUid());
                dto.setConfigurationName(cfg.getName());
                dto.setCellsStructureSnapshot(cfg.getCellsStructure());
                if (cfg.getModel() != null) {
                    dto.setModelUid(cfg.getModel().getUid());
                    dto.setModelName(cfg.getModel().getName());
                }
            });
        }

        final String stationUidForFact = station != null ? station.getUid() : null;
        Map<String, Integer> factByAddress = new HashMap<>();
        if (stationUidForFact != null) {
            List<RegStationCell> stationCells = regStationCellRepository.findByStationUid(stationUidForFact);
            for (RegStationCell sc : stationCells) {
                factByAddress.put(
                        cellKey(sc.getDrumNumber(), sc.getColumnNumber(), sc.getNumberCell()),
                        sc.getQuantity() != null ? sc.getQuantity() : 0
                );
            }
        }

        List<RefillCellDto> cells = new ArrayList<>();
        if (template != null) {
            List<RegCells> templateCells = regCellsRepository.findByDocPatternUid(template.getUid());
            for (RegCells tc : templateCells) {
                String addrKey = cellKey(tc.getDrumNumber(), tc.getColumnNumber(), tc.getNumberCell());

                RefillCellDto cell = buildCellDtoFromTemplate(tc, factByAddress.get(addrKey));

                if (tc.getMaterial() != null) {
                    StockBalanceDto bal = stockRegisterService.getBalanceByMaterial(tc.getMaterial().getUid(), null);
                    if (bal != null) {
                        cell.setStockAvailable(bal.getCleanQuantity());
                        cell.setStockMinStock(bal.getMinStock());
                        cell.setStockCriticalStock(bal.getCriticalStock());
                    }
                }

                if (station != null && tc.getMaterial() != null) {
                    regStockLevelControlRepository
                            .findByStationUidAndMaterialUid(station.getUid(), tc.getMaterial().getUid())
                            .ifPresentOrElse(reg -> {
                                cell.setStockControlExists(true);
                                cell.setMinStockSnapshot(reg.getMinStock());
                                cell.setCriticalStockSnapshot(reg.getCriticalStock());
                            }, () -> cell.setStockControlExists(false));
                } else {
                    cell.setStockControlExists(false);
                }

                cells.add(cell);
            }
        }

        dto.setCells(cells);
        dto.setTotalCells(cells.size());
        return dto;
    }

    // ==================== CREATE ====================

    @Transactional
    public DocRefillDto create(CreateDocRefillRequest request, String author) {
        UUID uid = request.getUid() != null ? request.getUid() : UUID.randomUUID();

        Optional<DocRefill> existing = docRepository.findByUidWithRelations(uid);
        if (existing.isPresent()) {
            return toDTO(existing.get());
        }

        DocRefill doc = DocRefill.builder()
                .uid(uid)
                .code(generateCode())
                .docDatetime(request.getDocDatetime() != null ? request.getDocDatetime() : LocalDateTime.now())
                .status(request.getStatus() != null ? request.getStatus() : "NEW")
                .build();

        if (request.getCreatedByUserId() != null) {
            userRepository.findById(request.getCreatedByUserId()).ifPresent(doc::setCreatedBy);
        }

        Station station = null;
        if (request.getStationUid() != null) {
            station = stationRepository.findByUid(request.getStationUid())
                    .orElseThrow(() -> new RuntimeException("Станция не найдена: " + request.getStationUid()));
            doc.setStation(station);
            doc.setStationUid(station.getUid());
        }

        final UUID effectiveTemplateUid;
        if (request.getTemplateUid() != null) {
            effectiveTemplateUid = request.getTemplateUid();
        } else if (station != null && station.getActiveTemplate() != null) {
            effectiveTemplateUid = station.getActiveTemplate().getUid();
        } else {
            effectiveTemplateUid = null;
        }

        DocPattern template = null;
        if (effectiveTemplateUid != null) {
            template = docPatternRepository.findById(effectiveTemplateUid)
                    .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + effectiveTemplateUid));
            doc.setTemplate(template);
        }

        UUID configUid = null;
        if (station != null && station.getConfiguration() != null) {
            configUid = station.getConfiguration().getUid();
        } else if (template != null && template.getStationConfiguration() != null) {
            configUid = template.getStationConfiguration().getUid();
        }
        if (configUid != null) {
            doc.setConfigurationUid(configUid);
            final UUID cfgUid = configUid;
            stationConfigurationRepository.findById(cfgUid).ifPresent(cfg -> {
                if (cfg.getModel() != null) doc.setModelUid(cfg.getModel().getUid());
            });
        }

        DocRefill saved = docRepository.save(doc);

        List<CreateDocRefillRequest.RefillCellRequest> incomingCells = request.getCells();
        boolean frontProvidedCells = incomingCells != null
                && !incomingCells.isEmpty()
                && incomingCells.stream().anyMatch(c ->
                        c.getTemplateQuantity() != null
                                || c.getMaterialUid() != null
                                || c.getCellAssignmentUid() != null);

        int totalCells = 0;
        int cellsToLoad = 0;
        int totalQuantity = 0;

        if (frontProvidedCells) {
            for (CreateDocRefillRequest.RefillCellRequest rc : incomingCells) {
                RegRefillCell cell = RegRefillCell.builder()
                        .docRefill(saved)
                        .drumNumber(rc.getDrumNumber())
                        .columnNumber(rc.getColumnNumber())
                        .numberCell(rc.getNumberCell())
                        .templateQuantity(rc.getTemplateQuantity())
                        .factQuantity(rc.getFactQuantity())
                        .refillQuantity(rc.getRefillQuantity())
                        .minStockSnapshot(rc.getMinStockSnapshot())
                        .criticalStockSnapshot(rc.getCriticalStockSnapshot())
                        .stockControlExists(rc.getStockControlExists() != null && rc.getStockControlExists())
                        .returnToThisCell(rc.getReturnToThisCell() != null && rc.getReturnToThisCell())
                        .isIndividual(rc.getIsIndividual() != null && rc.getIsIndividual())
                        .build();

                if (rc.getCellAssignmentUid() != null) {
                    cell.setCellAssignment(cellAssignmentRepository.getReferenceById(rc.getCellAssignmentUid()));
                }
                if (rc.getMaterialUid() != null) {
                    cell.setMaterial(materialRepository.getReferenceById(rc.getMaterialUid()));
                }

                if (cell.getRefillQuantity() != null && cell.getRefillQuantity() > 0) {
                    cellsToLoad++;
                    totalQuantity += cell.getRefillQuantity();
                }
                totalCells++;

                cellRepository.save(cell);
            }
        } else if (template != null) {
            final String stationUidForFact = station != null ? station.getUid() : null;
            Map<String, Integer> factByAddress = new HashMap<>();
            if (stationUidForFact != null) {
                List<RegStationCell> stationCells = regStationCellRepository.findByStationUid(stationUidForFact);
                for (RegStationCell sc : stationCells) {
                    factByAddress.put(
                            cellKey(sc.getDrumNumber(), sc.getColumnNumber(), sc.getNumberCell()),
                            sc.getQuantity() != null ? sc.getQuantity() : 0
                    );
                }
            }

            Map<String, Integer> userQty = new HashMap<>();
            if (incomingCells != null) {
                for (CreateDocRefillRequest.RefillCellRequest c : incomingCells) {
                    userQty.put(cellKey(c.getDrumNumber(), c.getColumnNumber(), c.getNumberCell()),
                            c.getRefillQuantity());
                }
            }

            List<RegCells> templateCells = regCellsRepository.findByDocPatternUid(template.getUid());
            for (RegCells tc : templateCells) {
                String addrKey = cellKey(tc.getDrumNumber(), tc.getColumnNumber(), tc.getNumberCell());

                RegRefillCell cell = RegRefillCell.builder()
                        .docRefill(saved)
                        .drumNumber(tc.getDrumNumber())
                        .columnNumber(tc.getColumnNumber())
                        .numberCell(tc.getNumberCell())
                        .cellAssignment(tc.getCellAssignment())
                        .material(tc.getMaterial())
                        .templateQuantity(tc.getQuantity())
                        .factQuantity(factByAddress.get(addrKey))
                        .refillQuantity(userQty.get(addrKey))
                        .returnToThisCell(tc.getReturnToThisCell() != null && tc.getReturnToThisCell())
                        .isIndividual(tc.getIsIndividual() != null && tc.getIsIndividual())
                        .build();

                if (station != null && tc.getMaterial() != null) {
                    regStockLevelControlRepository
                            .findByStationUidAndMaterialUid(station.getUid(), tc.getMaterial().getUid())
                            .ifPresentOrElse(reg -> {
                                cell.setStockControlExists(true);
                                cell.setMinStockSnapshot(reg.getMinStock());
                                cell.setCriticalStockSnapshot(reg.getCriticalStock());
                            }, () -> cell.setStockControlExists(false));
                } else {
                    cell.setStockControlExists(false);
                }

                if (cell.getRefillQuantity() != null && cell.getRefillQuantity() > 0) {
                    cellsToLoad++;
                    totalQuantity += cell.getRefillQuantity();
                }
                totalCells++;

                cellRepository.save(cell);
            }
        }

        saved.setTotalCells(totalCells);
        saved.setCellsToLoad(cellsToLoad);
        saved.setTotalQuantity(totalQuantity);
        docRepository.save(saved);

        cellRepository.flush();

        // === РЕГИСТР СКЛАДА: создаём бронь ===
        List<RegRefillCell> savedCells = cellRepository.findByDocUid(saved.getUid());
        List<StockReserveItem> stockItems = buildStockItems(savedCells);
        if (!stockItems.isEmpty()) {
            stockRegisterService.reserve(saved.getUid(), null, stockItems, author);
        }

        logEvent(saved.getUid(), null, "CREATE",
                "Создание документа пополнения: код " + saved.getCode(), null, null, null, author);

        return toDTO(saved);
    }

    // ==================== UPDATE ====================

    @Transactional
    public DocRefillDto update(UUID uid, UpdateDocRefillRequest request, String author) {
        DocRefill doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));

        if ("POSTED".equals(doc.getStatus())) {
            throw new RuntimeException("Проведённый документ нельзя редактировать");
        }

        if (request.getDocDatetime() != null) doc.setDocDatetime(request.getDocDatetime());

        if (request.getStationUid() != null) {
            Station station = stationRepository.findByUid(request.getStationUid())
                    .orElseThrow(() -> new RuntimeException("Станция не найдена"));
            doc.setStation(station);
            doc.setStationUid(station.getUid());
        }

        if (request.getStatus() != null) {
            doc.setStatus(request.getStatus());
        }

        docRepository.save(doc);

        if (request.getCells() != null) {
            List<RegRefillCell> existing = cellRepository.findByDocUid(uid);
            Map<String, RegRefillCell> existingMap = new HashMap<>();
            for (RegRefillCell c : existing) {
                existingMap.put(cellKey(c.getDrumNumber(), c.getColumnNumber(), c.getNumberCell()), c);
            }

            int cellsToLoad = 0;
            int totalQuantity = 0;

            for (CreateDocRefillRequest.RefillCellRequest rc : request.getCells()) {
                String key = cellKey(rc.getDrumNumber(), rc.getColumnNumber(), rc.getNumberCell());
                RegRefillCell cell = existingMap.get(key);
                if (cell == null) continue;

                if (!Objects.equals(cell.getRefillQuantity(), rc.getRefillQuantity())) {
                    logEvent(uid, cell.getUid(), "UPDATE",
                            "Ячейка " + formatCellLabel(rc.getDrumNumber(), rc.getColumnNumber(), rc.getNumberCell())
                                    + ": Количество на пополнение " + cell.getRefillQuantity()
                                    + " → " + rc.getRefillQuantity(),
                            "Количество на пополнение",
                            String.valueOf(cell.getRefillQuantity()),
                            String.valueOf(rc.getRefillQuantity()), author);
                    cell.setRefillQuantity(rc.getRefillQuantity());
                    cellRepository.save(cell);
                }

                if (cell.getRefillQuantity() != null && cell.getRefillQuantity() > 0) {
                    cellsToLoad++;
                    totalQuantity += cell.getRefillQuantity();
                }
            }

            doc.setCellsToLoad(cellsToLoad);
            doc.setTotalQuantity(totalQuantity);
            docRepository.save(doc);
        }

        // === РЕГИСТР СКЛАДА: снимаем старые брони, создаём новые ===
        List<RegRefillCell> freshCells = cellRepository.findByDocUid(uid);
        List<StockReserveItem> stockItems = buildStockItems(freshCells);
        stockRegisterService.rebuildForDoc(uid, null, stockItems, author);

        return toDTO(doc);
    }

    // ==================== DELETE ====================

    @Transactional
    public void delete(UUID uid, String author) {
        DocRefill doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));

        if ("POSTED".equals(doc.getStatus())) {
            throw new RuntimeException("Проведённый документ нельзя удалить");
        }

        // === РЕГИСТР СКЛАДА: снимаем бронь ===
        stockRegisterService.releaseByDoc(uid, author);

        logEvent(uid, null, "DELETE", "Удаление документа: код " + doc.getCode(),
                null, String.valueOf(doc.getCode()), null, author);

        docRepository.delete(doc);
    }

    // ==================== POST ====================

    @Transactional
    public DocRefillDto post(UUID uid, String author) {
        DocRefill doc = docRepository.findByUidWithRelations(uid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + uid));

        if ("POSTED".equals(doc.getStatus())) {
            throw new RuntimeException("Документ уже проведён");
        }

        String stationUid = doc.getStationUid();
        if (stationUid == null) {
            throw new RuntimeException("У документа не указана станция");
        }

        Station station = stationRepository.findByUid(stationUid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + stationUid));

        List<RegRefillCell> cells = cellRepository.findByDocUid(uid);

        for (RegRefillCell c : cells) {
            Integer refillQty = c.getRefillQuantity();
            if (refillQty == null || refillQty <= 0) continue;

            Integer drum = c.getDrumNumber();
            Integer column = c.getColumnNumber();
            Integer number = c.getNumberCell();

            Optional<RegStationCell> existing = regStationCellRepository
                    .findByStationUidAndAddress(stationUid, drum, column, number);

            if (existing.isPresent()) {
                RegStationCell stationCell = existing.get();
                int currentQty = stationCell.getQuantity() != null ? stationCell.getQuantity() : 0;
                stationCell.setQuantity(currentQty + refillQty);
                regStationCellRepository.save(stationCell);
            } else {
                RegStationCell stationCell = RegStationCell.builder()
                        .station(station)
                        .drumNumber(drum)
                        .columnNumber(column)
                        .numberCell(number)
                        .quantity(refillQty)
                        .cellAssignment(c.getCellAssignment())
                        .material(c.getMaterial())
                        .returnToThisCell(c.getReturnToThisCell() != null && c.getReturnToThisCell())
                        .isIndividual(c.getIsIndividual() != null && c.getIsIndividual())
                        .build();
                regStationCellRepository.save(stationCell);
            }
        }

        // === РЕГИСТР СКЛАДА: закрываем бронь ===
        stockRegisterService.commitByDoc(uid, author);

        doc.setStatus("POSTED");
        docRepository.save(doc);

        logEvent(uid, null, "POST", "Проведение документа: код " + doc.getCode(),
                null, null, null, author);

        return toDTO(doc);
    }

    // ==================== EVENTS ====================

    public List<RefillEventLogDto> getEvents(UUID uid) {
        return eventLogRepository.findByDocRefillUidOrderByCreatedAtDesc(uid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<RefillEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private String buildUserName(User u) {
        if (u == null) return null;
        StringBuilder sb = new StringBuilder();
        if (u.getLastName() != null) sb.append(u.getLastName()).append(" ");
        if (u.getFirstName() != null) sb.append(u.getFirstName()).append(" ");
        if (u.getMiddleName() != null) sb.append(u.getMiddleName());
        String s = sb.toString().trim();
        return s.isEmpty() ? u.getUsername() : s;
    }

    private List<StockReserveItem> buildStockItems(List<RegRefillCell> cells) {
        Map<UUID, Integer> grouped = new LinkedHashMap<>();
        for (RegRefillCell c : cells) {
            if (c.getMaterial() == null) continue;
            int qty = c.getRefillQuantity() != null ? c.getRefillQuantity() : 0;
            if (qty <= 0) continue;
            grouped.merge(c.getMaterial().getUid(), qty, Integer::sum);
        }
        return grouped.entrySet().stream()
                .map(e -> StockReserveItem.builder()
                        .materialUid(e.getKey())
                        .quantity(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private String buildAccountingLabel(Station station) {
        List<String> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(station.getIsTmc())) parts.add("ТМЦ");
        if (Boolean.TRUE.equals(station.getIsSgd())) parts.add("СГД");
        if (Boolean.TRUE.equals(station.getIsOk())) parts.add("ОК");
        return parts.isEmpty() ? null : String.join(" - ", parts);
    }

    private String cellKey(Integer drum, Integer column, Integer number) {
        return (drum != null ? drum : 0) + "-" + (column != null ? column : 0) + "-" + (number != null ? number : 0);
    }

    private String formatCellLabel(Integer drum, Integer column, Integer number) {
        StringBuilder sb = new StringBuilder();
        if (drum != null) sb.append("Барабан ").append(drum).append(", ");
        if (column != null) sb.append("Колонка ").append(column).append(", ");
        if (number != null) sb.append("Ячейка ").append(number);
        return sb.length() > 0 ? sb.toString() : "—";
    }

    private void logEvent(UUID docUid, UUID cellUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        RefillEventLog log = RefillEventLog.builder()
                .docRefillUid(docUid)
                .cellUid(cellUid)
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

    private RefillEventLogDto toEventDTO(RefillEventLog e) {
        return RefillEventLogDto.builder()
                .uid(e.getUid())
                .docRefillUid(e.getDocRefillUid())
                .cellUid(e.getCellUid())
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

    private DocRefillDto toDTO(DocRefill doc) {
        Station station = doc.getStation();
        DocPattern template = doc.getTemplate();
        User createdBy = doc.getCreatedBy();

        DocRefillDto dto = DocRefillDto.builder()
                .uid(doc.getUid())
                .code(doc.getCode())
                .docDatetime(doc.getDocDatetime())
                .totalCells(doc.getTotalCells())
                .cellsToLoad(doc.getCellsToLoad())
                .totalQuantity(doc.getTotalQuantity())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .stationUid(doc.getStationUid())
                .stationName(station != null ? station.getName() : null)
                .stationCode(station != null ? station.getCode() : null)
                .isTmc(station != null && Boolean.TRUE.equals(station.getIsTmc()))
                .isSgd(station != null && Boolean.TRUE.equals(station.getIsSgd()))
                .isOk(station != null && Boolean.TRUE.equals(station.getIsOk()))
                .accountingLabel(station != null ? buildAccountingLabel(station) : null)
                .templateUid(template != null ? template.getUid() : null)
                .templateName(template != null ? template.getNamePattern() : null)
                .templateNumber(template != null ? template.getNumber() : null)
                .templateCategoryName(template != null && template.getCategory() != null
                        ? template.getCategory().getName() : null)
                .configurationUid(doc.getConfigurationUid())
                .modelUid(doc.getModelUid())
                .createdByUserId(createdBy != null ? createdBy.getId() : null)
                .createdByName(buildUserName(createdBy))
                .build();

        if (doc.getConfigurationUid() != null) {
            stationConfigurationRepository.findById(doc.getConfigurationUid()).ifPresent(cfg -> {
                dto.setConfigurationName(cfg.getName());
                dto.setCellsStructureSnapshot(cfg.getCellsStructure());
                if (cfg.getModel() != null) {
                    dto.setModelName(cfg.getModel().getName());
                    if (dto.getModelUid() == null) dto.setModelUid(cfg.getModel().getUid());
                }
            });
        }

        List<RegRefillCell> cells = cellRepository.findByDocUid(doc.getUid());
        dto.setCells(cells.stream().map(this::toCellDTO).collect(Collectors.toList()));
        return dto;
    }

    private RefillCellDto buildCellDtoFromTemplate(RegCells tc, Integer factQty) {
        SprCellAssignment assignment = tc.getCellAssignment();
        SprMaterial material = tc.getMaterial();

        return RefillCellDto.builder()
                .uid(null)
                .drumNumber(tc.getDrumNumber())
                .columnNumber(tc.getColumnNumber())
                .numberCell(tc.getNumberCell())
                .cellAssignmentUid(assignment != null ? assignment.getUid() : null)
                .cellAssignmentName(assignment != null ? assignment.getName() : null)
                .cellAssignmentTypeUid(assignment != null && assignment.getType() != null
                        ? assignment.getType().getUid() : null)
                .cellAssignmentTypeName(assignment != null && assignment.getType() != null
                        ? assignment.getType().getTypeName() : null)
                .materialUid(material != null ? material.getUid() : null)
                .materialName(material != null ? material.getNameMaterial() : null)
                .materialArticle(material != null ? material.getArticle() : null)
                .materialCode(material != null ? material.getCodeMaterial() : null)
                .materialGroupName(material != null && material.getGroupMaterial() != null
                        ? material.getGroupMaterial().getGroupName() : null)
                .materialMeasureName(material != null && material.getMeasure() != null
                        ? material.getMeasure().getName() : null)
                .materialUsage(material != null ? material.getUsage() : null)
                .materialTypeName(material != null && material.getTypeMain() != null
                        ? material.getTypeMain().getTypeName() : null)
                .materialPurposeName(material != null && material.getTypePurpose() != null
                        ? material.getTypePurpose().getTypeName() : null)
                .materialProductName(material != null && material.getTypeProduct() != null
                        ? material.getTypeProduct().getTypeName() : null)
                .materialManufacturerName(material != null && material.getManufacturer() != null
                        ? material.getManufacturer().getName() : null)
                .materialCountryName(material != null && material.getCountry() != null
                        ? material.getCountry().getName() : null)
                .materialBrandName(material != null && material.getBrand() != null
                        ? material.getBrand().getName() : null)
                .materialModelName(material != null && material.getModelOfBrand() != null
                        ? material.getModelOfBrand().getName() : null)
                .templateQuantity(tc.getQuantity())
                .factQuantity(factQty)
                .refillQuantity(null)
                .returnToThisCell(tc.getReturnToThisCell() != null && tc.getReturnToThisCell())
                .isIndividual(tc.getIsIndividual() != null && tc.getIsIndividual())
                .build();
    }

    private RefillCellDto toCellDTO(RegRefillCell c) {
        SprCellAssignment assignment = c.getCellAssignment();
        SprMaterial material = c.getMaterial();

        Integer stockAvailable = null;
        Integer stockMinStock = null;
        Integer stockCriticalStock = null;
        if (material != null) {
            StockBalanceDto bal = stockRegisterService.getBalanceByMaterial(material.getUid(), null);
            if (bal != null) {
                stockAvailable = bal.getCleanQuantity();
                stockMinStock = bal.getMinStock();
                stockCriticalStock = bal.getCriticalStock();
            }
        }

        return RefillCellDto.builder()
                .uid(c.getUid())
                .drumNumber(c.getDrumNumber())
                .columnNumber(c.getColumnNumber())
                .numberCell(c.getNumberCell())
                .cellAssignmentUid(assignment != null ? assignment.getUid() : null)
                .cellAssignmentName(assignment != null ? assignment.getName() : null)
                .cellAssignmentTypeUid(assignment != null && assignment.getType() != null
                        ? assignment.getType().getUid() : null)
                .cellAssignmentTypeName(assignment != null && assignment.getType() != null
                        ? assignment.getType().getTypeName() : null)
                .materialUid(material != null ? material.getUid() : null)
                .materialName(material != null ? material.getNameMaterial() : null)
                .materialArticle(material != null ? material.getArticle() : null)
                .materialCode(material != null ? material.getCodeMaterial() : null)
                .materialGroupName(material != null && material.getGroupMaterial() != null
                        ? material.getGroupMaterial().getGroupName() : null)
                .materialMeasureName(material != null && material.getMeasure() != null
                        ? material.getMeasure().getName() : null)
                .materialUsage(material != null ? material.getUsage() : null)
                .materialTypeName(material != null && material.getTypeMain() != null
                        ? material.getTypeMain().getTypeName() : null)
                .materialPurposeName(material != null && material.getTypePurpose() != null
                        ? material.getTypePurpose().getTypeName() : null)
                .materialProductName(material != null && material.getTypeProduct() != null
                        ? material.getTypeProduct().getTypeName() : null)
                .materialManufacturerName(material != null && material.getManufacturer() != null
                        ? material.getManufacturer().getName() : null)
                .materialCountryName(material != null && material.getCountry() != null
                        ? material.getCountry().getName() : null)
                .materialBrandName(material != null && material.getBrand() != null
                        ? material.getBrand().getName() : null)
                .materialModelName(material != null && material.getModelOfBrand() != null
                        ? material.getModelOfBrand().getName() : null)
                .templateQuantity(c.getTemplateQuantity())
                .factQuantity(c.getFactQuantity())
                .refillQuantity(c.getRefillQuantity())
                .minStockSnapshot(c.getMinStockSnapshot())
                .criticalStockSnapshot(c.getCriticalStockSnapshot())
                .stockControlExists(c.getStockControlExists())
                .stockAvailable(stockAvailable)
                .stockMinStock(stockMinStock)
                .stockCriticalStock(stockCriticalStock)
                .returnToThisCell(c.getReturnToThisCell())
                .isIndividual(c.getIsIndividual())
                .build();
    }
}