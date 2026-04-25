package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.ExtractResourceResponse;
import cn.edu.nsu.maic.dto.OptimizeTextRequest;
import cn.edu.nsu.maic.dto.OptimizeTextResponse;
import cn.edu.nsu.maic.dto.TeachingCalendarDto;
import cn.edu.nsu.maic.service.AuthService;
import cn.edu.nsu.maic.service.ResourceExtractionService;
import cn.edu.nsu.maic.service.TeachingCalendarParseService;
import cn.edu.nsu.maic.service.TextOptimizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/ai")
public class AiToolController {

    private static final Logger log = LoggerFactory.getLogger(AiToolController.class);

    private final AuthService authService;
    private final TextOptimizationService textOptimizationService;
    private final ResourceExtractionService resourceExtractionService;
    private final TeachingCalendarParseService teachingCalendarParseService;

    public AiToolController(AuthService authService, TextOptimizationService textOptimizationService,
                            ResourceExtractionService resourceExtractionService,
                            TeachingCalendarParseService teachingCalendarParseService) {
        this.authService = authService;
        this.textOptimizationService = textOptimizationService;
        this.resourceExtractionService = resourceExtractionService;
        this.teachingCalendarParseService = teachingCalendarParseService;
    }

    @PostMapping("/optimize-text")
    public ApiResponse<OptimizeTextResponse> optimizeText(@Valid @RequestBody OptimizeTextRequest request,
                                                          HttpServletRequest servletRequest) {
        authService.requireUser(servletRequest);
        return ApiResponse.ok(new OptimizeTextResponse(textOptimizationService.optimize(request)));
    }

    @PostMapping("/extract-resource")
    public ApiResponse<ExtractResourceResponse> extractResource(@RequestParam("file") MultipartFile file,
                                                                HttpServletRequest servletRequest) throws IOException {
        authService.requireUser(servletRequest);
        log.info("Extracting reference material: name={}, size={} bytes, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        return ApiResponse.ok(resourceExtractionService.extract(file));
    }

    @PostMapping("/parse-teaching-calendar")
    public ApiResponse<TeachingCalendarDto> parseTeachingCalendar(@RequestParam("file") MultipartFile file,
                                                                  HttpServletRequest servletRequest) throws IOException {
        authService.requireUser(servletRequest);
        log.info("Parsing teaching calendar: name={}, size={} bytes, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        return ApiResponse.ok(teachingCalendarParseService.parse(file));
    }
}
