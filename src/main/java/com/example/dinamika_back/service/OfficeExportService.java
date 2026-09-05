package com.example.dinamika_back.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OfficeExportService {

    private static final float MAX_FONT_SIZE = 9f;
    private static final float MIN_FONT_SIZE = 5f;
    private static final int MIN_CHAR_LENGTH = 4;
    private static final float WORD_PAGE_WIDTH = 550f; // приблизительная ширина A4 с полями в Word

    // ==================== Excel ====================
    public byte[] exportExcel(String title,
            List<String> columns,
            List<String> columnLabels,
            List<Map<String, Object>> data,
            List<String> footerLines) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(title.length() > 31 ? title.substring(0, 31) : title);

            int colCount = columns.size();

            // Собираем все тексты ячеек (заголовки + данные)
            String[][] cellTexts = new String[data.size() + 1][colCount];
            for (int i = 0; i < colCount; i++) {
                cellTexts[0][i] = (columnLabels != null && i < columnLabels.size()) ? columnLabels.get(i)
                        : columns.get(i);
            }
            for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
                Map<String, Object> rowData = data.get(rowIdx);
                for (int i = 0; i < colCount; i++) {
                    Object val = rowData.get(columns.get(i));
                    cellTexts[rowIdx + 1][i] = val != null ? val.toString() : "";
                }
            }

            // Определяем максимальную длину слова для каждой колонки
            int[] maxWordLengths = new int[colCount];
            for (int i = 0; i < colCount; i++) {
                int maxLen = 0;
                for (int row = 0; row < cellTexts.length; row++) {
                    String text = cellTexts[row][i];
                    if (text != null && !text.isEmpty()) {
                        String[] words = text.split("\\s+");
                        for (String word : words) {
                            if (word.length() > maxLen)
                                maxLen = word.length();
                        }
                    }
                }
                if (maxLen < MIN_CHAR_LENGTH)
                    maxLen = MIN_CHAR_LENGTH;
                maxWordLengths[i] = maxLen;
            }

            // Подбираем единый размер шрифта
            float fontSize = MAX_FONT_SIZE;
            boolean fits = false;
            while (fontSize >= MIN_FONT_SIZE && !fits) {
                fits = true;
                int allowed = (int) (30 + (MAX_FONT_SIZE - fontSize) * 3);
                for (int i = 0; i < colCount; i++) {
                    if (maxWordLengths[i] > allowed) {
                        fits = false;
                        break;
                    }
                }
                if (!fits)
                    fontSize -= 0.5f;
            }
            if (!fits)
                fontSize = MIN_FONT_SIZE;

            // Стиль для информационных строк
            Font infoFont = workbook.createFont();
            infoFont.setFontName("Arial");
            infoFont.setFontHeightInPoints((short) fontSize);
            CellStyle infoStyle = workbook.createCellStyle();
            infoStyle.setFont(infoFont);
            infoStyle.setAlignment(HorizontalAlignment.LEFT);

            // Стиль заголовков
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontName("Arial");
            headerFont.setFontHeightInPoints((short) fontSize);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setWrapText(false);

            // Стиль данных
            Font dataFont = workbook.createFont();
            dataFont.setFontName("Arial");
            dataFont.setFontHeightInPoints((short) fontSize);
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setFont(dataFont);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setWrapText(false);

            int rowIndex = 0;

            // --- Вывод сортировки и фильтров (слева) ---
            if (footerLines != null && !footerLines.isEmpty()) {
                for (String line : footerLines) {
                    Row row = sheet.createRow(rowIndex++);
                    Cell cell = row.createCell(0);
                    cell.setCellValue(line);
                    cell.setCellStyle(infoStyle);
                }
                rowIndex++; // пустая строка
            }

            // Заголовки
            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < colCount; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cellTexts[0][i]);
                cell.setCellStyle(headerStyle);
            }

            // Данные
            for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < colCount; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(cellTexts[rowIdx + 1][i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            // Автоширина колонок
            for (int i = 0; i < colCount; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                width += 512;
                if (width > 20000)
                    width = 20000;
                sheet.setColumnWidth(i, width);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ==================== Word ====================
    public byte[] exportWord(String title,
            List<String> columns,
            List<String> columnLabels,
            List<Map<String, Object>> data,
            List<String> footerLines) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            // Заголовок документа
            var titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            var titleRun = titleParagraph.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontFamily("Arial");
            titleRun.setFontSize(16);
            document.createParagraph();

            int colCount = columns.size();

            // Собираем все тексты
            String[][] cellTexts = new String[data.size() + 1][colCount];
            for (int i = 0; i < colCount; i++) {
                cellTexts[0][i] = (columnLabels != null && i < columnLabels.size()) ? columnLabels.get(i)
                        : columns.get(i);
            }
            for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
                Map<String, Object> rowData = data.get(rowIdx);
                for (int i = 0; i < colCount; i++) {
                    Object val = rowData.get(columns.get(i));
                    cellTexts[rowIdx + 1][i] = val != null ? val.toString() : "";
                }
            }

            // Для каждой колонки находим самое длинное слово и его длину (в символах)
            int[] maxWordLengths = new int[colCount];
            for (int i = 0; i < colCount; i++) {
                int maxLen = 0;
                for (int row = 0; row < cellTexts.length; row++) {
                    String text = cellTexts[row][i];
                    if (text != null && !text.isEmpty()) {
                        String[] words = text.split("\\s+");
                        for (String word : words) {
                            if (word.length() > maxLen)
                                maxLen = word.length();
                        }
                    }
                }
                if (maxLen < MIN_CHAR_LENGTH)
                    maxLen = MIN_CHAR_LENGTH;
                maxWordLengths[i] = maxLen;
            }

            // Оцениваем ширину каждого слова в пунктах при 9pt (приблизительно: 1 символ ~
            // 5pt)
            float[] wordWidthsAt9pt = new float[colCount];
            float totalWidthAt9pt = 0;
            for (int i = 0; i < colCount; i++) {
                float width = maxWordLengths[i] * 5f; // грубая оценка
                wordWidthsAt9pt[i] = width;
                totalWidthAt9pt += width;
            }

            // Определяем реальный размер шрифта
            float fontSize = MAX_FONT_SIZE;
            if (totalWidthAt9pt > WORD_PAGE_WIDTH) {
                fontSize = MAX_FONT_SIZE * (WORD_PAGE_WIDTH / totalWidthAt9pt);
                if (fontSize < MIN_FONT_SIZE)
                    fontSize = MIN_FONT_SIZE;
            }

            // Вычисляем веса колонок (в процентах)
            float[] columnWeights = new float[colCount];
            if (totalWidthAt9pt > 0) {
                for (int i = 0; i < colCount; i++) {
                    columnWeights[i] = (wordWidthsAt9pt[i] / totalWidthAt9pt) * 100;
                }
            } else {
                for (int i = 0; i < colCount; i++)
                    columnWeights[i] = 100f / colCount;
            }
            float sum = 0;
            for (float w : columnWeights)
                sum += w;
            for (int i = 0; i < colCount; i++)
                columnWeights[i] = (columnWeights[i] / sum) * 100;

            // --- Вывод сортировки и фильтров (слева, тем же шрифтом, что и таблица) ---
            if (footerLines != null && !footerLines.isEmpty()) {
                for (String line : footerLines) {
                    var p = document.createParagraph();
                    p.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
                    var run = p.createRun();
                    run.setText(line);
                    run.setFontFamily("Arial");
                    run.setFontSize((int) fontSize);
                }
                document.createParagraph();
            }

            // Создаём таблицу
            XWPFTable table = document.createTable(data.size() + 1, colCount);
            table.setWidth("100%");

            // Устанавливаем ширину колонок (используем Locale.US)
            XWPFTableRow headerRow = table.getRow(0);
            for (int i = 0; i < colCount; i++) {
                var cell = headerRow.getCell(i);
                String widthPercent = String.format(Locale.US, "%.2f%%", columnWeights[i]);
                cell.setWidth(widthPercent);
            }

            // Заголовки
            for (int i = 0; i < colCount; i++) {
                var cell = headerRow.getCell(i);
                cell.setText(cellTexts[0][i]);
                var paragraph = cell.getParagraphs().get(0);
                paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
                var run = paragraph.getRuns().get(0);
                run.setBold(true);
                run.setFontFamily("Arial");
                run.setFontSize((int) fontSize);
            }

            // Данные
            for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
                XWPFTableRow row = table.getRow(rowIdx + 1);
                for (int i = 0; i < colCount; i++) {
                    var cell = row.getCell(i);
                    cell.setText(cellTexts[rowIdx + 1][i]);
                    var paragraph = cell.getParagraphs().get(0);
                    paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
                    var run = paragraph.getRuns().get(0);
                    run.setFontFamily("Arial");
                    run.setFontSize((int) fontSize);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }
}