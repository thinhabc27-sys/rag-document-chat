package com.rag.chat;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class GenerateSampleDocxTest {

    @Test
    public void generateSampleDocx() throws IOException {
        String txtPath = "tai_lieu_mau_rag.txt";
        if (!Files.exists(Paths.get(txtPath))) {
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get(txtPath));

        try (XWPFDocument document = new XWPFDocument()) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();

                if (trimmed.startsWith("Section")) {
                    paragraph.setStyle("Heading1");
                    paragraph.setAlignment(ParagraphAlignment.LEFT);
                    run.setBold(true);
                    run.setFontSize(14);
                    run.setText(trimmed);
                } else {
                    run.setFontSize(11);
                    run.setText(trimmed);
                }
            }

            try (FileOutputStream out = new FileOutputStream("tai_lieu_mau_rag.docx")) {
                document.write(out);
            }
        }
    }
}
