package cn.edu.nsu.maic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PaddleOcrService {

    private static final int MAX_OCR_PAGES = 30;
    private static final int OCR_DPI = 180;

    private final ObjectMapper objectMapper;

    @Value("${maic.ocr.enabled:true}")
    private boolean enabled;

    @Value("${maic.ocr.python-command:python}")
    private String pythonCommand;

    @Value("${maic.ocr.script-path:scripts/paddle_ocr.py}")
    private String scriptPath;

    public PaddleOcrService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OcrExtraction extractPdfText(byte[] pdfBytes, String fileName) {
        if (!enabled) {
            throw new IllegalArgumentException("该 PDF 没有可复制文字，OCR 功能未启用，无法提取扫描版 PDF 内容。");
        }
        Path script = Path.of(scriptPath).toAbsolutePath().normalize();
        if (!Files.exists(script)) {
            throw new IllegalArgumentException("该 PDF 没有可复制文字，未找到 PaddleOCR 脚本：" + script);
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("maic-ocr-");
            List<Path> images = renderPdfToImages(pdfBytes, tempDir);
            if (images.isEmpty()) {
                throw new IllegalArgumentException("该 PDF 没有可复制文字，且未能渲染出可 OCR 的页面。");
            }
            ProcessBuilder builder = new ProcessBuilder(buildCommand(script, images));
            builder.environment().putIfAbsent("KMP_DUPLICATE_LIB_OK", "TRUE");
            builder.environment().putIfAbsent("OMP_NUM_THREADS", "1");
            builder.environment().putIfAbsent("FLAGS_allocator_strategy", "auto_growth");
            builder.environment().putIfAbsent("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(Duration.ofSeconds(420).toMillis(), TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalArgumentException("扫描版 PDF OCR 超时。文件大小不是唯一因素，页数较多或页面较复杂时会逐页识别较久，请稍后重试，或先拆分 PDF 后再上传。");
            }
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException("扫描版 PDF 需要 PaddleOCR 识别，但 OCR 执行失败：" + parseOcrError(output));
            }
            JsonNode root = objectMapper.readTree(output);
            String text = root.path("text").asText("").trim();
            if (text.isBlank()) {
                throw new IllegalArgumentException("扫描版 PDF 已执行 OCR，但未识别到可用文本内容。");
            }
            return new OcrExtraction(text, images.size(), root.path("confidence").asDouble(0D));
        } catch (IOException e) {
            throw new IllegalArgumentException("扫描版 PDF OCR 处理失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("扫描版 PDF OCR 被中断", e);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private List<String> buildCommand(Path script, List<Path> images) {
        List<String> command = new ArrayList<>();
        command.add(pythonCommand);
        command.add(script.toString());
        images.forEach(image -> command.add(image.toString()));
        return command;
    }

    private String parseOcrError(String output) {
        String message = output == null ? "" : output.trim();
        if (message.isBlank()) {
            return "OCR 脚本没有返回错误信息";
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            String error = root.path("error").asText("").trim();
            return error.isBlank() ? message : error;
        } catch (Exception ignored) {
            return message;
        }
    }

    private List<Path> renderPdfToImages(byte[] pdfBytes, Path tempDir) throws IOException {
        List<Path> images = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), MAX_OCR_PAGES);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.RGB);
                Path imagePath = tempDir.resolve("page-" + (i + 1) + ".png");
                ImageIO.write(image, "png", imagePath.toFile());
                images.add(imagePath);
            }
        }
        return images;
    }

    private void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 临时文件清理失败不影响主流程结果。
                        }
                    });
        } catch (IOException ignored) {
            // 临时目录不存在或不可读时无需继续处理。
        }
    }

    public record OcrExtraction(String text, int pageCount, double confidence) {
    }
}
