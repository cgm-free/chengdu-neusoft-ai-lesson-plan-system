package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.ApiResponse;
import cn.edu.nsu.maic.dto.GenerationRecordSummary;
import cn.edu.nsu.maic.service.AuthService;
import cn.edu.nsu.maic.service.GenerationRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/generation-records")
public class GenerationRecordController {

    private final GenerationRecordService generationRecordService;
    private final AuthService authService;

    public GenerationRecordController(GenerationRecordService generationRecordService, AuthService authService) {
        this.generationRecordService = generationRecordService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<GenerationRecordSummary>> list(HttpServletRequest request) {
        return ApiResponse.ok(generationRecordService.listRecent(authService.requireUser(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(generationRecordService.detail(id, authService.requireUser(request)));
    }
}
