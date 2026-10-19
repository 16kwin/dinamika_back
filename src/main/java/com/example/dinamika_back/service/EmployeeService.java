// EmployeeService.java
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
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final WorkshopRepository workshopRepository;
    private final SectionRepository sectionRepository;
    private final EmployeeEventLogRepository eventLogRepository;
    private final EmployeeColumnSettingsService columnSettingsService;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> ALL_COLUMNS_ORDER = List.of(
            "lastName", "firstName", "middleName", "profession", "scopeName"
    );
    private static final Set<String> REQUIRED_COLUMNS = new LinkedHashSet<>(List.of("lastName"));

    public static final String PROFESSION_SECTION_HEAD     = "SECTION_HEAD";
    public static final String PROFESSION_WORKSHOP_HEAD    = "WORKSHOP_HEAD";
    public static final String PROFESSION_PRODUCTION_HEAD  = "PRODUCTION_HEAD";

    // ==================== GET ALL WITH SETTINGS ====================

    public EmployeeListResponse getAllWithSettings(Integer userId) {
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

        List<EmployeeFlatDto> employees = employeeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        EmployeeListResponse response = new EmployeeListResponse();
        response.setColumns(orderedColumns);
        response.setData(employees);
        response.setColumnWidths(columnWidths);
        response.setRequiredColumns(new ArrayList<>(requiredColumns));
        return response;
    }

    private void parseColumnSettings(String json, Set<String> visibleColumns, Map<String, Double> columnWidths, Set<String> requiredColumns) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            visibleColumns.clear();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof Boolean) {
                    if ((Boolean) value) {
                        visibleColumns.add(key);
                    }
                } else if (value instanceof Map) {
                    Map<String, Object> settings = (Map<String, Object>) value;
                    Object visible = settings.get("visible");
                    Object width = settings.get("width");
                    Object required = settings.get("required");

                    if (visible instanceof Boolean && (Boolean) visible) {
                        visibleColumns.add(key);
                    }
                    if (width instanceof Number) {
                        columnWidths.put(key, ((Number) width).doubleValue());
                    }
                    if (required instanceof Boolean && (Boolean) required) {
                        requiredColumns.add(key);
                    }
                }
            }
        } catch (Exception e) {
            visibleColumns.clear();
            visibleColumns.addAll(ALL_COLUMNS_ORDER);
        }
    }

    // ==================== CRUD ====================

    public List<EmployeeFlatDto> getAll() {
        return employeeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public EmployeeFlatDto getById(UUID uid) {
        Employee employee = employeeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден: " + uid));
        return toDTO(employee);
    }

    @Transactional
    public EmployeeFlatDto create(CreateEmployeeRequest request) {
        validateProfession(request.getProfession());

        Employee employee = new Employee();
        employee.setUid(UUID.randomUUID());
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setMiddleName(request.getMiddleName());
        employee.setProfession(request.getProfession());

        // 1. Сначала сохраняем и сразу флашим — чтобы запись физически была в БД
        employee = employeeRepository.saveAndFlush(employee);

        // 2. Только потом привязка (при isCreate=true она не пишет лог)
        applyScope(employee, request.getProfession(),
                request.getEnterpriseId(), request.getWorkshopId(), request.getSectionId(), true);

        employee = employeeRepository.save(employee);

        logEvent(employee.getUid(), "CREATE",
                "Создание сотрудника: '" + buildFio(employee) + "'",
                null, null, null, userService.getCurrentUsername());

        return toDTO(employee);
    }

    @Transactional
    public EmployeeFlatDto update(UUID uid, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден: " + uid));

        String author = userService.getCurrentUsername();
        validateProfession(request.getProfession());

        // ФИО
        if (!Objects.equals(request.getLastName(), employee.getLastName())) {
            logEvent(uid, "UPDATE",
                    "'" + buildFio(employee) + "': Значение поля 'Фамилия' изменено с '" + employee.getLastName() + "' на '" + request.getLastName() + "'",
                    "Фамилия", employee.getLastName(), request.getLastName(), author);
            employee.setLastName(request.getLastName());
        }
        if (!Objects.equals(request.getFirstName(), employee.getFirstName())) {
            logEvent(uid, "UPDATE",
                    "'" + buildFio(employee) + "': Значение поля 'Имя' изменено с '" + employee.getFirstName() + "' на '" + request.getFirstName() + "'",
                    "Имя", employee.getFirstName(), request.getFirstName(), author);
            employee.setFirstName(request.getFirstName());
        }
        if (!Objects.equals(request.getMiddleName(), employee.getMiddleName())) {
            logEvent(uid, "UPDATE",
                    "'" + buildFio(employee) + "': Значение поля 'Отчество' изменено с '" + employee.getMiddleName() + "' на '" + request.getMiddleName() + "'",
                    "Отчество", employee.getMiddleName(), request.getMiddleName(), author);
            employee.setMiddleName(request.getMiddleName());
        }

        // Профессия
        boolean professionChanged = !Objects.equals(request.getProfession(), employee.getProfession());
        if (professionChanged) {
            String oldProfessionRu = professionToRu(employee.getProfession());
            String newProfessionRu = professionToRu(request.getProfession());
            logEvent(uid, "UPDATE",
                    "'" + buildFio(employee) + "': Значение поля 'Профессия' изменено с '" + oldProfessionRu + "' на '" + newProfessionRu + "'",
                    "Профессия", oldProfessionRu, newProfessionRu, author);
            employee.setProfession(request.getProfession());

            // При смене профессии сбрасываем старую привязку
            String oldScopeName = getScopeName(employee);
            if (oldScopeName != null) {
                logEvent(uid, "UPDATE",
                        "'" + buildFio(employee) + "': Значение поля 'Объект' изменено с '" + oldScopeName + "' на 'null' (смена профессии)'",
                        "Объект", oldScopeName, null, author);
            }
            employee.setEnterprise(null);
            employee.setWorkshop(null);
            employee.setSection(null);
        }

        // Привязка (isCreate=false → пишет лог при смене)
        applyScope(employee, request.getProfession(),
                request.getEnterpriseId(), request.getWorkshopId(), request.getSectionId(), false);

        employee = employeeRepository.save(employee);
        return toDTO(employee);
    }

    @Transactional
    public void delete(UUID uid) {
        Employee employee = employeeRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден: " + uid));

        logEvent(uid, "DELETE",
                "Удаление сотрудника: '" + buildFio(employee) + "'",
                null, buildFio(employee), null, userService.getCurrentUsername());

        employeeRepository.delete(employee);
    }

    // ==================== EVENTS ====================

    public List<EmployeeEventLogDto> getEvents(UUID uid) {
        return eventLogRepository.findByEmployeeUidOrderByCreatedAtDesc(uid).stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    public List<EmployeeEventLogDto> getAllEvents() {
        return eventLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE ====================

    private void validateProfession(String profession) {
        if (!PROFESSION_SECTION_HEAD.equals(profession)
                && !PROFESSION_WORKSHOP_HEAD.equals(profession)
                && !PROFESSION_PRODUCTION_HEAD.equals(profession)) {
            throw new RuntimeException("Недопустимая профессия: " + profession);
        }
    }

    private void applyScope(Employee employee, String profession,
                            Long enterpriseId, Long workshopId, Long sectionId,
                            boolean isCreate) {
        switch (profession) {
            case PROFESSION_PRODUCTION_HEAD -> {
                if (enterpriseId == null) {
                    throw new RuntimeException("Для профессии 'Начальник производства' обязательно указать предприятие");
                }
                Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                        .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + enterpriseId));
                if (!isCreate && (employee.getEnterprise() == null || !employee.getEnterprise().getId().equals(enterpriseId))) {
                    logScopeChange(employee, enterprise.getName());
                }
                employee.setEnterprise(enterprise);
                employee.setWorkshop(null);
                employee.setSection(null);
            }
            case PROFESSION_WORKSHOP_HEAD -> {
                if (workshopId == null) {
                    throw new RuntimeException("Для профессии 'Начальник цеха' обязательно указать цех");
                }
                Workshop workshop = workshopRepository.findById(workshopId)
                        .orElseThrow(() -> new RuntimeException("Цех не найден: " + workshopId));
                if (!isCreate && (employee.getWorkshop() == null || !employee.getWorkshop().getId().equals(workshopId))) {
                    logScopeChange(employee, workshop.getName());
                }
                employee.setWorkshop(workshop);
                employee.setEnterprise(null);
                employee.setSection(null);
            }
            case PROFESSION_SECTION_HEAD -> {
                if (sectionId == null) {
                    throw new RuntimeException("Для профессии 'Начальник участка' обязательно указать участок");
                }
                Section section = sectionRepository.findById(sectionId)
                        .orElseThrow(() -> new RuntimeException("Участок не найден: " + sectionId));
                if (!isCreate && (employee.getSection() == null || !employee.getSection().getId().equals(sectionId))) {
                    logScopeChange(employee, section.getName());
                }
                employee.setSection(section);
                employee.setEnterprise(null);
                employee.setWorkshop(null);
            }
        }
    }

    private void logScopeChange(Employee employee, String newScopeName) {
        String oldName = getScopeName(employee);
        if (oldName != null && !oldName.equals(newScopeName)) {
            logEvent(employee.getUid(), "UPDATE",
                    "'" + buildFio(employee) + "': Значение поля 'Объект' изменено с '" + oldName + "' на '" + newScopeName + "'",
                    "Объект", oldName, newScopeName, userService.getCurrentUsername());
        } else if (oldName == null) {
            logEvent(employee.getUid(), "UPDATE",
                    "'" + buildFio(employee) + "': Значение поля 'Объект' изменено с 'null' на '" + newScopeName + "'",
                    "Объект", null, newScopeName, userService.getCurrentUsername());
        }
    }

    private String getScopeName(Employee e) {
        if (e.getEnterprise() != null) return e.getEnterprise().getName();
        if (e.getWorkshop() != null)   return e.getWorkshop().getName();
        if (e.getSection() != null)    return e.getSection().getName();
        return null;
    }

    private String buildFio(Employee e) {
        StringBuilder sb = new StringBuilder();
        if (e.getLastName() != null)   sb.append(e.getLastName());
        if (e.getFirstName() != null)  sb.append(sb.length() > 0 ? " " : "").append(e.getFirstName());
        if (e.getMiddleName() != null) sb.append(sb.length() > 0 ? " " : "").append(e.getMiddleName());
        return sb.toString();
    }

    private String professionToRu(String code) {
        if (code == null) return null;
        return switch (code) {
            case PROFESSION_SECTION_HEAD     -> "Начальник участка";
            case PROFESSION_WORKSHOP_HEAD    -> "Начальник цеха";
            case PROFESSION_PRODUCTION_HEAD  -> "Начальник производства";
            default -> code;
        };
    }

    private void logEvent(UUID employeeUid, String eventType, String description,
                          String fieldName, String oldValue, String newValue, String author) {
        EmployeeEventLog log = EmployeeEventLog.builder()
                .uid(UUID.randomUUID())
                .employeeUid(employeeUid)
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

    private EmployeeEventLogDto toEventDTO(EmployeeEventLog e) {
        return EmployeeEventLogDto.builder()
                .uid(e.getUid())
                .employeeUid(e.getEmployeeUid())
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

    private EmployeeFlatDto toDTO(Employee employee) {
        EmployeeFlatDto dto = EmployeeFlatDto.builder()
                .uid(employee.getUid())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .middleName(employee.getMiddleName())
                .profession(employee.getProfession())
                .build();

        if (employee.getEnterprise() != null) {
            dto.setEnterpriseId(employee.getEnterprise().getId());
            dto.setEnterpriseName(employee.getEnterprise().getName());
        }
        if (employee.getWorkshop() != null) {
            dto.setWorkshopId(employee.getWorkshop().getId());
            dto.setWorkshopName(employee.getWorkshop().getName());
        }
        if (employee.getSection() != null) {
            dto.setSectionId(employee.getSection().getId());
            dto.setSectionName(employee.getSection().getName());
        }
        return dto;
    }
}