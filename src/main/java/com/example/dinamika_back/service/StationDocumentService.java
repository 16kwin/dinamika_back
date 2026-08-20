// StationDocumentService.java — ПОЛНЫЙ ФАЙЛ (добавлены логирование и переименование)
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.StationDocumentDto;
import com.example.dinamika_back.model.StationDocument;
import com.example.dinamika_back.model.StationEventLog;
import com.example.dinamika_back.repository.StationDocumentRepository;
import com.example.dinamika_back.repository.StationEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationDocumentService {

    private final StationDocumentRepository documentRepository;
    private final StationEventLogRepository eventLogRepository;
    private final UserService userService;

    private static final String UPLOAD_DIR = "uploads/stations/";

    private Path getStationDir(String stationUid) throws IOException {
        Path dir = Path.of(UPLOAD_DIR, stationUid);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    private String saveFile(String stationUid, MultipartFile file) throws IOException {
        Path dir = getStationDir(stationUid);
        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + extension;
        Path filePath = dir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    private void deleteFile(String stationUid, String fileName) {
        try {
            Path filePath = Path.of(UPLOAD_DIR, stationUid, fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private String getFileUrl(String stationUid, String filePath) {
        return "/uploads/stations/" + stationUid + "/" + filePath;
    }

    public List<StationDocumentDto> getDocuments(String stationUid) {
        return documentRepository.findByStationUidOrderByCreatedAtDesc(stationUid).stream()
                .map(doc -> StationDocumentDto.builder()
                        .uid(doc.getUid())
                        .stationUid(stationUid)
                        .documentName(doc.getDocumentName())
                        .filePath(doc.getFilePath())
                        .originalName(doc.getOriginalName())
                        .url(getFileUrl(stationUid, doc.getFilePath()))
                        .createdAt(doc.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public StationDocumentDto uploadDocument(String stationUid, String documentName, MultipartFile file) {
        try {
            String fileName = saveFile(stationUid, file);

            StationDocument document = StationDocument.builder()
                    .uid(UUID.randomUUID())
                    .stationUid(stationUid)
                    .documentName(documentName)
                    .filePath(fileName)
                    .originalName(file.getOriginalFilename())
                    .createdAt(LocalDateTime.now())
                    .build();

            documentRepository.save(document);
            
            logEvent(stationUid, "DOCUMENT_ADD", "Добавлен документ: '" + documentName + "'", null, null, null, userService.getCurrentUsername());

            return StationDocumentDto.builder()
                    .uid(document.getUid())
                    .stationUid(stationUid)
                    .documentName(document.getDocumentName())
                    .filePath(document.getFilePath())
                    .originalName(document.getOriginalName())
                    .url(getFileUrl(stationUid, fileName))
                    .createdAt(document.getCreatedAt())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка загрузки файла", e);
        }
    }

    @Transactional
    public StationDocumentDto renameDocument(String stationUid, String documentUid, String newDocumentName) {
        StationDocument document = documentRepository.findById(UUID.fromString(documentUid))
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + documentUid));
        
        String oldName = document.getDocumentName();
        document.setDocumentName(newDocumentName);
        documentRepository.save(document);
        
        logEvent(stationUid, "DOCUMENT_RENAME", "Документ переименован с '" + oldName + "' на '" + newDocumentName + "'", null, null, null, userService.getCurrentUsername());
        
        return StationDocumentDto.builder()
                .uid(document.getUid())
                .stationUid(document.getStationUid())
                .documentName(document.getDocumentName())
                .filePath(document.getFilePath())
                .originalName(document.getOriginalName())
                .url(getFileUrl(document.getStationUid(), document.getFilePath()))
                .createdAt(document.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteDocument(String stationUid, UUID documentUid) {
        StationDocument document = documentRepository.findById(documentUid)
                .orElseThrow(() -> new RuntimeException("Документ не найден: " + documentUid));
        
        String docName = document.getDocumentName();
        deleteFile(document.getStationUid(), document.getFilePath());
        documentRepository.delete(document);
        
        logEvent(stationUid, "DOCUMENT_DELETE", "Удален документ: '" + docName + "'", null, null, null, userService.getCurrentUsername());
    }

    @Transactional
    public void deleteAllStationDocuments(String stationUid) {
        List<StationDocument> documents = documentRepository.findByStationUidOrderByCreatedAtDesc(stationUid);
        for (StationDocument doc : documents) {
            deleteFile(stationUid, doc.getFilePath());
        }
        documentRepository.deleteByStationUid(stationUid);
    }

    private void logEvent(String stationUid, String eventType, String description,
                         String fieldName, String oldValue, String newValue, String author) {
        StationEventLog log = StationEventLog.builder()
                .uid(UUID.randomUUID())
                .stationUid(stationUid)
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
}