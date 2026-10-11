package com.rag.chat.service;

import com.rag.chat.model.DocumentChunk;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    // Regex phát hiện tiêu đề section / chương / mục
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^(?:(?i:chương|section|phần|mục|bài|điều)\\s+[0-9IVXLCDMivxlcdm]+|\\d+(?:\\.\\d+)*\\.?\\s+[A-ZÀ-Ỹa-zà-ỹ]).*",
            Pattern.CASE_INSENSITIVE
    );

    public List<DocumentChunk> parseDocument(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "tailieu.pdf";
        }

        String lowerName = originalFilename.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return parsePdf(file.getInputStream(), originalFilename);
        } else if (lowerName.endsWith(".docx")) {
            return parseDocx(file.getInputStream(), originalFilename);
        } else if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            return parsePlainText(file.getInputStream(), originalFilename);
        } else {
            // Mặc định thử đọc dạng text hoặc pdf
            try {
                return parsePdf(file.getInputStream(), originalFilename);
            } catch (Exception e) {
                return parsePlainText(file.getInputStream(), originalFilename);
            }
        }
    }

    private List<DocumentChunk> parsePdf(InputStream inputStream, String fileName) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();
        try (PDDocument document = PDDocument.load(inputStream)) {
            int totalPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            String currentSection = "Phần mở đầu (Trang 1)";

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);

                if (pageText == null || pageText.isBlank()) {
                    continue;
                }

                // Tách theo dòng để phát hiện section heading
                String[] lines = pageText.split("\\r?\\n");
                StringBuilder chunkBuffer = new StringBuilder();

                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    if (isSectionHeader(line)) {
                        // Nếu trong buffer đã có nội dung, lưu chunk trước đó lại
                        if (!chunkBuffer.isEmpty()) {
                            chunks.add(DocumentChunk.builder()
                                    .fileName(fileName)
                                    .pageNumber(page)
                                    .section(currentSection)
                                    .text(chunkBuffer.toString().trim())
                                    .build());
                            chunkBuffer.setLength(0);
                        }
                        currentSection = line + " (Trang " + page + ")";
                    }

                    chunkBuffer.append(line).append(" ");

                    // Nếu kích thước chunk đạt khoảng 450 ký tự, tách thành 1 chunk
                    if (chunkBuffer.length() >= 450) {
                        chunks.add(DocumentChunk.builder()
                                .fileName(fileName)
                                .pageNumber(page)
                                .section(currentSection)
                                .text(chunkBuffer.toString().trim())
                                .build());
                        chunkBuffer.setLength(0);
                    }
                }

                if (!chunkBuffer.isEmpty()) {
                    chunks.add(DocumentChunk.builder()
                            .fileName(fileName)
                            .pageNumber(page)
                            .section(currentSection)
                            .text(chunkBuffer.toString().trim())
                            .build());
                }
            }
        }

        log.info("Đã parse PDF '{}': tổng số {} chunks", fileName, chunks.size());
        return chunks;
    }

    private List<DocumentChunk> parseDocx(InputStream inputStream, String fileName) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            String currentSection = "Phần mở đầu";
            StringBuilder chunkBuffer = new StringBuilder();

            int paragraphIndex = 0;
            for (XWPFParagraph p : doc.getParagraphs()) {
                paragraphIndex++;
                String text = p.getText().trim();
                if (text.isEmpty()) {
                    continue;
                }

                String style = p.getStyle();
                boolean isHeading = (style != null && style.toLowerCase().contains("heading")) || isSectionHeader(text);

                if (isHeading) {
                    if (!chunkBuffer.isEmpty()) {
                        chunks.add(DocumentChunk.builder()
                                .fileName(fileName)
                                .pageNumber(1 + paragraphIndex / 20)
                                .section(currentSection)
                                .text(chunkBuffer.toString().trim())
                                .build());
                        chunkBuffer.setLength(0);
                    }
                    currentSection = text;
                }

                chunkBuffer.append(text).append(" ");

                if (chunkBuffer.length() >= 450) {
                    chunks.add(DocumentChunk.builder()
                            .fileName(fileName)
                            .pageNumber(1 + paragraphIndex / 20)
                            .section(currentSection)
                            .text(chunkBuffer.toString().trim())
                            .build());
                    chunkBuffer.setLength(0);
                }
            }

            if (!chunkBuffer.isEmpty()) {
                chunks.add(DocumentChunk.builder()
                        .fileName(fileName)
                        .pageNumber(1 + paragraphIndex / 20)
                        .section(currentSection)
                        .text(chunkBuffer.toString().trim())
                        .build());
            }
        }
        return chunks;
    }

    private List<DocumentChunk> parsePlainText(InputStream inputStream, String fileName) throws IOException {
        List<DocumentChunk> chunks = new ArrayList<>();
        String currentSection = "Phần mở đầu";
        StringBuilder chunkBuffer = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (isSectionHeader(trimmed)) {
                    if (!chunkBuffer.isEmpty()) {
                        chunks.add(DocumentChunk.builder()
                                .fileName(fileName)
                                .pageNumber(1 + lineNumber / 40)
                                .section(currentSection)
                                .text(chunkBuffer.toString().trim())
                                .build());
                        chunkBuffer.setLength(0);
                    }
                    currentSection = trimmed;
                }

                chunkBuffer.append(trimmed).append(" ");
                if (chunkBuffer.length() >= 450) {
                    chunks.add(DocumentChunk.builder()
                            .fileName(fileName)
                            .pageNumber(1 + lineNumber / 40)
                            .section(currentSection)
                            .text(chunkBuffer.toString().trim())
                            .build());
                    chunkBuffer.setLength(0);
                }
            }

            if (!chunkBuffer.isEmpty()) {
                chunks.add(DocumentChunk.builder()
                        .fileName(fileName)
                        .pageNumber(1 + lineNumber / 40)
                        .section(currentSection)
                        .text(chunkBuffer.toString().trim())
                        .build());
            }
        }
        return chunks;
    }

    private boolean isSectionHeader(String text) {
        if (text.length() > 120) {
            return false;
        }
        return SECTION_PATTERN.matcher(text).matches();
    }
}
