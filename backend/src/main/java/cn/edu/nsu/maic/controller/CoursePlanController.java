package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.CoursePlanDtos;
import cn.edu.nsu.maic.dto.UserInfo;
import cn.edu.nsu.maic.service.AuthService;
import cn.edu.nsu.maic.service.CoursePlanAnalysisService;
import cn.edu.nsu.maic.service.CoursePlanGenerationJobService;
import cn.edu.nsu.maic.service.CoursePlanPdfExportService;
import cn.edu.nsu.maic.service.CoursePlanService;
import cn.edu.nsu.maic.service.CoursePlanWordExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/course-plans")
public class CoursePlanController {

    private static final String EXPORT_FILENAME_HEADER = "X-Course-Plan-Filename";
    private static final String DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DEFAULT_TEMPLATE_DISPLAY_NAME = "20XX-20XX学年第X学期《课程名称》-课程教案（模版).docx";

    private final Path defaultTemplatePath;
    private final AuthService authService;
    private final CoursePlanAnalysisService coursePlanAnalysisService;
    private final CoursePlanService coursePlanService;
    private final CoursePlanGenerationJobService coursePlanGenerationJobService;
    private final CoursePlanWordExportService coursePlanWordExportService;
    private final CoursePlanPdfExportService coursePlanPdfExportService;

    public CoursePlanController(
            @Value("${maic.course-plan.default-template-path}") String defaultTemplatePath,
            AuthService authService,
            CoursePlanAnalysisService coursePlanAnalysisService,
            CoursePlanService coursePlanService,
            CoursePlanGenerationJobService coursePlanGenerationJobService,
            CoursePlanWordExportService coursePlanWordExportService,
            CoursePlanPdfExportService coursePlanPdfExportService
    ) {
        this.defaultTemplatePath = Path.of(defaultTemplatePath);
        this.authService = authService;
        this.coursePlanAnalysisService = coursePlanAnalysisService;
        this.coursePlanService = coursePlanService;
        this.coursePlanGenerationJobService = coursePlanGenerationJobService;
        this.coursePlanWordExportService = coursePlanWordExportService;
        this.coursePlanPdfExportService = coursePlanPdfExportService;
    }

    @GetMapping
    public ApiResponse<List<CoursePlanDtos.CoursePlanSummary>> list(HttpServletRequest servletRequest) {
        return ApiResponse.ok(coursePlanService.listLatest(authService.requireUser(servletRequest)));
    }

    @GetMapping("/default-template")
    public ResponseEntity<byte[]> defaultTemplate(HttpServletRequest servletRequest) throws IOException {
        authService.requireUser(servletRequest);
        byte[] bytes = loadDefaultTemplateBytes();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DOCX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(defaultTemplateFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(bytes);
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoursePlanDtos.AnalysisResult> analyze(
            @RequestPart(value = "template", required = false) MultipartFile template,
            @RequestPart("courseStandard") MultipartFile courseStandard,
            @RequestPart("ppts") List<MultipartFile> ppts,
            @RequestPart(value = "references", required = false) List<MultipartFile> references,
            @RequestPart(value = "teacherRequirements", required = false) String teacherRequirements,
            HttpServletRequest servletRequest
    ) throws IOException {
        authService.requireUser(servletRequest);
        return ApiResponse.ok(coursePlanAnalysisService.analyze(resolveTemplate(template), courseStandard, ppts, references, teacherRequirements));
    }

    @PostMapping(value = "/{id}/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoursePlanDtos.AnalysisResult> analyzeExisting(
            @PathVariable Long id,
            @RequestPart(value = "template", required = false) MultipartFile template,
            @RequestPart(value = "courseStandard", required = false) MultipartFile courseStandard,
            @RequestPart(value = "ppts", required = false) List<MultipartFile> ppts,
            @RequestPart(value = "references", required = false) List<MultipartFile> references,
            @RequestPart(value = "teacherRequirements", required = false) String teacherRequirements,
            HttpServletRequest servletRequest
    ) throws IOException {
        UserInfo user = authService.requireUser(servletRequest);
        return ApiResponse.ok(coursePlanService.analyzeExisting(id, template, courseStandard, ppts, references, teacherRequirements, user));
    }

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoursePlanDtos.Detail> generate(
            @RequestPart(value = "template", required = false) MultipartFile template,
            @RequestPart("courseStandard") MultipartFile courseStandard,
            @RequestPart("ppts") List<MultipartFile> ppts,
            @RequestPart(value = "references", required = false) List<MultipartFile> references,
            @RequestPart("payload") CoursePlanDtos.GenerateRequest payload,
            HttpServletRequest servletRequest
    ) throws IOException {
        UserInfo user = authService.requireUser(servletRequest);
        return ApiResponse.ok(coursePlanService.generate(resolveTemplate(template), courseStandard, ppts, references, payload, user));
    }

    @PostMapping(value = "/generation-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoursePlanDtos.GenerationJobSummary> createGenerationJob(
            @RequestPart(value = "coursePlanId", required = false) String coursePlanId,
            @RequestPart(value = "sourceCoursePlanId", required = false) String sourceCoursePlanId,
            @RequestPart(value = "template", required = false) MultipartFile template,
            @RequestPart(value = "courseStandard", required = false) MultipartFile courseStandard,
            @RequestPart(value = "ppts", required = false) List<MultipartFile> ppts,
            @RequestPart(value = "references", required = false) List<MultipartFile> references,
            @RequestPart("payload") CoursePlanDtos.GenerateRequest payload,
            HttpServletRequest servletRequest
    ) throws IOException {
        UserInfo user = authService.requireUser(servletRequest);
        Long parsedCoursePlanId = parseOptionalLong(coursePlanId);
        Long parsedSourceCoursePlanId = parseOptionalLong(sourceCoursePlanId);
        MultipartFile effectiveTemplate = parsedCoursePlanId == null ? resolveTemplate(template) : template;
        return ApiResponse.ok(coursePlanGenerationJobService.submit(
                parsedCoursePlanId,
                parsedSourceCoursePlanId,
                effectiveTemplate,
                courseStandard,
                ppts,
                references,
                payload,
                user
        ));
    }

    @GetMapping("/generation-jobs/{jobId}")
    public ApiResponse<CoursePlanDtos.GenerationJobSummary> generationJob(
            @PathVariable Long jobId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(coursePlanGenerationJobService.getJob(jobId, authService.requireUser(servletRequest)));
    }

    @PostMapping("/generation-jobs/{jobId}/cancel")
    public ApiResponse<CoursePlanDtos.GenerationJobSummary> cancelGenerationJob(
            @PathVariable Long jobId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(coursePlanGenerationJobService.cancelJob(jobId, authService.requireUser(servletRequest)));
    }

    @PostMapping(value = "/{id}/regenerate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CoursePlanDtos.Detail> regenerate(
            @PathVariable Long id,
            @RequestPart(value = "template", required = false) MultipartFile template,
            @RequestPart(value = "courseStandard", required = false) MultipartFile courseStandard,
            @RequestPart(value = "ppts", required = false) List<MultipartFile> ppts,
            @RequestPart(value = "references", required = false) List<MultipartFile> references,
            @RequestPart("payload") CoursePlanDtos.GenerateRequest payload,
            HttpServletRequest servletRequest
    ) throws IOException {
        UserInfo user = authService.requireUser(servletRequest);
        return ApiResponse.ok(coursePlanService.regenerate(id, template, courseStandard, ppts, references, payload, user));
    }

    @PostMapping("/{id}/reanalyze")
    public ApiResponse<CoursePlanDtos.Detail> reanalyze(
            @PathVariable Long id,
            @RequestBody(required = false) CoursePlanDtos.ReanalyzeRequest request,
            HttpServletRequest servletRequest
    ) throws IOException {
        String teacherRequirements = request == null ? "" : request.teacherRequirements();
        return ApiResponse.ok(coursePlanService.reanalyze(id, teacherRequirements, authService.requireUser(servletRequest)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CoursePlanDtos.Detail> detail(@PathVariable Long id, HttpServletRequest servletRequest) {
        return ApiResponse.ok(coursePlanService.getDetail(id, authService.requireUser(servletRequest)));
    }

    @PutMapping("/{id}")
    public ApiResponse<CoursePlanDtos.Detail> save(
            @PathVariable Long id,
            @RequestBody CoursePlanDtos.SaveRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(coursePlanService.save(id, request, authService.requireUser(servletRequest)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest servletRequest) {
        coursePlanService.delete(id, authService.requireUser(servletRequest));
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/export-word")
    public ResponseEntity<byte[]> exportWord(@PathVariable Long id, HttpServletRequest servletRequest) {
        UserInfo user = authService.requireUser(servletRequest);
        CoursePlanDtos.Detail detail = coursePlanService.getDetail(id, user);
        CoursePlanService.TemplateBinary templateBinary = coursePlanService.loadTemplate(id, user);
        byte[] bytes = coursePlanWordExportService.export(detail, templateBinary.bytes());
        String fileName = exportFileName(detail, ".docx");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("attachment", fileName))
                .header(EXPORT_FILENAME_HEADER, encodedFileName(fileName))
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION + ", " + EXPORT_FILENAME_HEADER)
                .body(bytes);
    }

    @GetMapping("/{id}/export-pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id, HttpServletRequest servletRequest) {
        UserInfo user = authService.requireUser(servletRequest);
        CoursePlanDtos.Detail detail = coursePlanService.getDetail(id, user);
        CoursePlanService.TemplateBinary templateBinary = coursePlanService.loadTemplate(id, user);
        byte[] bytes = coursePlanPdfExportService.export(detail, templateBinary.bytes());
        String fileName = exportFileName(detail, ".pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("attachment", fileName))
                .header(EXPORT_FILENAME_HEADER, encodedFileName(fileName))
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION + ", " + EXPORT_FILENAME_HEADER)
                .body(bytes);
    }

    @GetMapping("/{id}/preview-pdf")
    public ResponseEntity<byte[]> previewPdf(@PathVariable Long id, HttpServletRequest servletRequest) {
        UserInfo user = authService.requireUser(servletRequest);
        CoursePlanDtos.Detail detail = coursePlanService.getDetail(id, user);
        CoursePlanService.TemplateBinary templateBinary = coursePlanService.loadTemplate(id, user);
        byte[] bytes = coursePlanPdfExportService.export(detail, templateBinary.bytes());
        String fileName = exportFileName(detail, ".pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("inline", fileName))
                .header(EXPORT_FILENAME_HEADER, encodedFileName(fileName))
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION + ", " + EXPORT_FILENAME_HEADER)
                .body(bytes);
    }

    private String contentDisposition(String type, String fileName) {
        return type + "; filename*=UTF-8''" + encodedFileName(fileName);
    }

    private String encodedFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex);
    }

    private String exportFileName(CoursePlanDtos.Detail detail, String suffix) {
        CoursePlanDtos.BasicInfo contentBasicInfo = detail == null || detail.content() == null ? null : detail.content().basicInfo();
        CoursePlanDtos.BasicInfo analysisBasicInfo = detail == null || detail.analysis() == null ? null : detail.analysis().basicInfo();
        String academicTerm = firstNonBlank(
                contentBasicInfo == null ? "" : contentBasicInfo.semester(),
                analysisBasicInfo == null ? "" : analysisBasicInfo.semester()
        );
        String courseName = firstNonBlank(
                contentBasicInfo == null ? "" : contentBasicInfo.courseName(),
                analysisBasicInfo == null ? "" : analysisBasicInfo.courseName()
        );
        if (academicTerm.isBlank()) {
            throw new IllegalStateException("缺少学年学期信息，不能导出课程教案。");
        }
        if (courseName.isBlank()) {
            throw new IllegalStateException("缺少课程名称，不能导出课程教案。");
        }
        return sanitizeFileName(academicTerm + wrapCourseName(courseName) + "-课程教案" + suffix);
    }

    private String wrapCourseName(String courseName) {
        String value = courseName.replace("《", "").replace("》", "").trim();
        if (value.isBlank()) {
            throw new IllegalStateException("缺少课程名称，不能导出课程教案。");
        }
        return "《" + value + "》";
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("文件名为空，不能导出课程教案。");
        }
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String text = safeText(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private MultipartFile resolveTemplate(MultipartFile template) throws IOException {
        if (template != null && !template.isEmpty()) {
            return template;
        }
        return new InMemoryMultipartFile(defaultTemplateFileName(), DOCX_MEDIA_TYPE, loadDefaultTemplateBytes());
    }

    private Long parseOptionalLong(String value) {
        String text = safeText(value);
        if (text.isBlank()) {
            return null;
        }
        return Long.parseLong(text);
    }

    private byte[] loadDefaultTemplateBytes() throws IOException {
        if (!Files.isRegularFile(defaultTemplatePath)) {
            throw new IllegalStateException("默认教案模板不存在：" + defaultTemplatePath);
        }
        return Files.readAllBytes(defaultTemplatePath);
    }

    private String defaultTemplateFileName() {
        return DEFAULT_TEMPLATE_DISPLAY_NAME;
    }

    private MultipartFile toMultipartFile(CoursePlanService.TemplateBinary value) {
        return new InMemoryMultipartFile(value.fileName(), DOCX_MEDIA_TYPE, value.bytes());
    }

    private record InMemoryMultipartFile(
            String originalFilename,
            String contentType,
            byte[] bytes
    ) implements MultipartFile {
        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes == null ? 0 : bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes == null ? new byte[0] : bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), bytes == null ? new byte[0] : bytes);
        }
    }
}
