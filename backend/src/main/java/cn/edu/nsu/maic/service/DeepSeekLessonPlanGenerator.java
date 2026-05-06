package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.LessonPlanRequest;
import cn.edu.nsu.maic.dto.ReferenceMaterialDto;
import cn.edu.nsu.maic.dto.TeachingCalendarDto;
import cn.edu.nsu.maic.dto.TeachingCalendarEntryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class DeepSeekLessonPlanGenerator implements AiLessonPlanGenerator {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${maic.ai.provider:deepseek}")
    private String provider;

    @Value("${maic.ai.model-name:deepseek-v4-pro}")
    private String modelName;

    @Value("${maic.ai.api-key:}")
    private String apiKey;

    @Value("${maic.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    public DeepSeekLessonPlanGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public LessonPlanGeneration generate(LessonPlanRequest request) {
        if (!"deepseek".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DeepSeek API Key，已按要求停止生成，不使用本地兜底。");
        }

        long startedAt = System.currentTimeMillis();
        String basicPrompt = buildBasicSectionPrompt(request);
        String processPrompt = buildTeachingProcessSectionPrompt(request);
        String taskPrompt = buildPracticeTaskSectionPrompt(request);
        String evaluationPrompt = buildEvaluationSectionPrompt(request);
        String prompt = basicPrompt + "\n\n---\n\n" + processPrompt + "\n\n---\n\n" + taskPrompt + "\n\n---\n\n" + evaluationPrompt;
        String firstContent = "";
        try {
            CompletableFuture<JsonNode> basicFuture = requestSectionAsync(basicPrompt, 2300, 110, "学情、目标与资源");
            CompletableFuture<JsonNode> processFuture = requestSectionAsync(processPrompt, 1500, 100, "教学过程");
            CompletableFuture<JsonNode> taskFuture = requestSectionAsync(taskPrompt, 1300, 95, "课堂任务与代码材料");
            CompletableFuture<JsonNode> evaluationFuture = requestSectionAsync(evaluationPrompt, 1500, 95, "作业、评价与反思");
            CompletableFuture.allOf(basicFuture, processFuture, taskFuture, evaluationFuture)
                    .orTimeout(175, TimeUnit.SECONDS)
                    .join();

            JsonNode basicSection = basicFuture.join();
            JsonNode processSection = processFuture.join();
            JsonNode taskSection = taskFuture.join();
            JsonNode evaluationSection = evaluationFuture.join();
            ObjectNode assembled = assembleLessonJson(request, basicSection, processSection, taskSection, evaluationSection);
            firstContent = objectMapper.writeValueAsString(assembled);
            String normalizedJson = normalizeJson(firstContent, request);
            return new LessonPlanGeneration("deepseek", modelName, prompt, normalizedJson, System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            String reason = rootReason(e);
            if (firstContent.isBlank()) {
                throw new IllegalStateException("DeepSeek 生成失败，已按要求停止生成，不使用本地兜底。原因：" + reason, e);
            }
            throw new IllegalStateException("DeepSeek 返回内容未达到教案结构要求，已停止生成，不使用本地兜底。原因：" + reason, e);
        }
    }

    private CompletableFuture<JsonNode> requestSectionAsync(String prompt, int maxTokens, int timeoutSeconds, String sectionName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return requestSection(prompt, maxTokens, timeoutSeconds, sectionName);
            } catch (Exception e) {
                throw new CompletionException(new IllegalStateException(sectionName + "生成失败：" + rootReason(e), e));
            }
        });
    }

    private JsonNode requestSection(String prompt, int maxTokens, int timeoutSeconds, String sectionName) throws IOException, InterruptedException {
        String responseBody = callDeepSeek(prompt, maxTokens, timeoutSeconds);
        String content = extractContent(responseBody);
        try {
            return objectMapper.readTree(content.trim());
        } catch (JsonProcessingException firstError) {
            return repairAndParseSection(content, maxTokens, sectionName, firstError);
        }
    }

    private JsonNode repairAndParseSection(String invalidContent, int maxTokens, String sectionName, JsonProcessingException firstError)
            throws IOException, InterruptedException {
        int repairMaxTokens = Math.min(Math.max(maxTokens + 500, 1800), 3200);
        String repairResponseBody = callDeepSeek(buildJsonRepairPrompt(invalidContent, sectionName), repairMaxTokens, 75);
        String repairedContent = extractContent(repairResponseBody);
        try {
            return objectMapper.readTree(repairedContent.trim());
        } catch (JsonProcessingException secondError) {
            secondError.addSuppressed(firstError);
            throw new IllegalStateException(sectionName + "返回 JSON 格式不完整，系统已自动修复一次但仍无法解析，请重新生成或减少主要参考材料长度。", secondError);
        }
    }

    private String rootReason(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof TimeoutException) {
            return "DeepSeek 请求超过系统等待时间，请稍后重试或减少主要参考材料长度。";
        }
        if (cause instanceof JsonProcessingException) {
            return "模型返回 JSON 格式不完整，系统已尝试自动修复，请重新生成或减少主要参考材料长度。";
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private String callDeepSeek(String prompt) throws IOException, InterruptedException {
        return callDeepSeek(prompt, 4500, 100);
    }

    private String callDeepSeek(String prompt, int maxTokens, int timeoutSeconds) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.4,
                "max_tokens", maxTokens,
                "stream", false
        );

        String requestBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new IllegalStateException("DeepSeek 请求超过 " + timeoutSeconds + " 秒未返回，请稍后重试或减少主要参考材料长度。", cause);
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("DeepSeek 请求失败：" + (cause == null ? e.getMessage() : cause.getMessage()), cause);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DeepSeek HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private ObjectNode assembleLessonJson(
            LessonPlanRequest request,
            JsonNode basicSection,
            JsonNode processSection,
            JsonNode taskSection,
            JsonNode evaluationSection
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode basicInfo = objectMapper.createObjectNode();
        ObjectNode generationContext = objectMapper.createObjectNode();
        basicInfo.put("courseName", textOrDefault(request.getCourseName(), "未填写"));
        basicInfo.put("topic", textOrDefault(request.getTopic(), "未填写"));
        basicInfo.put("major", textOrDefault(request.getMajor(), "未填写"));
        basicInfo.put("grade", textOrDefault(request.getGrade(), "未填写"));
        basicInfo.put("targetStudents", textOrDefault(request.getTargetStudents(), "未填写"));
        basicInfo.put("lessonType", textOrDefault(request.getLessonType(), "未填写"));
        basicInfo.put("teachingMode", textOrDefault(request.getTeachingMode(), "未填写"));
        basicInfo.put("totalMinutes", valueOrDefault(request.getPeriodCount(), 2) * valueOrDefault(request.getMinutesPerPeriod(), 40));
        root.set("basicInfo", basicInfo);
        generationContext.put("prerequisiteKnowledge", textOrDefault(request.getPrerequisiteKnowledge(), ""));
        generationContext.put("commonMisconceptions", textOrDefault(request.getCommonMisconceptions(), ""));
        generationContext.put("classLevelProfile", textOrDefault(request.getClassLevelProfile(), ""));
        generationContext.put("lessonFocus", textOrDefault(request.getLessonFocus(), ""));
        generationContext.put("expectedOutputs", textOrDefault(request.getExpectedOutputs(), ""));
        root.set("generationContext", generationContext);
        root.set("referenceMaterials", objectMapper.valueToTree(request.getReferenceMaterials() == null ? List.of() : request.getReferenceMaterials()));
        root.set("teachingCalendar", objectMapper.valueToTree(request.getTeachingCalendar()));

        copyField(root, basicSection, "studentAnalysis");
        copyField(root, basicSection, "studentProblems");
        copyField(root, basicSection, "objectives");
        copyField(root, basicSection, "keyPoints");
        copyField(root, basicSection, "difficultPoints");
        copyField(root, basicSection, "teachingMethods");
        copyField(root, basicSection, "resources");
        copyField(root, basicSection, "ideologyDesign");
        copyField(root, processSection, "teachingProcess");
        copyField(root, taskSection, "practiceTask");
        copyField(root, taskSection, "codeExamples");
        copyField(root, evaluationSection, "homework");
        copyField(root, evaluationSection, "evaluationDesign");
        copyField(root, evaluationSection, "rubric");
        copyField(root, evaluationSection, "reflection");
        return root;
    }

    private void copyField(ObjectNode target, JsonNode source, String fieldName) {
        if (source.has(fieldName)) {
            target.set(fieldName, source.path(fieldName));
        }
    }

    private String extractContent(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new IllegalStateException("DeepSeek 返回内容为空");
        }
        return contentNode.asText();
    }

    private String normalizeJson(String content, LessonPlanRequest request) throws JsonProcessingException {
        String trimmed = content.trim();
        JsonNode parsed = objectMapper.readTree(trimmed);
        validateContent(parsed, request);
        return objectMapper.writeValueAsString(parsed);
    }

    private void validateContent(JsonNode root, LessonPlanRequest request) {
        if (!root.has("basicInfo") || !root.has("objectives") || !root.has("teachingProcess")) {
            throw new IllegalStateException("DeepSeek 返回 JSON 缺少 basicInfo/objectives/teachingProcess 核心字段");
        }
        if (containsAny(root.toString(), List.of("待补充", "暂无", "视情况", "根据实际情况调整", "根据需要补充"))) {
            throw new IllegalStateException("DeepSeek 返回内容包含占位或敷衍文本");
        }
        if (root.path("studentAnalysis").asText("").length() < 120) {
            throw new IllegalStateException("DeepSeek 返回学情分析过短，缺少完整段落");
        }
        if (hasVagueObjective(root.path("objectives"))) {
            throw new IllegalStateException("DeepSeek 返回教学目标存在不可衡量表述");
        }
        JsonNode teachingProcess = root.path("teachingProcess");
        if (!teachingProcess.isArray() || teachingProcess.size() < 5) {
            throw new IllegalStateException("DeepSeek 返回教学过程少于 5 个环节");
        }
        if (root.path("keyPoints").size() < 4 || root.path("difficultPoints").size() < 3) {
            throw new IllegalStateException("DeepSeek 返回教学重难点不完整");
        }
        if (hasBlankStructuredField(root.path("keyPoints"), "point") || hasBlankStructuredField(root.path("difficultPoints"), "point")
                || hasBlankStructuredField(root.path("difficultPoints"), "strategy")) {
            throw new IllegalStateException("DeepSeek 返回的重点或难点缺少结构化字段");
        }
        if (root.path("teachingMethods").size() < 2 || root.path("resources").size() < 3) {
            throw new IllegalStateException("DeepSeek 返回教学方法或教学资源不完整");
        }
        int totalMinutes = valueOrDefault(request.getPeriodCount(), 2) * valueOrDefault(request.getMinutesPerPeriod(), 40);
        int durationSum = 0;
        int lectureMinutes = 0;
        for (JsonNode row : teachingProcess) {
            int duration = row.path("duration").asInt(0);
            durationSum += duration;
            String stage = row.path("stage").asText("");
            if (stage.contains("理论") || stage.contains("讲解") || stage.contains("知识")) {
                lectureMinutes += duration;
            }
            if (row.path("evaluation").asText("").isBlank()) {
                throw new IllegalStateException("DeepSeek 返回教学过程存在空评价方式");
            }
            if (row.path("output").asText("").isBlank() || row.path("checkpoint").asText("").isBlank()) {
                throw new IllegalStateException("DeepSeek 返回教学过程缺少课堂产出或检查点");
            }
            if (row.path("teacherActivity").asText("").length() < 20 || row.path("studentActivity").asText("").length() < 20) {
                throw new IllegalStateException("DeepSeek 返回教学过程活动描述过短");
            }
        }
        if (durationSum != totalMinutes) {
            throw new IllegalStateException("DeepSeek 返回教学过程总时长不等于课时总时长");
        }
        if (isPracticeLesson(request.getLessonType()) && lectureMinutes > Math.max(10, Math.round(totalMinutes * 0.15f))) {
            throw new IllegalStateException("DeepSeek 返回实验课理论讲解时间过长");
        }
        JsonNode task = root.path("practiceTask");
        int layeredTaskCount = task.path("basicTasks").size() + task.path("advancedTasks").size() + task.path("challengeTasks").size();
        if (layeredTaskCount < 3 || task.path("acceptanceCriteria").size() < 3) {
            throw new IllegalStateException("DeepSeek 返回课堂任务缺少分层任务或验收标准");
        }
        String allText = root.toString();
        if (isJavaPolymorphismLesson(request)) {
            if (!containsAny(allText, List.of("Developer", "Tester", "Engineer", "Sales", "Intern"))
                    || !allText.contains("Manager") || !allText.contains("instanceof") || !allText.contains("ClassCastException")) {
                throw new IllegalStateException("DeepSeek 返回 Java 继承与多态实验任务不够具体");
            }
            if (!containsAny(allText, List.of("至少 3 个子类", "至少3个子类", "三个子类", "3 个子类", "3个子类"))) {
                throw new IllegalStateException("DeepSeek 返回 Java 实验任务未体现至少 3 个子类的复杂度");
            }
        }
        if (!Boolean.FALSE.equals(request.getIncludeObe()) && root.path("evaluationDesign").size() < 4) {
            throw new IllegalStateException("DeepSeek 返回 OBE 评价设计不完整");
        }
        if (!Boolean.FALSE.equals(request.getIncludeObe())
                && (!containsAny(root.path("evaluationDesign").toString(), List.of("毕业要求", "指标点", "课程目标", "OBE"))
                || !containsAny(root.path("evaluationDesign").toString(), List.of("证据", "权重")))) {
            throw new IllegalStateException("DeepSeek 返回 OBE 评价缺少指标点或证据链");
        }
        if (hasBlankStructuredField(root.path("evaluationDesign"), "item") || hasBlankStructuredField(root.path("evaluationDesign"), "weight")
                || hasBlankStructuredField(root.path("evaluationDesign"), "evidence") || hasBlankStructuredField(root.path("evaluationDesign"), "standard")) {
            throw new IllegalStateException("DeepSeek 返回评价设计缺少评价项、权重、证据或标准");
        }
    }

    private boolean hasVagueObjective(JsonNode objectives) {
        for (String field : List.of("knowledge", "ability", "quality")) {
            JsonNode rows = objectives.path(field);
            if (!rows.isArray()) {
                return true;
            }
            for (JsonNode item : rows) {
                String text = item.asText("").trim();
                if (text.isBlank() || text.startsWith("掌握") || text.startsWith("理解")
                        || text.startsWith("了解") || text.startsWith("熟悉") || text.startsWith("具备")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String value, List<String> words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBlankStructuredField(JsonNode array, String field) {
        if (!array.isArray()) {
            return true;
        }
        for (JsonNode item : array) {
            if (!item.isObject() || item.path(field).asText("").isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isPracticeLesson(String lessonType) {
        return lessonType != null && (lessonType.contains("实验") || lessonType.contains("实践") || lessonType.contains("理实"));
    }

    private boolean isJavaPolymorphismLesson(LessonPlanRequest request) {
        String text = (request.getCourseName() + " " + request.getTopic() + " " + request.getMajor() + " " + request.getLessonType()).toLowerCase();
        return text.contains("java") || text.contains("继承") || text.contains("多态");
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String systemPrompt() {
        return """
                你是成都东软学院应用型本科课程的教案生成助手。
                必须只输出合法 JSON，不要输出 Markdown，不要输出解释性文字。
                JSON 必须适合后端直接解析和前端展示。
                生成内容要体现应用型本科、任务驱动、实践能力培养、课程思政自然融入和 OBE 目标支撑。
                严格避免空话、套话和泛泛描述。每个教学活动必须具体到教师做什么、学生做什么、产出什么、如何评价。
                如果提供教学日历，必须按教学日历限定本次课主题和课时，不要把整章内容塞进一份教案。
                不允许输出“待补充”“根据实际情况”“相关知识”“掌握/理解/具备”等模板化目标。
                """;
    }

    private String buildBasicSectionPrompt(LessonPlanRequest request) {
        return """
                请只生成教案的“学情、目标、重难点、方法资源”JSON 片段。
                输出字段必须且只能包含：
                studentAnalysis, studentProblems, objectives, keyPoints, difficultPoints, teachingMethods, resources, ideologyDesign。

                要求：
                - studentAnalysis 写成 2 个自然段，总字数 140-220 字，必须结合专业、年级、先修基础、常见误区、强弱差异和本节课要解决的问题；如果提供了“先修基础、常见误区、班级情况”，必须优先吸收，不允许忽略。
                - studentProblems 给 3 条对象，每条包含 problem、evidence、strategy、assessment。
                - objectives 包含 knowledge、ability、quality、obeSupport 四组数组，每组 2-3 条；目标必须用“说出、区分、解释、设计、实现、调试、评价、提交”等可观察动词开头，并优先对齐“本节重点”和“预期产出”。
                - keyPoints 至少 4 条，必须返回对象数组，每条包含 point、reason。
                - difficultPoints 至少 3 条，必须返回对象数组，每条包含 point、reason、strategy。
                - teachingMethods 必须返回数组，至少 3 条，每条说明一种方法、适用环节和教师操作，并与本节重点或任务类型有直接对应关系。
                - resources 必须返回数组，至少 4 条，必须包含课件、案例材料、课堂任务单、评价证据收集方式，并优先吸收“预期产出”中需要的材料或提交物。
                - ideologyDesign 只在课程思政为“是”时写 2-3 条，必须返回对象数组，每条包含 stage、carrier、integration，且绑定具体案例、任务或评价环节。
                - 不允许返回空数组，不允许把多个条目合并成一个长字符串。

                课程信息：
                %s
                """.formatted(compactRequestInfo(request));
    }

    private String buildTeachingProcessSectionPrompt(LessonPlanRequest request) {
        int totalMinutes = valueOrDefault(request.getPeriodCount(), 2) * valueOrDefault(request.getMinutesPerPeriod(), 40);
        return """
                请只生成教案的“教学过程”JSON 片段。
                输出字段必须且只能包含：teachingProcess。

                要求：
                - teachingProcess 至少 6 行，duration 总和必须严格等于 %d 分钟。
                - 每行包含 stage、duration、teacherActivity、studentActivity、output、checkpoint、designPurpose、resources、evaluation。
                - teacherActivity 和 studentActivity 必须是完整句子，写清楚教师做什么、学生做什么。
                - output 必须写清课堂产出，checkpoint 必须写清教师如何检查，不允许为空。
                - 必须设置阶段检查点，避免学生进度失控。
                - 如果是实验课，理论讲解总时长不超过 10 分钟；如果是理论课，也要包含案例分析、讨论或课堂产出。
                - output 和 checkpoint 必须围绕“预期产出”生成，不允许泛泛写成“完成学习”“完成讨论”。
                - 每行只写课堂实施动作，不要把作业、评价量表和代码全文塞进 teachingProcess。

                软件工程或 Java 继承与多态相关课程必须出现 instanceof、ClassCastException、至少 3 个子类和可检查提交物。

                课程信息：
                %s
                """.formatted(totalMinutes, compactRequestInfo(request));
    }

    private String buildPracticeTaskSectionPrompt(LessonPlanRequest request) {
        return """
                请只生成教案的“课堂任务与代码材料”JSON 片段。
                输出字段必须且只能包含：practiceTask, codeExamples。

                要求：
                - practiceTask 包含 taskName、scenario、basicTasks、advancedTasks、challengeTasks、steps、acceptanceCriteria、commonErrors。
                - basicTasks、advancedTasks、challengeTasks 都必须返回数组且至少各 1 条。实验课必须写基础、提高、挑战任务；理论课也要写基础理解、课堂研讨和拓展迁移任务。
                - steps 写 4-6 条可执行步骤，每一步说明学生要交付什么。
                - acceptanceCriteria 必须至少 3 条。理论课也必须给出可检查证据，例如课堂讨论记录、概念辨析表、案例分析单、随堂测结果或学习反思，并与“预期产出”一一对应。
                - commonErrors 写 3 条，包含错误表现和教师干预办法。
                - codeExamples 至少 1 条，包含 title、language、purpose、code；代码控制在 18 行以内，保留缩进和注释。
                - 软件工程或 Java 继承与多态相关课程必须出现 instanceof、ClassCastException、至少 3 个子类和可检查提交物。

                课程信息：
                %s
                """.formatted(compactRequestInfo(request));
    }

    private String buildEvaluationSectionPrompt(LessonPlanRequest request) {
        return """
                请只生成教案的“作业、评价、Rubric、课后反思”JSON 片段。
                输出字段必须且只能包含：homework, evaluationDesign, rubric, reflection。

                要求：
                - homework 3-4 条，覆盖基础巩固、拓展挑战、提交物、教师反馈方式。
                - evaluationDesign 至少 4 条，必须返回对象数组，每条包含 item、weight、evidence、standard；整体必须包含教师评价、学生自评、同伴互评、OBE 证据链和权重，且 evidence 要能映射到课堂产出或任务提交物。
                - rubric 至少 4 个对象，每个对象包含 criterion、weight、excellent、qualified、evidence。
                - reflection 写成 1 个完整自然段，说明本节课预计达成效果、可能风险和后续改进点。
                - 不允许写“待补充”“根据实际情况”等占位表达。

                课程信息：
                %s
                """.formatted(compactRequestInfo(request));
    }

    private String buildJsonRepairPrompt(String invalidJson, String sectionName) {
        return """
                下面是 DeepSeek 刚刚生成的“%s”JSON 片段，但括号、逗号、引号或转义格式有错误，导致系统无法解析。
                请只输出修复后的合法 JSON 对象，不要解释，不要 Markdown，不要代码块。

                严格要求：
                1. 顶层必须是一个 JSON object。
                2. 只修复 JSON 语法错误，尽量保留原有字段名、原有教学内容和原有数组结构。
                3. 可以补齐缺失的 }、]、逗号、引号和必要转义。
                4. 不要新增“待补充”“暂无”“根据实际情况”等占位内容。
                5. 如果某段代码中包含换行、引号或反斜杠，必须转义为合法 JSON 字符串。

                原始 JSON 片段：
                %s
                """.formatted(sectionName, abbreviate(invalidJson, 7000));
    }

    private String compactRequestInfo(LessonPlanRequest request) {
        int periodCount = request.getPeriodCount() == null ? 2 : request.getPeriodCount();
        int minutesPerPeriod = request.getMinutesPerPeriod() == null ? 40 : request.getMinutesPerPeriod();
        return """
                课程名称：%s
                章节主题：%s
                授课专业：%s
                年级：%s
                授课对象：%s
                课程类型：%s
                教学模式/方法组合：%s
                总时长：%d 分钟
                先修基础：%s
                常见误区：%s
                班级情况：%s
                本节重点：%s
                预期产出：%s
                学情描述：%s
                教学资源：%s
                其他要求：%s
                %s
                %s
                """.formatted(
                textOrDefault(request.getCourseName(), "未填写"),
                textOrDefault(request.getTopic(), "未填写"),
                textOrDefault(request.getMajor(), "未填写"),
                textOrDefault(request.getGrade(), "未填写"),
                textOrDefault(request.getTargetStudents(), "未填写"),
                textOrDefault(request.getLessonType(), "未填写"),
                textOrDefault(request.getTeachingMode(), "未填写"),
                periodCount * minutesPerPeriod,
                abbreviate(textOrDefault(request.getPrerequisiteKnowledge(), "未填写"), 300),
                abbreviate(textOrDefault(request.getCommonMisconceptions(), "未填写"), 300),
                abbreviate(textOrDefault(request.getClassLevelProfile(), "未填写"), 300),
                abbreviate(textOrDefault(request.getLessonFocus(), "未填写"), 300),
                abbreviate(textOrDefault(request.getExpectedOutputs(), "未填写"), 300),
                abbreviate(textOrDefault(request.getStudentAnalysis(), "未填写"), 600),
                abbreviate(textOrDefault(request.getExperimentEnv(), "未填写"), 400),
                abbreviate(textOrDefault(request.getExtraRequirements(), "无"), 1000),
                referenceMaterialsSummary(request),
                teachingCalendarSummary(request)
        );
    }

    private String referenceMaterialsSummary(LessonPlanRequest request) {
        List<ReferenceMaterialDto> materials = request.getReferenceMaterials();
        if (materials == null || materials.isEmpty()) {
            return "参考资料：无";
        }
        StringBuilder builder = new StringBuilder("参考资料摘要：\n");
        int remaining = 15000;
        int index = 1;
        List<ReferenceMaterialDto> sorted = materials.stream()
                .filter(item -> item != null && !isBlank(item.getFileName()))
                .sorted(Comparator.comparing((ReferenceMaterialDto item) -> !"primary".equalsIgnoreCase(item.getRole())))
                .toList();
        for (ReferenceMaterialDto item : sorted) {
            if (remaining <= 0) {
                break;
            }
            String source = firstNonBlank(item.getExcerpt(), item.getExtractedText());
            if (source.isBlank()) {
                continue;
            }
            String excerpt = abbreviate(source, 4000);
            if (excerpt.length() > remaining) {
                excerpt = excerpt.substring(0, remaining);
            }
            builder.append(index++)
                    .append(". ")
                    .append("primary".equalsIgnoreCase(item.getRole()) ? "[主参考资料] " : "")
                    .append(item.getFileName())
                    .append("（")
                    .append(textOrDefault(item.getFileType(), "unknown"))
                    .append("，")
                    .append(item.getCharCount() == null ? excerpt.length() : item.getCharCount())
                    .append("字）\n")
                    .append(excerpt)
                    .append("\n");
            remaining -= excerpt.length();
        }
        if (index == 1) {
            return "参考资料：无";
        }
        return builder.toString().trim();
    }

    private String teachingCalendarSummary(LessonPlanRequest request) {
        TeachingCalendarDto calendar = request.getTeachingCalendar();
        if (calendar == null || calendar.getEntries() == null || calendar.getEntries().isEmpty()) {
            return "教学日历：无";
        }
        StringBuilder builder = new StringBuilder("教学日历安排：如果当前章节主题能匹配下列某一行或相邻几行，只生成该次课教案，不要扩写整章。\n");
        int index = 1;
        for (TeachingCalendarEntryDto entry : calendar.getEntries()) {
            if (entry == null || index > 40) {
                break;
            }
            String topic = firstNonBlank(entry.getTopic(), entry.getRawText());
            if (topic.isBlank()) {
                continue;
            }
            builder.append(index++)
                    .append(". ")
                    .append(textOrDefault(entry.getWeek(), ""))
                    .append(" ")
                    .append(textOrDefault(entry.getSession(), ""))
                    .append(" ")
                    .append(textOrDefault(entry.getLessonType(), ""))
                    .append(" ")
                    .append(entry.getPeriodCount() == null ? "" : entry.getPeriodCount() + "学时 ")
                    .append(topic)
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n……";
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (!isBlank(preferred)) {
            return preferred.trim();
        }
        return isBlank(fallback) ? "" : fallback.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String textOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
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
}
