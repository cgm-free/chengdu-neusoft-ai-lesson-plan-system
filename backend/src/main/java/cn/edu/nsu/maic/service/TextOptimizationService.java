package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.OptimizeTextRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class TextOptimizationService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${maic.ai.api-key:}")
    private String apiKey;

    @Value("${maic.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${maic.ai.model-name:deepseek-v4-pro}")
    private String modelName;

    public TextOptimizationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public String optimize(OptimizeTextRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DeepSeek API Key，已按要求停止文本优化，不使用兜底。");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", modelName,
                    "messages", List.of(
                            Map.of("role", "system", "content", "你是高校教师教案文本润色助手。只返回润色后的正文，不要解释，不要 Markdown。"),
                            Map.of("role", "user", "content", prompt(request))
                    ),
                    "temperature", 0.3,
                    "max_tokens", 1200,
                    "stream", false
            );
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("DeepSeek 返回了空文本");
            }
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("DeepSeek 文本优化失败：" + e.getMessage(), e);
        }
    }

    private String prompt(OptimizeTextRequest request) {
        if (text(request.getFieldName()).startsWith("自动填写-")) {
            return """
                    请根据以下课程信息直接生成“%s”字段正文。
                    要求：只返回可直接粘贴到表单里的正文，不要标题，不要解释，不要 Markdown。
                    内容必须具体、可落地，适合成都东软学院应用型本科课堂。

                    课程信息：
                    %s
                    """.formatted(
                    text(request.getFieldName()).replace("自动填写-", ""),
                    text(request.getText())
            );
        }
        return """
                请润色以下教案输入内容，使其更具体、更专业、更适合成都东软学院应用型本科课堂。
                字段：%s
                课程：%s
                主题：%s
                要求：增强针对性、可操作性和教学语境，不要凭空增加不相关内容。

                原文：
                %s
                """.formatted(
                text(request.getFieldName()),
                text(request.getCourseName()),
                text(request.getTopic()),
                text(request.getText())
        );
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.deepseek.com";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }
}
