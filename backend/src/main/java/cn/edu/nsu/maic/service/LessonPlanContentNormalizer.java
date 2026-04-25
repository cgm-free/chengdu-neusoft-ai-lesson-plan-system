package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.LessonPlanDetail;
import cn.edu.nsu.maic.dto.LessonPlanRequest;
import cn.edu.nsu.maic.dto.LessonPlanSaveRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LessonPlanContentNormalizer {

    private final ObjectMapper objectMapper;

    public LessonPlanContentNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalizeForRequest(String contentJson, LessonPlanRequest request) {
        return writeNormalized(normalizeRoot(parseRoot(contentJson), Metadata.fromRequest(request)));
    }

    public String normalizeForSave(String contentJson, LessonPlanDetail existing, LessonPlanSaveRequest request) {
        return writeNormalized(normalizeRoot(parseRoot(contentJson), Metadata.fromSave(existing, request)));
    }

    public String normalizeForDetail(String contentJson, LessonPlanDetail detail) {
        return writeNormalized(normalizeRoot(parseRoot(contentJson), Metadata.fromDetail(detail)));
    }

    private ObjectNode parseRoot(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(contentJson);
            if (node != null && node.isObject()) {
                return (ObjectNode) node;
            }
            throw new IllegalStateException("content_json 不是合法 JSON 对象");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("content_json 解析失败", e);
        }
    }

    private String writeNormalized(ObjectNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("教案内容归一化失败", e);
        }
    }

    private ObjectNode normalizeRoot(ObjectNode root, Metadata meta) {
        JsonNode practiceTaskSource = firstPresent(root.path("practiceTask"));
        JsonNode evaluationDesignSource = firstPresent(root.path("evaluationDesign"), root.path("evaluation").path("evaluationDesign"));
        JsonNode rubricSource = firstPresent(root.path("rubric"), root.path("practiceTask").path("rubric"), root.path("evaluation").path("rubric"));
        JsonNode codeExamplesSource = firstPresent(root.path("codeExamples"), root.path("practiceTask").path("codeExamples"));
        root.set("basicInfo", normalizeBasicInfo(root.path("basicInfo"), meta));
        root.set("generationContext", normalizeGenerationContext(root.path("generationContext"), meta));
        root.set("referenceMaterials", normalizeReferenceMaterials(root.path("referenceMaterials")));
        root.set("teachingCalendar", normalizeTeachingCalendar(root.path("teachingCalendar")));
        root.put("studentAnalysis", textValue(root.path("studentAnalysis")));
        root.set("studentProblems", normalizeStudentProblems(root.path("studentProblems")));
        root.set("objectives", normalizeObjectives(root.path("objectives")));
        root.set("keyPoints", normalizeStructuredArray(root.path("keyPoints"), List.of("point", "reason")));
        root.set("difficultPoints", normalizeStructuredArray(root.path("difficultPoints"), List.of("point", "reason", "strategy")));
        root.set("teachingMethods", normalizeTextArray(root.path("teachingMethods")));
        root.set("resources", normalizeTextArray(root.path("resources")));
        root.set("ideologyDesign", normalizeStructuredArray(root.path("ideologyDesign"), List.of("stage", "carrier", "integration")));
        root.set("teachingProcess", normalizeTeachingProcess(root.path("teachingProcess")));
        root.set("practiceTask", normalizePracticeTask(practiceTaskSource));
        root.set("homework", normalizeTextArray(root.path("homework")));
        root.set("evaluationDesign", normalizeStructuredArray(evaluationDesignSource, List.of("item", "weight", "evidence", "standard")));
        root.set("rubric", normalizeRubric(rubricSource));
        root.set("codeExamples", normalizeCodeExamples(codeExamplesSource));
        root.put("reflection", textValue(root.path("reflection")));
        return root;
    }

    private ObjectNode normalizeBasicInfo(JsonNode node, Metadata meta) {
        ObjectNode normalized = objectMapper.createObjectNode();
        JsonNode source = node != null && node.isObject() ? node : objectMapper.createObjectNode();
        normalized.put("courseName", pick(meta.courseName(), textValue(source.path("courseName"))));
        normalized.put("topic", pick(meta.topic(), textValue(source.path("topic"))));
        normalized.put("major", pick(meta.major(), textValue(source.path("major"))));
        normalized.put("grade", pick(meta.grade(), textValue(source.path("grade"))));
        normalized.put("targetStudents", pick(meta.targetStudents(), textValue(source.path("targetStudents"))));
        normalized.put("lessonType", pick(meta.lessonType(), textValue(source.path("lessonType"))));
        normalized.put("teachingMode", pick(meta.teachingMode(), textValue(source.path("teachingMode"))));
        normalized.put("periodCount", meta.periodCount() != null ? meta.periodCount() : intValue(source.path("periodCount"), 2));
        normalized.put("minutesPerPeriod", meta.minutesPerPeriod() != null ? meta.minutesPerPeriod() : intValue(source.path("minutesPerPeriod"), 40));
        normalized.put("totalMinutes", normalized.path("periodCount").asInt(2) * normalized.path("minutesPerPeriod").asInt(40));
        return normalized;
    }

    private ObjectNode normalizeGenerationContext(JsonNode node, Metadata meta) {
        ObjectNode normalized = objectMapper.createObjectNode();
        JsonNode source = node != null && node.isObject() ? node : objectMapper.createObjectNode();
        normalized.put("prerequisiteKnowledge", pick(meta.prerequisiteKnowledge(), textValue(source.path("prerequisiteKnowledge"))));
        normalized.put("commonMisconceptions", pick(meta.commonMisconceptions(), textValue(source.path("commonMisconceptions"))));
        normalized.put("classLevelProfile", pick(meta.classLevelProfile(), textValue(source.path("classLevelProfile"))));
        normalized.put("lessonFocus", pick(meta.lessonFocus(), textValue(source.path("lessonFocus"))));
        normalized.put("expectedOutputs", pick(meta.expectedOutputs(), textValue(source.path("expectedOutputs"))));
        return normalized;
    }

    private ArrayNode normalizeStudentProblems(JsonNode node) {
        ArrayNode normalized = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return normalized;
        }
        for (JsonNode item : node) {
            ObjectNode row = objectMapper.createObjectNode();
            if (item != null && item.isObject()) {
                row.put("problem", textValue(item.path("problem")));
                row.put("evidence", textValue(item.path("evidence")));
                row.put("strategy", textValue(item.path("strategy")));
                row.put("assessment", textValue(item.path("assessment")));
            } else {
                row.put("problem", textValue(item));
                row.put("evidence", "");
                row.put("strategy", "");
                row.put("assessment", "");
            }
            normalized.add(row);
        }
        return normalized;
    }

    private ArrayNode normalizeReferenceMaterials(JsonNode node) {
        ArrayNode normalized = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return normalized;
        }
        LinkedHashMap<String, ObjectNode> deduplicated = new LinkedHashMap<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode row = objectMapper.createObjectNode();
            String fileName = textValue(item.path("fileName"));
            if (fileName.isBlank()) {
                continue;
            }
            row.put("id", textValue(item.path("id")));
            row.put("fileName", fileName);
            row.put("fileType", textValue(item.path("fileType")));
            row.put("charCount", intValue(item.path("charCount"), textValue(item.path("extractedText")).length()));
            row.put("excerpt", textValue(item.path("excerpt")));
            row.put("extractedText", textValue(item.path("extractedText")));
            row.put("role", "primary".equalsIgnoreCase(textValue(item.path("role"))) ? "primary" : "secondary");
            row.put("uploadedAt", textValue(item.path("uploadedAt")));
            row.put("extractionMethod", textValue(item.path("extractionMethod")));
            row.put("ocrStatus", textValue(item.path("ocrStatus")));
            if (!item.path("pageCount").isMissingNode() && !item.path("pageCount").isNull()) {
                row.put("pageCount", intValue(item.path("pageCount"), 0));
            }
            if (!item.path("ocrConfidence").isMissingNode() && !item.path("ocrConfidence").isNull()) {
                row.put("ocrConfidence", item.path("ocrConfidence").asDouble(0D));
            }
            deduplicated.remove(fileName.toLowerCase());
            deduplicated.put(fileName.toLowerCase(), row);
        }
        boolean hasPrimary = false;
        for (ObjectNode item : deduplicated.values()) {
            if (!hasPrimary && "primary".equals(item.path("role").asText())) {
                hasPrimary = true;
                normalized.add(item);
                continue;
            }
            item.put("role", "secondary");
            normalized.add(item);
        }
        if (!hasPrimary && !normalized.isEmpty()) {
            ((ObjectNode) normalized.get(0)).put("role", "primary");
        }
        return normalized;
    }

    private ObjectNode normalizeTeachingCalendar(JsonNode node) {
        ObjectNode normalized = objectMapper.createObjectNode();
        JsonNode source = node != null && node.isObject() ? node : objectMapper.createObjectNode();
        normalized.put("fileName", textValue(source.path("fileName")));
        normalized.put("fileType", textValue(source.path("fileType")));
        normalized.put("rowCount", intValue(source.path("rowCount"), 0));
        normalized.put("excerpt", textValue(source.path("excerpt")));
        normalized.put("uploadedAt", textValue(source.path("uploadedAt")));
        ArrayNode entries = objectMapper.createArrayNode();
        JsonNode rawEntries = source.path("entries");
        if (rawEntries.isArray()) {
            for (JsonNode item : rawEntries) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                ObjectNode row = objectMapper.createObjectNode();
                row.put("week", textValue(item.path("week")));
                row.put("session", textValue(item.path("session")));
                if (!item.path("periodCount").isMissingNode() && !item.path("periodCount").isNull()) {
                    row.put("periodCount", intValue(item.path("periodCount"), 0));
                } else {
                    row.putNull("periodCount");
                }
                row.put("lessonType", textValue(item.path("lessonType")));
                row.put("topic", textValue(item.path("topic")));
                row.put("rawText", textValue(item.path("rawText")));
                if (!textValue(row.path("topic")).isBlank() || !textValue(row.path("rawText")).isBlank()) {
                    entries.add(row);
                }
            }
        }
        normalized.set("entries", entries);
        normalized.put("rowCount", entries.size());
        if (normalized.path("excerpt").asText("").isBlank() && !entries.isEmpty()) {
            normalized.put("excerpt", buildTeachingCalendarExcerpt(entries));
        }
        return normalized;
    }

    private String buildTeachingCalendarExcerpt(ArrayNode entries) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode entry : entries) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(String.join(" ",
                    textValue(entry.path("week")),
                    textValue(entry.path("session")),
                    textValue(entry.path("lessonType")),
                    textValue(entry.path("topic"))
            ).trim());
            if (builder.length() >= 1500) {
                return builder.substring(0, 1500);
            }
        }
        return builder.toString().trim();
    }

    private ObjectNode normalizeObjectives(JsonNode node) {
        ObjectNode normalized = objectMapper.createObjectNode();
        JsonNode source = node != null && node.isObject() ? node : objectMapper.createObjectNode();
        normalized.set("knowledge", normalizeTextArray(source.path("knowledge")));
        normalized.set("ability", normalizeTextArray(source.path("ability")));
        normalized.set("quality", normalizeTextArray(source.path("quality")));
        normalized.set("obeSupport", normalizeTextArray(source.path("obeSupport")));
        return normalized;
    }

    private ArrayNode normalizeTeachingProcess(JsonNode node) {
        ArrayNode normalized = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return normalized;
        }
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            ObjectNode row = objectMapper.createObjectNode();
            row.put("stage", textValue(item.path("stage")));
            row.put("duration", intValue(item.path("duration"), 5));
            row.put("teacherActivity", textValue(item.path("teacherActivity")));
            row.put("studentActivity", textValue(item.path("studentActivity")));
            row.put("output", textValue(item.path("output")));
            row.put("checkpoint", textValue(item.path("checkpoint")));
            row.put("designPurpose", textValue(item.path("designPurpose")));
            row.put("resources", textValue(item.path("resources")));
            row.put("evaluation", textValue(item.path("evaluation")));
            normalized.add(row);
        }
        return normalized;
    }

    private ObjectNode normalizePracticeTask(JsonNode node) {
        ObjectNode normalized = objectMapper.createObjectNode();
        JsonNode source = node != null && node.isObject() ? node : objectMapper.createObjectNode();
        normalized.put("taskName", textValue(source.path("taskName")));
        normalized.put("scenario", textValue(source.path("scenario")));
        ArrayNode basicTasks = normalizeTextArray(source.path("basicTasks"));
        ArrayNode advancedTasks = normalizeTextArray(source.path("advancedTasks"));
        ArrayNode challengeTasks = normalizeTextArray(source.path("challengeTasks"));
        if (basicTasks.isEmpty() && advancedTasks.isEmpty() && challengeTasks.isEmpty() && source.path("requirements").isArray()) {
            List<String> legacy = asTextList(source.path("requirements"));
            for (int i = 0; i < legacy.size(); i++) {
                String value = legacy.get(i);
                if (value.contains("基础")) {
                    basicTasks.add(value);
                } else if (value.contains("提高")) {
                    advancedTasks.add(value);
                } else if (value.contains("挑战")) {
                    challengeTasks.add(value);
                } else if (basicTasks.isEmpty()) {
                    basicTasks.add(value);
                } else if (advancedTasks.isEmpty()) {
                    advancedTasks.add(value);
                } else {
                    challengeTasks.add(value);
                }
            }
        }
        normalized.set("basicTasks", basicTasks);
        normalized.set("advancedTasks", advancedTasks);
        normalized.set("challengeTasks", challengeTasks);
        normalized.set("steps", normalizeTextArray(source.path("steps")));
        normalized.set("acceptanceCriteria", normalizeTextArray(source.path("acceptanceCriteria")));
        normalized.set("commonErrors", normalizeTextArray(source.path("commonErrors")));
        return normalized;
    }

    private ArrayNode normalizeRubric(JsonNode node) {
        return normalizeStructuredArray(node, List.of("criterion", "weight", "excellent", "qualified", "evidence"));
    }

    private ArrayNode normalizeCodeExamples(JsonNode node) {
        ArrayNode normalized = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return normalized;
        }
        for (JsonNode item : node) {
            ObjectNode row = objectMapper.createObjectNode();
            if (item != null && item.isObject()) {
                row.put("title", textValue(item.path("title")));
                row.put("type", textValue(item.path("type")));
                row.put("language", textValue(item.path("language")));
                row.put("purpose", textValue(item.path("purpose")));
                row.put("code", textValue(item.path("code")));
            } else {
                row.put("title", "");
                row.put("type", "");
                row.put("language", "");
                row.put("purpose", "");
                row.put("code", textValue(item));
            }
            normalized.add(row);
        }
        return normalized;
    }

    private ArrayNode normalizeStructuredArray(JsonNode node, List<String> fields) {
        ArrayNode normalized = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return normalized;
        }
        for (JsonNode item : node) {
            ObjectNode row = objectMapper.createObjectNode();
            if (item != null && item.isObject()) {
                for (String field : fields) {
                    row.put(field, textValue(item.path(field)));
                }
            } else {
                String text = textValue(item);
                Map<String, String> parsed = parseLabeledText(text, fields);
                for (String field : fields) {
                    row.put(field, parsed.getOrDefault(field, ""));
                }
                if (fields.contains("point") && row.path("point").asText().isBlank()) {
                    row.put("point", text);
                }
                if (fields.contains("criterion") && row.path("criterion").asText().isBlank()) {
                    row.put("criterion", text);
                }
                if (fields.contains("item") && row.path("item").asText().isBlank()) {
                    row.put("item", text);
                }
            }
            normalized.add(row);
        }
        return normalized;
    }

    private ArrayNode normalizeTextArray(JsonNode node) {
        ArrayNode normalized = objectMapper.createArrayNode();
        for (String item : asTextList(node)) {
            normalized.add(item);
        }
        return normalized;
    }

    private List<String> asTextList(JsonNode node) {
        List<String> items = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return items;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = stringifyItem(item);
                if (!text.isBlank()) {
                    items.add(text);
                }
            }
            return items;
        }
        String text = stringifyItem(node);
        if (!text.isBlank()) {
            items.add(text);
        }
        return items;
    }

    private String stringifyItem(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (!node.isObject()) {
            return textValue(node);
        }
        List<String> parts = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            String value = textValue(entry.getValue());
            if (!value.isBlank()) {
                parts.add(value);
            }
        });
        return String.join("；", parts).trim();
    }

    private Map<String, String> parseLabeledText(String text, List<String> fields) {
        ObjectNode holder = objectMapper.createObjectNode();
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            String label = fieldLabel(field);
            StringBuilder nextLabels = new StringBuilder();
            for (int j = i + 1; j < fields.size(); j++) {
                if (nextLabels.length() > 0) {
                    nextLabels.append("|");
                }
                nextLabels.append(Pattern.quote(fieldLabel(fields.get(j)))).append("[：:]");
            }
            Pattern pattern = Pattern.compile(Pattern.quote(label) + "[：:]\\s*(.*?)(?=(?:" + nextLabels + ")|$)");
            Matcher matcher = pattern.matcher(text == null ? "" : text);
            if (matcher.find()) {
                holder.put(field, matcher.group(1).trim().replaceAll("；$", ""));
            }
        }
        return objectMapper.convertValue(holder, Map.class);
    }

    private String fieldLabel(String field) {
        return switch (field) {
            case "point" -> "重点内容";
            case "reason" -> "说明";
            case "strategy" -> "突破策略";
            case "stage" -> "融入环节";
            case "carrier" -> "融入载体";
            case "integration" -> "融入设计";
            case "problem" -> "学情问题";
            case "assessment" -> "评价证据";
            case "item" -> "评价项";
            case "weight" -> "权重";
            case "evidence" -> "评价证据";
            case "standard" -> "达标标准";
            case "criterion" -> "评价维度";
            case "excellent" -> "优秀标准";
            case "qualified" -> "达标标准";
            case "title" -> "标题";
            case "type" -> "类型";
            case "language" -> "语言";
            case "purpose" -> "用途说明";
            case "code" -> "代码";
            default -> field;
        };
    }

    private JsonNode firstPresent(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && !candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }
        return objectMapper.createObjectNode();
    }

    private int intValue(JsonNode node, int defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        if (node.isNumber()) {
            return node.asInt(defaultValue);
        }
        String text = textValue(node).replaceAll("[^0-9-]", "");
        if (text.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String pick(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred.trim() : fallback;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private record Metadata(
            String courseName,
            String topic,
            String major,
            String grade,
            String targetStudents,
            String lessonType,
            String teachingMode,
            Integer periodCount,
            Integer minutesPerPeriod,
            String prerequisiteKnowledge,
            String commonMisconceptions,
            String classLevelProfile,
            String lessonFocus,
            String expectedOutputs
    ) {
        static Metadata fromRequest(LessonPlanRequest request) {
            return new Metadata(
                    request.getCourseName(),
                    request.getTopic(),
                    request.getMajor(),
                    request.getGrade(),
                    request.getTargetStudents(),
                    request.getLessonType(),
                    request.getTeachingMode(),
                    request.getPeriodCount(),
                    request.getMinutesPerPeriod(),
                    request.getPrerequisiteKnowledge(),
                    request.getCommonMisconceptions(),
                    request.getClassLevelProfile(),
                    request.getLessonFocus(),
                    request.getExpectedOutputs()
            );
        }

        static Metadata fromDetail(LessonPlanDetail detail) {
            return new Metadata(
                    detail.getCourseName(),
                    detail.getTopic(),
                    detail.getMajor(),
                    detail.getGrade(),
                    detail.getTargetStudents(),
                    detail.getLessonType(),
                    detail.getTeachingMode(),
                    detail.getPeriodCount(),
                    detail.getMinutesPerPeriod(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        static Metadata fromSave(LessonPlanDetail existing, LessonPlanSaveRequest request) {
            return new Metadata(
                    pick(request.getCourseName(), existing.getCourseName()),
                    pick(request.getTopic(), existing.getTopic()),
                    pick(request.getMajor(), existing.getMajor()),
                    pick(request.getGrade(), existing.getGrade()),
                    pick(request.getTargetStudents(), existing.getTargetStudents()),
                    pick(request.getLessonType(), existing.getLessonType()),
                    pick(request.getTeachingMode(), existing.getTeachingMode()),
                    request.getPeriodCount() != null && request.getPeriodCount() > 0 ? request.getPeriodCount() : existing.getPeriodCount(),
                    request.getMinutesPerPeriod() != null && request.getMinutesPerPeriod() > 0 ? request.getMinutesPerPeriod() : existing.getMinutesPerPeriod(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        private static String pick(String preferred, String fallback) {
            return preferred != null && !preferred.isBlank() ? preferred.trim() : fallback;
        }
    }
}
