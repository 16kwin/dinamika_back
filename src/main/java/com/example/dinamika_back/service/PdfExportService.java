package com.example.dinamika_back.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class PdfExportService {

    private static final float MAX_FONT_SIZE = 9f;
    private static final float MIN_FONT_SIZE = 5f;
    private static final float PAGE_MARGIN = 20f;
    private static final float PAGE_WIDTH_PORTRAIT = 595f - 2 * PAGE_MARGIN;
    private static final float PAGE_WIDTH_LANDSCAPE = 842f - 2 * PAGE_MARGIN;
    private static final float EXTRA_PADDING = 4f;

    public byte[] generatePdf(String title,
            List<String> columns,
            List<String> columnLabels,
            List<Map<String, Object>> data,
            boolean landscape,
            List<String> footerLines) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ЯВНО УКАЗЫВАЕМ ПОЛНОЕ ИМЯ КЛАССА com.lowagie.text.Rectangle
        com.lowagie.text.Rectangle pageSize = landscape ? PageSize.A4.rotate() : PageSize.A4;
        Document document = new Document(pageSize, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont baseFont = loadBaseFont();
        Font titleFont = new Font(baseFont, 16, Font.BOLD);
        Font footerFont = new Font(baseFont, 10, Font.ITALIC);

        // Заголовок
        Paragraph titleParagraph = new Paragraph(title, titleFont);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        document.add(titleParagraph);
        document.add(new Paragraph(" "));

        // Фильтры/сортировка
        if (footerLines != null && !footerLines.isEmpty()) {
            List<String> meaningful = footerLines.stream()
                    .filter(line -> !line.startsWith("Видимые поля") && !line.startsWith("Невидимые поля"))
                    .filter(line -> {
                        if (line.startsWith("Сортировка:"))
                            return !line.contains("Без сортировки");
                        if (line.startsWith("Фильтры:"))
                            return !line.contains("Нет фильтров");
                        return true;
                    })
                    .toList();
            if (!meaningful.isEmpty()) {
                for (String line : meaningful) {
                    Paragraph p = new Paragraph(line, footerFont);
                    p.setAlignment(Element.ALIGN_LEFT);
                    document.add(p);
                }
                document.add(new Paragraph(" "));
            }
        }

        int colCount = columns.size();

        // Собираем все тексты ячеек (заголовки + данные)
        String[][] cellTexts = new String[data.size() + 1][colCount];
        for (int i = 0; i < colCount; i++) {
            cellTexts[0][i] = (columnLabels != null && i < columnLabels.size()) ? columnLabels.get(i) : columns.get(i);
        }
        for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
            Map<String, Object> row = data.get(rowIdx);
            for (int i = 0; i < colCount; i++) {
                Object val = row.get(columns.get(i));
                cellTexts[rowIdx + 1][i] = val != null ? val.toString() : "";
            }
        }

        // Для каждой колонки находим самое длинное слово (по ширине при опорном
        // размере)
        float[] maxWordWidths = new float[colCount];
        for (int i = 0; i < colCount; i++) {
            float maxW = 0;
            for (int row = 0; row < cellTexts.length; row++) {
                String text = cellTexts[row][i];
                if (text != null && !text.isEmpty()) {
                    String[] words = text.split("\\s+");
                    for (String word : words) {
                        if (!word.isEmpty()) {
                            float w = getTextWidth(word, MAX_FONT_SIZE, baseFont);
                            if (w > maxW)
                                maxW = w;
                        }
                    }
                }
            }
            maxWordWidths[i] = maxW + EXTRA_PADDING;
        }

        // Минимальная ширина колонки
        float MIN_COLUMN_WIDTH = 30f;
        for (int i = 0; i < colCount; i++) {
            if (maxWordWidths[i] < MIN_COLUMN_WIDTH) {
                maxWordWidths[i] = MIN_COLUMN_WIDTH;
            }
        }

        float totalWidth = 0;
        for (float w : maxWordWidths)
            totalWidth += w;

        float pageWidth = landscape ? PAGE_WIDTH_LANDSCAPE : PAGE_WIDTH_PORTRAIT;

        // Определяем размер шрифта
        float fontSize = MAX_FONT_SIZE;
        if (totalWidth > pageWidth) {
            fontSize = MAX_FONT_SIZE * (pageWidth / totalWidth);
            if (fontSize < MIN_FONT_SIZE)
                fontSize = MIN_FONT_SIZE;
        }

        // Вычисляем веса колонок (в процентах)
        float[] columnWeights = new float[colCount];
        if (totalWidth > 0) {
            for (int i = 0; i < colCount; i++) {
                columnWeights[i] = (maxWordWidths[i] / totalWidth) * 100;
            }
        } else {
            for (int i = 0; i < colCount; i++)
                columnWeights[i] = 100f / colCount;
        }

        // Нормализуем
        float sum = 0;
        for (float w : columnWeights)
            sum += w;
        for (int i = 0; i < colCount; i++)
            columnWeights[i] = (columnWeights[i] / sum) * 100;

        // Создаём таблицу
        PdfPTable table = new PdfPTable(colCount);
        table.setWidthPercentage(100);
        table.setWidths(columnWeights);

        // Шрифт для всех ячеек
        Font cellFont = new Font(baseFont, fontSize, Font.NORMAL);
        Font headerFont = new Font(baseFont, fontSize, Font.BOLD, new Color(64, 64, 64));

        // Заголовки
        for (int i = 0; i < colCount; i++) {
            String header = cellTexts[0][i];
            PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
            cell.setBackgroundColor(new Color(240, 240, 240));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(1f);
            table.addCell(cell);
        }

        // Данные
        for (int rowIdx = 1; rowIdx < cellTexts.length; rowIdx++) {
            for (int i = 0; i < colCount; i++) {
                String text = cellTexts[rowIdx][i];
                PdfPCell cell = new PdfPCell(new Paragraph(text, cellFont));
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(1f);
                table.addCell(cell);
            }
        }

        document.add(table);
        document.close();
        return out.toByteArray();
    }

    private float getTextWidth(String text, float fontSize, BaseFont baseFont) {
        if (text == null || text.isEmpty())
            return 0;
        try {
            return baseFont.getWidthPoint(text, fontSize);
        } catch (Exception e) {
            return text.length() * fontSize * 0.6f;
        }
    }

    private BaseFont loadBaseFont() throws Exception {
        try (InputStream fontStream = getClass().getClassLoader().getResourceAsStream("fonts/arial.ttf")) {
            if (fontStream == null) {
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            }
            byte[] fontBytes = fontStream.readAllBytes();
            return BaseFont.createFont("arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    BaseFont.CACHED, fontBytes, null);
        }
    }
}