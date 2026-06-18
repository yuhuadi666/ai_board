package org.example.aiboard.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeDocumentService {

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try (InputStream inputStream = file.getInputStream()) {
            if (filename.endsWith(".pdf")) {
                return extractPdf(inputStream);
            }
            if (filename.endsWith(".docx")) {
                return extractDocx(inputStream);
            }
            if (filename.endsWith(".doc")) {
                return extractDoc(inputStream);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("简历文件解析失败，请确认文件为 doc、docx 或 pdf 格式", e);
        }
        throw new IllegalArgumentException("仅支持上传 doc、docx、pdf 简历文件");
    }

    public byte[] buildDocx(String resumeText) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String block : resumeText.strip().split("\\R{2,}")) {
                XWPFParagraph paragraph = document.createParagraph();
                for (String line : block.split("\\R")) {
                    XWPFRun run = paragraph.createRun();
                    run.setText(line);
                    run.setFontFamily("Microsoft YaHei");
                    run.setFontSize(11);
                    run.addBreak();
                }
            }
            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成简历文档失败", e);
        }
    }

    private String extractPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            return clean(new PDFTextStripper().getText(document));
        }
    }

    private String extractDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return clean(extractor.getText());
        }
    }

    private String extractDoc(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return clean(extractor.getText());
        }
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u0000', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("(?m)^\\s+", "")
                .strip();
    }
}
