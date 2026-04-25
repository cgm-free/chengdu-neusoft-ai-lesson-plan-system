package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.ExtractResourceResponse;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class ResourceExtractionService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 12000;
    private static final int EXCERPT_LENGTH = 1500;

    private final PaddleOcrService paddleOcrService;

    public ResourceExtractionService(PaddleOcrService paddleOcrService) {
        this.paddleOcrService = paddleOcrService;
    }

    public ExtractResourceResponse extract(MultipartFile file) throws IOException {
        validateFile(file);
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String fileType = detectFileType(fileName);
        ExtractionResult extractionResult = extractByType(file, fileName, fileType);
        String extractedText = extractionResult.text();
        String cleanedText = limit(cleanText(extractedText));
        if (cleanedText.isBlank()) {
            throw new IllegalArgumentException("未从文件中提取到可用文本内容");
        }
        ExtractResourceResponse response = new ExtractResourceResponse();
        response.setFileName(fileName);
        response.setFileType(fileType);
        response.setExtractedText(cleanedText);
        response.setCharCount(cleanedText.length());
        response.setExcerpt(cleanedText.length() > EXCERPT_LENGTH ? cleanedText.substring(0, EXCERPT_LENGTH) : cleanedText);
        response.setExtractionMethod(extractionResult.extractionMethod());
        response.setOcrStatus(extractionResult.ocrStatus());
        response.setPageCount(extractionResult.pageCount());
        response.setOcrConfidence(extractionResult.ocrConfidence());
        return response;
    }

    private ExtractionResult extractByType(MultipartFile file, String fileName, String fileType) throws IOException {
        return switch (fileType) {
            case "docx" -> new ExtractionResult(extractDocx(file), "text", "not_required", null, null);
            case "pptx" -> new ExtractionResult(extractPptx(file), "text", "not_required", null, null);
            case "pdf" -> extractPdf(file, fileName);
            case "txt", "md" -> new ExtractionResult(new String(file.getBytes(), StandardCharsets.UTF_8), "text", "not_required", null, null);
            default -> throw new IllegalArgumentException("暂只支持 txt、md、docx、pptx、pdf 文件");
        };
    }

    private String extractDocx(MultipartFile file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    builder.append(text.trim()).append('\n');
                }
            }
        }
        return limit(builder.toString());
    }

    private String extractPptx(MultipartFile file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (XMLSlideShow slideShow = new XMLSlideShow(file.getInputStream())) {
            int index = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                builder.append("第").append(index++).append("页：\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            builder.append(text.trim()).append('\n');
                        }
                    }
                }
            }
        }
        return limit(builder.toString());
    }

    private ExtractionResult extractPdf(MultipartFile file, String fileName) throws IOException {
        byte[] bytes = file.getBytes();
        String textLayer;
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            textLayer = stripper.getText(document);
        }
        String cleanedTextLayer = cleanText(textLayer);
        if (!cleanedTextLayer.isBlank()) {
            return new ExtractionResult(textLayer, "text", "not_required", null, null);
        }
        PaddleOcrService.OcrExtraction ocrExtraction = paddleOcrService.extractPdfText(bytes, fileName);
        return new ExtractionResult(
                ocrExtraction.text(),
                "ocr",
                "success",
                ocrExtraction.pageCount(),
                ocrExtraction.confidence()
        );
    }

    private String limit(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的参考资料");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("单个参考资料不能超过 10MB");
        }
        detectFileType(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
    }

    private String detectFileType(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim().toLowerCase();
        if (normalized.endsWith(".txt")) {
            return "txt";
        }
        if (normalized.endsWith(".md")) {
            return "md";
        }
        if (normalized.endsWith(".docx")) {
            return "docx";
        }
        if (normalized.endsWith(".pptx")) {
            return "pptx";
        }
        if (normalized.endsWith(".pdf")) {
            return "pdf";
        }
        throw new IllegalArgumentException("暂只支持 txt、md、docx、pptx、pdf 文件");
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        String[] lines = normalized.split("\n");
        StringBuilder builder = new StringBuilder();
        boolean lastBlank = false;
        for (String line : lines) {
            String cleanedLine = line.trim();
            if (cleanedLine.isEmpty()) {
                if (!lastBlank && builder.length() > 0) {
                    builder.append('\n');
                }
                lastBlank = true;
                continue;
            }
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(cleanedLine);
            lastBlank = false;
        }
        return builder.toString().trim();
    }

    private record ExtractionResult(
            String text,
            String extractionMethod,
            String ocrStatus,
            Integer pageCount,
            Double ocrConfidence
    ) {
    }
}
