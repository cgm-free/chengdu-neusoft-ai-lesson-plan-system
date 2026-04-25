package cn.edu.nsu.maic.controller;

import cn.edu.nsu.maic.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "成都东软学院智能教案生成系统");
        data.put("status", "ok");
        data.put("time", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }
}

