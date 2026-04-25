package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.LessonPlanDetail;
import cn.edu.nsu.maic.dto.LessonPlanRequest;
import cn.edu.nsu.maic.dto.LessonPlanSaveRequest;
import cn.edu.nsu.maic.dto.LessonPlanSummary;
import cn.edu.nsu.maic.dto.UserInfo;
import cn.edu.nsu.maic.service.AuthService;
import cn.edu.nsu.maic.service.LessonPlanService;
import cn.edu.nsu.maic.service.WordExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lesson-plans")
public class LessonPlanController {

    private final LessonPlanService lessonPlanService;
    private final AuthService authService;
    private final WordExportService wordExportService;

    public LessonPlanController(LessonPlanService lessonPlanService, AuthService authService, WordExportService wordExportService) {
        this.lessonPlanService = lessonPlanService;
        this.authService = authService;
        this.wordExportService = wordExportService;
    }

    @GetMapping
    public ApiResponse<List<LessonPlanSummary>> list(HttpServletRequest servletRequest) {
        return ApiResponse.ok(lessonPlanService.listLatest(authService.requireUser(servletRequest)));
    }

    @GetMapping("/{id}")
    public ApiResponse<LessonPlanDetail> detail(@PathVariable Long id, HttpServletRequest servletRequest) {
        return ApiResponse.ok(lessonPlanService.getDetail(id, authService.requireUser(servletRequest)));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody LessonPlanRequest request, HttpServletRequest servletRequest) {
        Long id = lessonPlanService.createDraft(request, authService.requireUser(servletRequest));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        return ApiResponse.ok(data);
    }

    @PostMapping("/generate")
    public ApiResponse<LessonPlanDetail> generate(@Valid @RequestBody LessonPlanRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(lessonPlanService.generate(request, authService.requireUser(servletRequest)));
    }

    @PutMapping("/{id}")
    public ApiResponse<LessonPlanDetail> save(@PathVariable Long id, @Valid @RequestBody LessonPlanSaveRequest request,
                                              HttpServletRequest servletRequest) {
        return ApiResponse.ok(lessonPlanService.save(id, request, authService.requireUser(servletRequest)));
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<LessonPlanDetail> copy(@PathVariable Long id, HttpServletRequest servletRequest) {
        return ApiResponse.ok(lessonPlanService.copy(id, authService.requireUser(servletRequest)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest servletRequest) {
        lessonPlanService.delete(id, authService.requireUser(servletRequest));
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/export-word")
    public ResponseEntity<byte[]> exportWord(@PathVariable Long id, HttpServletRequest servletRequest) {
        UserInfo user = authService.requireUser(servletRequest);
        LessonPlanDetail detail = lessonPlanService.getDetail(id, user);
        byte[] bytes = wordExportService.export(detail);
        String fileName = sanitizeFileName(detail.getTitle()) + ".docx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(bytes);
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "教案";
        }
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
