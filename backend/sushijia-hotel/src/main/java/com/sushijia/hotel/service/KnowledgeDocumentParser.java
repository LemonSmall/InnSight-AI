package com.sushijia.hotel.service;

import com.sushijia.common.exception.BizException;
import com.sushijia.common.response.ResultCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
public class KnowledgeDocumentParser {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md");
    private static final int MAX_EXTRACTED_CHARS = 120_000;

    public String extension(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 PDF、Word、TXT 和 Markdown 文件");
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 PDF、Word、TXT 和 Markdown 文件");
        }
        return extension;
    }

    public String parse(Path file, String extension) {
        try {
            String content = switch (extension) {
                case "pdf" -> parsePdf(file);
                case "docx" -> parseDocx(file);
                case "doc" -> parseDoc(file);
                case "txt", "md" -> Files.readString(file, StandardCharsets.UTF_8);
                default -> throw new BizException(ResultCode.BAD_REQUEST, "不支持的文件类型");
            };
            String normalized = normalize(content);
            if (normalized.isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST, "文件中未提取到可用文字");
            }
            return normalized.length() > MAX_EXTRACTED_CHARS
                ? normalized.substring(0, MAX_EXTRACTED_CHARS)
                : normalized;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件解析失败");
        }
    }

    private String parsePdf(Path file) throws IOException {
        try (PDDocument document = PDDocument.load(file.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String parseDocx(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parseDoc(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file);
             HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String normalize(String content) {
        return content == null ? "" : content
            .replace("\u0000", "")
            .replaceAll("[\\t ]+", " ")
            .replaceAll("\\R{3,}", "\n\n")
            .trim();
    }
}
