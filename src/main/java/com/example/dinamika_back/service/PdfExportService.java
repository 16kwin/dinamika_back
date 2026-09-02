package com.example.dinamika_back.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class PdfExportService {

    public byte[] generatePdf(String title,
            List<String> columns,
            List<String> columnLabels,
            List<Map<String, Object>> data,
            boolean landscape,
            List<String> footerLines) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.lowagie.text.Document document = new com.lowagie.text.Document(
                landscape ? PageSize.A4.rotate() : PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont baseFont = loadBaseFont();
        Font titleFont = new Font(baseFont, 16, Font.BOLD);
        // Тёмно-серый цвет текста шапки (читаемый на светло-сером фоне)
        Font headerFont = new Font(baseFont, 10, Font.BOLD, new Color(64, 64, 64));
        Font cellFont = new Font(baseFont, 9, Font.NORMAL);

        Paragraph titleParagraph = new Paragraph(title, titleFont);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        document.add(titleParagraph);
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(columns.size());
        table.setWidthPercentage(100);
        float[] widths = new float[columns.size()];
        java.util.Arrays.fill(widths, 1f);
        table.setWidths(widths);

        // Заголовки: используем columnLabels, если передан
        for (int i = 0; i < columns.size(); i++) {
            String header = (columnLabels != null && i < columnLabels.size())
                    ? columnLabels.get(i)
                    : columns.get(i);
            PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
            cell.setBackgroundColor(new Color(240, 240, 240)); // светло-серый
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        // Данные: значения берём по ключам columns
        for (Map<String, Object> row : data) {
            for (String column : columns) {
                Object value = row.get(column);
                String text = value != null ? value.toString() : "";
                table.addCell(new Paragraph(text, cellFont));
            }
        }

        document.add(table);

        // Информационный блок (фильтры, сортировка, видимые/скрытые поля)
        if (footerLines != null && !footerLines.isEmpty()) {
            Font footerFont = new Font(baseFont, 10, Font.ITALIC);
            document.add(new Paragraph(" "));
            for (String line : footerLines) {
                document.add(new Paragraph(line, footerFont));
            }
        }

        document.close();
        return out.toByteArray();
    }

    private BaseFont loadBaseFont() throws Exception {
        try (InputStream fontStream = getClass().getClassLoader()
                .getResourceAsStream("fonts/arial.ttf")) {
            if (fontStream == null) {
                // Если шрифт не найден, используем стандартный (без кириллицы).
                // Обязательно положите arial.ttf в src/main/resources/fonts/
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            }
            byte[] fontBytes = fontStream.readAllBytes();
            return BaseFont.createFont("arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    BaseFont.CACHED, fontBytes, null);
        }
    }
}