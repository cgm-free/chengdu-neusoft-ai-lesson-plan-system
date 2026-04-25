package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class CoursePlanPdfExportService {

    private final CoursePlanWordExportService coursePlanWordExportService;

    public CoursePlanPdfExportService(CoursePlanWordExportService coursePlanWordExportService) {
        this.coursePlanWordExportService = coursePlanWordExportService;
    }

    public byte[] export(CoursePlanDtos.Detail detail, byte[] templateBytes) {
        byte[] wordBytes = coursePlanWordExportService.export(detail, templateBytes);
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("course-plan-export-");
            Path docxPath = tempDir.resolve("course-plan.docx");
            Path pdfPath = tempDir.resolve("course-plan.pdf");
            Files.write(docxPath, wordBytes);
            exportViaWord(docxPath, pdfPath);
            return Files.readAllBytes(pdfPath);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("导出课程教案 PDF 失败", e);
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void exportViaWord(Path docxPath, Path pdfPath) throws IOException, InterruptedException {
        String input = escapeForPowerShell(docxPath.toAbsolutePath().toString());
        String output = escapeForPowerShell(pdfPath.toAbsolutePath().toString());
        String script = """
                $word = $null
                $doc = $null
                try {
                  $word = New-Object -ComObject Word.Application
                  $word.Visible = $false
                  $doc = $word.Documents.Open('%s')
                  $doc.ExportAsFixedFormat('%s', 17)
                } finally {
                  if ($doc -ne $null) { $doc.Close() }
                  if ($word -ne $null) { $word.Quit() }
                }
                """.formatted(input, output);
        Process process = new ProcessBuilder(
                List.of("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
        ).redirectErrorStream(true).start();
        String outputText = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(pdfPath)) {
            throw new IllegalStateException("Word COM 转 PDF 失败：" + outputText);
        }
    }

    private String escapeForPowerShell(String value) {
        return value.replace("'", "''");
    }
}
