package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CoursePlanAiGenerationService {

    private static final int MIN_MAIN_CONTENT_CHARS = 1500;
    private static final int TARGET_MAIN_CONTENT_CHARS = 1700;
    private static final int TEACHING_DESIGN_AI_ATTEMPTS = 3;
    private static final int MIN_BLOCK_POINTS = 2;
    private static final int MAX_BLOCK_POINTS = 4;
    private static final List<String> MAIN_ACTIVITY_TAGS = List.of(
            "概念建构",
            "原理讲解",
            "案例分析",
            "代码演示",
            "对比辨析",
            "错误诊断",
            "实验操作",
            "课堂讨论",
            "即时测验",
            "项目推进",
            "练习巩固",
            "阶段小结",
            "问题探究"
    );
    private static final List<String> FORBIDDEN_MAIN_CONTENT_TITLE_FRAGMENTS = List.of(
            "课堂案例与互动",
            "演示与练习",
            "教师反馈与评价",
            "演示与互动",
            "练习安排"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${maic.ai.provider:deepseek}")
    private String provider;

    @Value("${maic.ai.model-name:deepseek-v4-flash}")
    private String modelName;

    @Value("${maic.ai.api-key:}")
    private String apiKey;

    @Value("${maic.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    public CoursePlanAiGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public CoursePlanDtos.DocumentContent enhance(CoursePlanDtos.AnalysisResult analysis, CoursePlanDtos.DocumentContent draft) {
        return enhance(analysis, draft, CoursePlanGenerationProgress.NOOP);
    }

    public CoursePlanDtos.DocumentContent enhance(
            CoursePlanDtos.AnalysisResult analysis,
            CoursePlanDtos.DocumentContent draft,
            CoursePlanGenerationProgress progress
    ) {
        if (analysis == null || draft == null) {
            throw new IllegalArgumentException("缺少课程教案分析结果或草稿内容，不能调用大模型加工。");
        }
        requireConfigured();
        ProgressCounter counter = new ProgressCounter(progress, generationStepCount(draft));
        counter.update("courseResources", "生成课程首页教学资源");
        ResourcePolishResult resources = polishResources(analysis, draft);
        counter.step();
        List<CoursePlanDtos.GeneratedUnit> units = new ArrayList<>();
        progress.snapshot(analysis, buildEnhancedContentSnapshot(draft, resources, units));
        for (CoursePlanDtos.GeneratedUnit unit : safeList(draft.units())) {
            units.add(polishUnit(analysis, unit, counter));
            progress.snapshot(analysis, buildEnhancedContentSnapshot(draft, resources, units));
        }
        return buildEnhancedContentSnapshot(draft, resources, units);
    }

    private CoursePlanDtos.DocumentContent buildEnhancedContentSnapshot(
            CoursePlanDtos.DocumentContent draft,
            ResourcePolishResult resources,
            List<CoursePlanDtos.GeneratedUnit> units
    ) {
        return new CoursePlanDtos.DocumentContent(
                draft.basicInfo(),
                draft.title(),
                draft.teacherRequirements(),
                resources.textbooksAndReferences(),
                resources.otherTeachingResources(),
                resources.courseEnvironment(),
                new ArrayList<>(safeList(units)),
                draft.warnings()
        );
    }

    private CoursePlanDtos.GeneratedUnit polishUnit(
            CoursePlanDtos.AnalysisResult analysis,
            CoursePlanDtos.GeneratedUnit unit,
            ProgressCounter counter
    ) {
        CoursePlanDtos.UnitAnalysis unitAnalysis = findUnitAnalysis(analysis, unit.index());
        counter.update("unitEnvironment", "生成第" + unit.index() + "单元首页教学环境");
        String environmentDesign = polishUnitEnvironment(analysis, unit, unitAnalysis);
        counter.step();
        List<CoursePlanDtos.TeachingDesign> teachingDesigns = new ArrayList<>();
        for (CoursePlanDtos.TeachingDesign design : safeList(unit.teachingDesigns())) {
            counter.update(
                    "teachingDesign",
                    "生成第" + unit.index() + "单元 第" + design.index() + "次课：" + text(design.title())
            );
            teachingDesigns.add(polishTeachingDesign(analysis, unit, unitAnalysis, design, counter));
            counter.step();
        }
        return new CoursePlanDtos.GeneratedUnit(
                unit.index(),
                unit.code(),
                unit.name(),
                unit.hours(),
                unit.teachingDesignCount(),
                environmentDesign,
                unit.projectName(),
                unit.theoryObjectives(),
                unit.skillObjectives(),
                unit.qualityObjectives(),
                unit.keyPoints(),
                unit.difficultPoints(),
                unit.teachingMethods(),
                unit.teachingOrganization(),
                unit.projectIntroduction(),
                unit.matchedPpts(),
                teachingDesigns,
                unit.resources(),
                unit.assessments()
        );
    }

    private ResourcePolishResult polishResources(CoursePlanDtos.AnalysisResult analysis, CoursePlanDtos.DocumentContent draft) {
        CoursePlanDtos.SourceContext sourceContext = analysis.sourceContext();
        if (sourceContext == null) {
            throw new IllegalStateException("缺少课程标准来源信息，不能生成教学资源文本。");
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("courseName", text(analysis.basicInfo() == null ? "" : analysis.basicInfo().courseName()));
        context.put("teacherRequirements", text(analysis.teacherRequirements()));
        context.put("textbookEvidence", compactList(sourceContext.textbooksAndReferences(), 12, 420));
        context.put("resourceEvidence", compactList(sourceContext.otherTeachingResources(), 14, 420));
        context.put("pptTitles", collectPptTitles(sourceContext));
        context.put("draftCourseEnvironment", text(draft.courseEnvironment()));
        JsonNode root = requestJson("""
                请把以下课程标准与课件证据整理成课程教案首页可直接填写的正式文本。
                严格要求：
                1. 只返回 JSON，不要 Markdown，不要解释。
                2. 不要直接粘贴原文长段，要提取有效信息后重新组织表达。
                3. textbooksAndReferences 只写教材、参考书、课程标准类资料，按条目简洁列出。
                4. otherTeachingResources 只写实验指导书、题库、微课、视频、网站、平台资源等。
                5. courseEnvironment 只写教学材料、课程平台、课件材料、授课安排等短行；不得混入学习策略、做笔记建议、课程教学基本条件说明。
                6. 禁止输出“学习策略与技巧”“好记性不如烂笔头”“做笔记是个好习惯”“课程教学基本条件”等原文噪声。
                JSON 格式：
                {
                  "textbooksAndReferences": "教材及参考资料正文",
                  "otherTeachingResources": "其他教学资源正文",
                  "courseEnvironment": "教学环境短行，使用换行分隔"
                }

                证据：
                %s
                """.formatted(toJson(context)), 2200, 90, "教学资源加工");
        ResourcePolishResult result = new ResourcePolishResult(
                requiredText(root, "textbooksAndReferences", "教学资源加工"),
                requiredText(root, "otherTeachingResources", "教学资源加工"),
                requiredText(root, "courseEnvironment", "教学资源加工")
        );
        validateNoNoise("教学资源加工", result.textbooksAndReferences(), result.otherTeachingResources(), result.courseEnvironment());
        return result;
    }

    private String polishUnitEnvironment(
            CoursePlanDtos.AnalysisResult analysis,
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.UnitAnalysis unitAnalysis
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("courseName", text(analysis.basicInfo() == null ? "" : analysis.basicInfo().courseName()));
        context.put("unitName", text(unit.name()));
        context.put("matchedPpts", compactList(unit.matchedPpts(), 6, 80));
        context.put("unitResources", compactList(unit.resources(), 8, 120));
        context.put("unitProject", text(unit.projectName()));
        context.put("calendarTopics", compactList(unitAnalysis == null ? List.of() : calendarTopics(unitAnalysis), 8, 90));
        JsonNode root = requestJson("""
                请为课程教案“单元首页”的教学环境设计生成正式文本。
                只返回 JSON，不要 Markdown。
                要求：只写短项，避免重复；不得直接粘贴课程标准原文；不得出现学习策略、做笔记建议。
                JSON 格式：{"environmentDesign":"多行短项"}

                证据：
                %s
                """.formatted(toJson(context)), 900, 60, "单元教学环境加工");
        String environmentDesign = requiredText(root, "environmentDesign", "单元教学环境加工");
        validateNoNoise("单元教学环境加工", environmentDesign);
        return environmentDesign;
    }

    private CoursePlanDtos.TeachingDesign polishTeachingDesign(
            CoursePlanDtos.AnalysisResult analysis,
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.UnitAnalysis unitAnalysis,
            CoursePlanDtos.TeachingDesign draft,
            ProgressCounter counter
    ) {
        int mainMinutes = safeList(draft.mainContentBlocks()).stream()
                .map(CoursePlanDtos.MainContentBlock::minutes)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();
        if (mainMinutes <= 0) {
            throw new IllegalStateException("教学设计“" + draft.title() + "”缺少主要内容时间，不能调用大模型加工。");
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("courseName", text(analysis.basicInfo() == null ? "" : analysis.basicInfo().courseName()));
        context.put("teacherRequirements", text(analysis.teacherRequirements()));
        context.put("unitName", text(unit.name()));
        context.put("unitKeyPoints", compactList(unit.keyPoints(), 8, 100));
        context.put("unitDifficultPoints", compactList(unit.difficultPoints(), 8, 100));
        context.put("unitObjectives", compactList(mergeLists(unit.theoryObjectives(), unit.skillObjectives(), unit.qualityObjectives()), 8, 120));
        context.put("calendarTopics", compactList(unitAnalysis == null ? List.of() : calendarTopics(unitAnalysis), 10, 100));
        context.put("pptHeadings", compactList(unitAnalysis == null ? List.of() : unitAnalysis.slideHeadings(), 18, 80));
        context.put("contentItems", compactList(unitAnalysis == null ? List.of() : unitAnalysis.contentItems(), 12, 120));
        context.put("implementationSuggestions", compactList(unitAnalysis == null ? List.of() : unitAnalysis.implementationSuggestions(), 8, 120));
        context.put("draftTeachingDesign", toDraftDesignMap(draft, mainMinutes));

        TeachingDesignOutline outline = generateFullTeachingDesign(unit, draft, mainMinutes, context);
        List<CoursePlanDtos.MainContentBlock> mainContentBlocks = repairMainContentBlocks(unit, draft, outline.mainContentBlocks(), context, counter);
        try {
            validateTeachingDesignResult(draft.title(), mainContentBlocks, outline.summary(), outline.assignments(), outline.remarks());
        } catch (IllegalStateException e) {
            boolean assignmentFailure = e.getMessage() != null && e.getMessage().contains("课外学习要求");
            throw generationException(
                    unit,
                    draft,
                    assignmentFailure ? "assignments" : "mainContent",
                    "",
                    assignmentFailure ? null : mainContentChars(mainContentBlocks),
                    assignmentFailure ? null : MIN_MAIN_CONTENT_CHARS,
                    e.getMessage(),
                    e
            );
        }
        return new CoursePlanDtos.TeachingDesign(
                draft.index(),
                draft.title(),
                draft.focus(),
                draft.totalMinutes(),
                draft.afterClassReviewMinutes(),
                draft.afterClassReview(),
                draft.introductionMinutes(),
                outline.introduction(),
                mainContentBlocks,
                draft.summaryMinutes(),
                outline.summary(),
                draft.assignmentMinutes(),
                outline.assignments(),
                draft.matchedSlides(),
                outline.remarks()
        );
    }

    private TeachingDesignOutline generateFullTeachingDesign(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            int mainMinutes,
            Map<String, Object> context
    ) {
        IllegalStateException lastFailure = null;
        String retryFeedback = "";
        for (int attempt = 1; attempt <= TEACHING_DESIGN_AI_ATTEMPTS; attempt++) {
            try {
                JsonNode root = requestJson(
                        buildFullTeachingDesignPrompt(mainMinutes, context, retryFeedback),
                        7000,
                        180,
                        "教学设计完整加工"
                );
                List<CoursePlanDtos.MainContentBlock> mainContentBlocks = repairMainContentOutlineBlocks(
                        unit,
                        draft,
                        parseMainContentBlocks(root, mainMinutes, draft.title()),
                        context
                );
                String introduction = requiredText(root, "introduction", "教学设计完整加工");
                String summary = repairSummaryIfNeeded(
                        unit,
                        draft,
                        context,
                        requiredText(root, "summary", "教学设计完整加工")
                );
                AssignmentPlan assignmentPlan;
                try {
                    assignmentPlan = parseAssignmentPlan(root, draft.title());
                } catch (IllegalStateException e) {
                    assignmentPlan = repairAssignments(unit, draft, context, e.getMessage());
                }
                List<String> assignments = assignmentPlan.toList();
                List<String> remarks = requiredTextList(root, "remarks", "教学设计完整加工");
                validateOutlineResult(draft.title(), introduction, mainContentBlocks, summary, assignments, remarks);
                validateNoNoise("教学设计完整加工", introduction, summary, String.join("\n", assignments), String.join("\n", remarks));
                return new TeachingDesignOutline(introduction, mainContentBlocks, summary, assignments, remarks);
            } catch (CoursePlanGenerationException e) {
                throw e;
            } catch (IllegalStateException e) {
                lastFailure = e;
                retryFeedback = e.getMessage();
            }
        }
        throw generationException(
                unit,
                draft,
                "teachingDesign",
                "",
                null,
                null,
                "教学设计完整加工连续 " + TEACHING_DESIGN_AI_ATTEMPTS + " 次未通过校验："
                        + (lastFailure == null ? "" : lastFailure.getMessage()),
                lastFailure
        );
    }

    private List<CoursePlanDtos.MainContentBlock> repairMainContentBlocks(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            List<CoursePlanDtos.MainContentBlock> generatedBlocks,
            Map<String, Object> context,
            ProgressCounter counter
    ) {
        List<CoursePlanDtos.MainContentBlock> repaired = new ArrayList<>();
        int requiredCharsPerBlock = Math.max(180, (int) Math.ceil((double) MIN_MAIN_CONTENT_CHARS / Math.max(1, generatedBlocks.size())));
        for (int blockIndex = 0; blockIndex < generatedBlocks.size(); blockIndex++) {
            CoursePlanDtos.MainContentBlock block = generatedBlocks.get(blockIndex);
            int actualChars = mainContentChars(block.title(), block.points());
            try {
                validateExpandedBlock(block, block.points(), requiredCharsPerBlock, actualChars);
                repaired.add(block);
            } catch (IllegalStateException e) {
                counter.update(
                        "mainContentRetry",
                        "重试第" + unit.index() + "单元 第" + draft.index() + "次课内容块：" + text(block.title())
                );
                repaired.add(expandMainContentBlock(unit, draft, block, blockIndex + 1, requiredCharsPerBlock, context));
            }
        }

        int totalChars = mainContentChars(repaired);
        if (totalChars < MIN_MAIN_CONTENT_CHARS) {
            int targetIndex = shortestBlockIndex(repaired);
            CoursePlanDtos.MainContentBlock block = repaired.get(targetIndex);
            int requiredChars = mainContentChars(block.title(), block.points())
                    + (MIN_MAIN_CONTENT_CHARS - totalChars)
                    + 180;
            counter.update(
                    "mainContentRetry",
                    "补强第" + unit.index() + "单元 第" + draft.index() + "次课主要内容：" + text(block.title())
            );
            repaired.set(targetIndex, expandMainContentBlock(unit, draft, block, targetIndex + 1, requiredChars, context));
        }
        return repaired;
    }

    private String buildFullTeachingDesignPrompt(int mainMinutes, Map<String, Object> context, String retryFeedback) {
        String retryInstruction = text(retryFeedback).isBlank()
                ? ""
                : """

                上一次输出未通过校验：%s
                本次必须重新生成完整 JSON，不要复用不合格结果。
                """.formatted(retryFeedback);
        return """
                请基于以下“本次课 80 分钟知识点与证据包”，生成课程教案教学设计页的正式内容。
                严格要求：
                1. 只返回 JSON，不要 Markdown，不要解释。
                2. 不要直接粘贴输入原文长段；必须提炼、组织、润色成高校课堂教案表达。
                3. introduction 写 2-3 行：复习旧课、问题/案例导入、本次任务说明。
                4. mainContentBlocks 必须 5-8 项；每项 title 只能有 1 个活动类型标签，标签必须从 %s 中选择；活动类型可按教学需要重复，但标题中的具体教学任务不能重复；不要输出 minutes，系统会按 %d 分钟自动分配每块时间。
                5. mainContentBlocks.points 每项必须是完整扩写后的教学内容，每个 point 不少于 80 个中文字符，全部 mainContentBlocks 合计目标 1700-2000 字，底线 1500 字。
                6. 每个主要内容块必须围绕本节课不同教学任务展开，例如概念辨析、案例分析、代码追踪、错误诊断、实验操作、课堂讨论、即时测验、项目推进；禁止每块都写同一套“讲解+案例+演示+反馈”流程。
                7. title 禁止出现【课堂案例与互动】【演示与练习】【教师反馈与评价】这类多标签堆叠或固定套路。
                8. summary 要求学生针对本节课知识点、重点和难点进行归纳总结，点明已解决问题和待解决问题。
                9. assignments 必须是结构化对象，必须包含课程调研、3-5 道习题设计、一个结合本节内容的开放性小项目、提交方式、提交标准。
                10. 禁止输出学校英文名、Logo、页脚、页码、学习策略、做笔记建议等噪声。%s
                JSON 格式：
                {
                  "introduction": "内容导入正文",
                  "mainContentBlocks": [
                    {"title":"【概念建构】围绕本次课主题的具体标题", "points":["扩写后的具体教学内容1","扩写后的具体教学内容2"]}
                  ],
                  "summary": "归纳总结正文",
                  "assignments": {
                    "researchTask": "课程调研任务，必须结合本次课主题",
                    "exerciseTasks": ["习题1", "习题2", "习题3"],
                    "openProject": "开放性小项目，必须结合本次课主题",
                    "submissionMethod": "提交方式",
                    "submissionStandard": "提交标准"
                  },
                  "remarks": ["右侧备注短项1", "右侧备注短项2"]
                }

                证据包：
                %s
                """.formatted(String.join("、", MAIN_ACTIVITY_TAGS), mainMinutes, retryInstruction, toJson(context));
    }

    private TeachingDesignOutline generateTeachingDesignOutline(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            int mainMinutes,
            Map<String, Object> context
    ) {
        IllegalStateException lastFailure = null;
        String retryFeedback = "";
        for (int attempt = 1; attempt <= TEACHING_DESIGN_AI_ATTEMPTS; attempt++) {
            try {
                JsonNode root = requestJson(
                        buildTeachingDesignOutlinePrompt(mainMinutes, context, retryFeedback),
                        3600,
                        120,
                        "教学设计大纲加工"
                );
                List<CoursePlanDtos.MainContentBlock> mainContentBlocks = repairMainContentOutlineBlocks(
                        unit,
                        draft,
                        parseMainContentBlocks(root, mainMinutes, draft.title()),
                        context
                );
                String introduction = requiredText(root, "introduction", "教学设计大纲加工");
                String summary = repairSummaryIfNeeded(
                        unit,
                        draft,
                        context,
                        requiredText(root, "summary", "教学设计大纲加工")
                );
                AssignmentPlan assignmentPlan;
                try {
                    assignmentPlan = parseAssignmentPlan(root, draft.title());
                } catch (IllegalStateException e) {
                    assignmentPlan = repairAssignments(unit, draft, context, e.getMessage());
                }
                List<String> assignments = assignmentPlan.toList();
                List<String> remarks = requiredTextList(root, "remarks", "教学设计大纲加工");
                validateOutlineResult(draft.title(), introduction, mainContentBlocks, summary, assignments, remarks);
                return new TeachingDesignOutline(introduction, mainContentBlocks, summary, assignments, remarks);
            } catch (CoursePlanGenerationException e) {
                throw e;
            } catch (IllegalStateException e) {
                lastFailure = e;
                retryFeedback = e.getMessage();
            }
        }
        throw generationException(
                unit,
                draft,
                "mainContentOutline",
                "",
                null,
                null,
                "教学设计大纲连续 " + TEACHING_DESIGN_AI_ATTEMPTS + " 次未通过校验："
                        + (lastFailure == null ? "" : lastFailure.getMessage()),
                lastFailure
        );
    }

    private List<CoursePlanDtos.MainContentBlock> expandMainContentBlocks(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            List<CoursePlanDtos.MainContentBlock> outlineBlocks,
            Map<String, Object> context
    ) {
        List<CoursePlanDtos.MainContentBlock> expanded = new ArrayList<>();
        int requiredCharsPerBlock = Math.max(250, (int) Math.ceil((double) TARGET_MAIN_CONTENT_CHARS / Math.max(1, outlineBlocks.size())));
        for (int blockIndex = 0; blockIndex < outlineBlocks.size(); blockIndex++) {
            expanded.add(expandMainContentBlock(unit, draft, outlineBlocks.get(blockIndex), blockIndex + 1, requiredCharsPerBlock, context));
        }
        return expanded;
    }

    private List<CoursePlanDtos.MainContentBlock> repairMainContentOutlineBlocks(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            List<CoursePlanDtos.MainContentBlock> blocks,
            Map<String, Object> context
    ) {
        List<CoursePlanDtos.MainContentBlock> repaired = new ArrayList<>();
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            CoursePlanDtos.MainContentBlock block = blocks.get(blockIndex);
            if (safeList(block.points()).size() >= MIN_BLOCK_POINTS && safeList(block.points()).size() <= MAX_BLOCK_POINTS) {
                repaired.add(block);
                continue;
            }
            repaired.add(repairMainContentOutlineBlock(unit, draft, block, blockIndex + 1, context));
        }
        return repaired;
    }

    private CoursePlanDtos.MainContentBlock repairMainContentOutlineBlock(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            CoursePlanDtos.MainContentBlock block,
            int blockIndex,
            Map<String, Object> context
    ) {
        IllegalStateException lastFailure = null;
        String retryFeedback = "当前主要内容块 points 数量为 " + safeList(block.points()).size()
                + "，必须为 2-4 个。";
        for (int attempt = 1; attempt <= TEACHING_DESIGN_AI_ATTEMPTS; attempt++) {
            try {
                JsonNode root = requestJson(
                        buildMainContentOutlineBlockPrompt(draft, block, blockIndex, context, retryFeedback),
                        1200,
                        60,
                        "主要内容大纲块加工"
                );
                String title = requiredText(root, "title", "主要内容大纲块加工");
                validateMainContentBlockTitle(title);
                List<String> points = requiredTextList(root, "points", "主要内容大纲块加工");
                if (points.size() < MIN_BLOCK_POINTS || points.size() > MAX_BLOCK_POINTS) {
                    throw new IllegalStateException("主要内容块 points 数量必须为 2-4 个，当前 " + points.size() + " 个。");
                }
                validateNoNoise("主要内容大纲块加工", title, String.join("\n", points));
                return new CoursePlanDtos.MainContentBlock(title, block.minutes(), points);
            } catch (IllegalStateException e) {
                lastFailure = e;
                retryFeedback = e.getMessage();
            }
        }
        throw generationException(
                unit,
                draft,
                "mainContentOutline",
                block.title(),
                null,
                null,
                "主要内容大纲块“" + block.title() + "”连续 " + TEACHING_DESIGN_AI_ATTEMPTS
                        + " 次未通过结构化校验：" + (lastFailure == null ? "" : lastFailure.getMessage()),
                lastFailure
        );
    }

    private CoursePlanDtos.MainContentBlock expandMainContentBlock(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            CoursePlanDtos.MainContentBlock outlineBlock,
            int blockIndex,
            int requiredChars,
            Map<String, Object> context
    ) {
        IllegalStateException lastFailure = null;
        String retryFeedback = "";
        int lastActualChars = 0;
        for (int attempt = 1; attempt <= TEACHING_DESIGN_AI_ATTEMPTS; attempt++) {
            try {
                JsonNode root = requestJson(
                        buildMainContentBlockPrompt(draft, outlineBlock, blockIndex, requiredChars, context, retryFeedback),
                        2200,
                        90,
                        "主要内容块扩写"
                );
                List<String> points = requiredTextList(root, "points", "主要内容块扩写");
                lastActualChars = mainContentChars(outlineBlock.title(), points);
                validateExpandedBlock(outlineBlock, points, requiredChars, lastActualChars);
                return new CoursePlanDtos.MainContentBlock(outlineBlock.title(), outlineBlock.minutes(), points);
            } catch (IllegalStateException e) {
                lastFailure = e;
                retryFeedback = e.getMessage();
            }
        }
        throw generationException(
                unit,
                draft,
                "mainContent",
                outlineBlock.title(),
                lastActualChars,
                requiredChars,
                "主要内容块“" + outlineBlock.title() + "”连续 " + TEACHING_DESIGN_AI_ATTEMPTS
                        + " 次扩写未通过校验：" + (lastFailure == null ? "" : lastFailure.getMessage()),
                lastFailure
        );
    }

    private String buildTeachingDesignOutlinePrompt(int mainMinutes, Map<String, Object> context, String retryFeedback) {
        String retryInstruction = text(retryFeedback).isBlank()
                ? ""
                : """

                上一次输出未通过校验：%s
                本次必须重新生成完整 JSON，不要复用不合格结果。
                """.formatted(retryFeedback);
        return """
                请基于以下“本次课 80 分钟知识点与证据包”，生成课程教案教学设计页的大纲和非主要内容字段。
                严格要求：
                1. 只返回 JSON，不要 Markdown，不要解释。
                2. 不要直接粘贴输入原文长段；必须提炼、组织、润色成高校课堂教案表达。
                3. introduction 写 2-3 行：复习旧课、问题/案例导入、本次任务说明。
                4. mainContentBlocks 必须 5-8 项；每项 title 只能有 1 个活动类型标签，标签必须从 %s 中选择；活动类型可按教学需要重复，但标题中的具体教学任务不能重复；不要输出 minutes，系统会按 %d 分钟自动分配每块时间。
                5. mainContentBlocks.points 只写每个内容块要展开的 2-4 个要点短句，不要在本步骤扩写长文。
                6. 每个内容块必须对应不同教学任务，禁止所有块都套用“课堂案例与互动、演示与练习、教师反馈与评价”固定组合。
                7. title 禁止出现多组【】标签，只允许一个活动类型标签加一个具体主题标题。
                8. summary 要求学生针对本节课知识点、重点和难点进行归纳总结，点明已解决问题和待解决问题。
                9. assignments 必须是结构化对象，必须包含课程调研、3-5 道习题设计、一个结合本节内容的开放性小项目、提交方式、提交标准。
                10. 禁止输出学校英文名、Logo、页脚、页码、学习策略、做笔记建议等噪声。%s
                JSON 格式：
                {
                  "introduction": "内容导入正文",
                  "mainContentBlocks": [
                    {"title":"【案例分析】围绕本次课主题的具体标题", "points":["要点短句1","要点短句2"]}
                  ],
                  "summary": "归纳总结正文",
                  "assignments": {
                    "researchTask": "课程调研任务，必须结合本次课主题",
                    "exerciseTasks": ["习题1", "习题2", "习题3"],
                    "openProject": "开放性小项目，必须结合本次课主题",
                    "submissionMethod": "提交方式",
                    "submissionStandard": "提交标准"
                  },
                  "remarks": ["右侧备注短项1", "右侧备注短项2"]
                }

                证据包：
                %s
                """.formatted(String.join("、", MAIN_ACTIVITY_TAGS), mainMinutes, retryInstruction, toJson(context));
    }

    private String buildMainContentOutlineBlockPrompt(
            CoursePlanDtos.TeachingDesign draft,
            CoursePlanDtos.MainContentBlock block,
            int blockIndex,
            Map<String, Object> context,
            String retryFeedback
    ) {
        Map<String, Object> blockContext = new LinkedHashMap<>(context);
        blockContext.put("lessonTitle", text(draft.title()));
        blockContext.put("lessonFocus", text(draft.focus()));
        blockContext.put("blockIndex", blockIndex);
        blockContext.put("originalBlockTitle", text(block.title()));
        blockContext.put("originalPoints", safeList(block.points()));
        blockContext.put("requiredPointCount", "2-4");
        String retryInstruction = text(retryFeedback).isBlank()
                ? ""
                : """

                上一次主要内容大纲块未通过校验：%s
                本次只重新生成这一个 mainContentBlock。
                """.formatted(retryFeedback);
        return """
                请只重新生成课程教案“三、主要内容设计”中的一个主要内容块大纲。
                严格要求：
                1. 只返回 JSON，不要 Markdown，不要解释。
                2. title 只能有 1 个活动类型标签，标签必须从 %s 中选择，并结合本次课主题写出具体标题。
                3. points 必须为 2-4 项，每项是可扩写成正式教案正文的要点，必须结合本次课主题。
                4. 不要输出 minutes，系统会分配时间。
                5. 禁止使用【课堂案例与互动】【演示与练习】【教师反馈与评价】这类固定套路或多标签堆叠。
                6. 禁止输出学校英文名、Logo、页脚、页码、学习策略、做笔记建议等噪声。%s
                JSON 格式：{"title":"【错误诊断】围绕本次课主题的具体标题", "points":["要点1","要点2"]}

                证据包：
                %s
                """.formatted(String.join("、", MAIN_ACTIVITY_TAGS), retryInstruction, toJson(blockContext));
    }

    private String buildMainContentBlockPrompt(
            CoursePlanDtos.TeachingDesign draft,
            CoursePlanDtos.MainContentBlock block,
            int blockIndex,
            int requiredChars,
            Map<String, Object> context,
            String retryFeedback
    ) {
        String retryInstruction = text(retryFeedback).isBlank()
                ? ""
                : """

                上一次扩写未通过校验：%s
                本次必须重新扩写该内容块，points 合计不得少于 %d 个中文字符。
                """.formatted(retryFeedback, requiredChars);
        Map<String, Object> blockContext = new LinkedHashMap<>(context);
        blockContext.put("lessonTitle", text(draft.title()));
        blockContext.put("blockIndex", blockIndex);
        blockContext.put("blockTitle", text(block.title()));
        blockContext.put("blockMinutes", block.minutes());
        blockContext.put("outlinePoints", safeList(block.points()));
        blockContext.put("requiredChars", requiredChars);
        return """
                请只扩写课程教案“三、主要内容设计”中的一个内容块。
                严格要求：
                1. 只返回 JSON，不要 Markdown，不要解释。
                2. points 必须 2-4 项，合计不少于 %d 个中文字符；每项必须是正式教案表达，不少于 80 个中文字符。
                3. 扩写必须贴合当前内容块的活动类型，不要把每个内容块都写成“讲解、案例、演示、反馈”四件套。
                4. 根据 blockTitle 选择合适写法：概念建构重在定义和关系梳理，案例分析重在情境与推理，代码演示重在步骤和结果观察，错误诊断重在常见错误与纠正，实验操作重在任务流程和验收。
                5. 只能基于证据包，不编造教材外材料，不粘贴原文长段。
                6. 禁止输出学校英文名、Logo、页脚、页码、学习策略、做笔记建议等噪声。%s
                JSON 格式：
                {"points":["扩写后的具体教学内容1","扩写后的具体教学内容2"]}

                证据包：
                %s
                """.formatted(requiredChars, retryInstruction, toJson(blockContext));
    }

    private AssignmentPlan repairAssignments(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            Map<String, Object> context,
            String initialFailure
    ) {
        IllegalStateException lastFailure = null;
        String retryFeedback = text(initialFailure);
        for (int attempt = 1; attempt <= TEACHING_DESIGN_AI_ATTEMPTS; attempt++) {
            try {
                JsonNode root = requestJson(
                        buildAssignmentsPrompt(draft, context, retryFeedback),
                        1800,
                        90,
                        "课外学习要求加工"
                );
                return parseAssignmentPlan(root, draft.title());
            } catch (IllegalStateException e) {
                lastFailure = e;
                retryFeedback = e.getMessage();
            }
        }
        throw generationException(
                unit,
                draft,
                "assignments",
                "",
                null,
                null,
                "课外学习要求连续 " + TEACHING_DESIGN_AI_ATTEMPTS + " 次未通过结构化校验："
                        + (lastFailure == null ? "" : lastFailure.getMessage()),
                lastFailure
        );
    }

    private String repairSummaryIfNeeded(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            Map<String, Object> context,
            String summary
    ) {
        try {
            validateSummary(draft.title(), summary);
            return summary;
        } catch (IllegalStateException e) {
            return repairSummary(unit, draft, context, e.getMessage());
        }
    }

    private String repairSummary(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign draft,
            Map<String, Object> context,
            String initialFailure
    ) {
        IllegalStateException lastFailure = null;
        String retryFeedback = text(initialFailure);
        for (int attempt = 1; attempt <= TEACHING_DESIGN_AI_ATTEMPTS; attempt++) {
            try {
                JsonNode root = requestJson(
                        buildSummaryPrompt(draft, context, retryFeedback),
                        900,
                        60,
                        "归纳总结加工"
                );
                String summary = parseSummaryPlan(root, draft.title()).toText();
                validateSummary(draft.title(), summary);
                return summary;
            } catch (IllegalStateException e) {
                lastFailure = e;
                retryFeedback = e.getMessage();
            }
        }
        throw generationException(
                unit,
                draft,
                "summary",
                "",
                null,
                null,
                "归纳总结连续 " + TEACHING_DESIGN_AI_ATTEMPTS + " 次未通过结构化校验："
                        + (lastFailure == null ? "" : lastFailure.getMessage()),
                lastFailure
        );
    }

    private String buildSummaryPrompt(
            CoursePlanDtos.TeachingDesign draft,
            Map<String, Object> context,
            String retryFeedback
    ) {
        Map<String, Object> summaryContext = new LinkedHashMap<>(context);
        summaryContext.put("lessonTitle", text(draft.title()));
        summaryContext.put("lessonFocus", text(draft.focus()));
        String retryInstruction = text(retryFeedback).isBlank()
                ? ""
                : """

                上一次归纳总结未通过校验：%s
                本次只重新生成 summary 字段，必须补齐缺失要求。
                """.formatted(retryFeedback);
        return """
                请只生成课程教案“四、归纳总结”的结构化 JSON。
                严格要求：
                1. 只返回 JSON，不要 Markdown，不要解释。
                2. 必须结合本次课主题，不要写通用空话。
                3. 必须引导学生围绕本节课知识点、重点和难点进行归纳总结。
                4. summary 必须是结构化对象，不要返回自由文本；每个字段必须结合本次课主题。
                5. 禁止输出学校英文名、Logo、页脚、页码、学习策略、做笔记建议等噪声。%s
                JSON 格式：
                {
                  "summary": {
                    "knowledgeSummary": "本节课知识点归纳",
                    "keyPoints": "本节课重点",
                    "difficultPoints": "本节课难点",
                    "resolvedProblems": "本节课已解决的问题",
                    "pendingProblems": "后续需要继续解决的问题"
                  }
                }

                证据包：
                %s
                """.formatted(retryInstruction, toJson(summaryContext));
    }

    private SummaryPlan parseSummaryPlan(JsonNode root, String title) {
        JsonNode node = root.path("summary");
        if (!node.isObject()) {
            throw new IllegalStateException("教学设计“" + title + "”的归纳总结缺少 summary 结构化对象。");
        }
        SummaryPlan plan = new SummaryPlan(
                requiredText(node, "knowledgeSummary", "归纳总结"),
                requiredText(node, "keyPoints", "归纳总结"),
                requiredText(node, "difficultPoints", "归纳总结"),
                requiredText(node, "resolvedProblems", "归纳总结"),
                requiredText(node, "pendingProblems", "归纳总结")
        );
        validateNoNoise(
                "归纳总结",
                plan.knowledgeSummary(),
                plan.keyPoints(),
                plan.difficultPoints(),
                plan.resolvedProblems(),
                plan.pendingProblems()
        );
        return plan;
    }

    private String buildAssignmentsPrompt(
            CoursePlanDtos.TeachingDesign draft,
            Map<String, Object> context,
            String retryFeedback
    ) {
        Map<String, Object> assignmentContext = new LinkedHashMap<>(context);
        assignmentContext.put("lessonTitle", text(draft.title()));
        assignmentContext.put("lessonFocus", text(draft.focus()));
        String retryInstruction = text(retryFeedback).isBlank()
                ? ""
                : """

                上一次课外学习要求未通过校验：%s
                本次只重新生成 assignments 对象，必须补齐缺失字段。
                """.formatted(retryFeedback);
        return """
                请只生成课程教案“五、课外学习要求”的结构化 JSON。
                严格要求：
                1. 只返回 JSON，不要 Markdown，不要解释。
                2. 必须结合本次课主题，不要写通用空话。
                3. researchTask 写课程调研任务。
                4. exerciseTasks 必须 3-5 项，每项是一道围绕本次课知识点、重点或难点的习题。
                5. openProject 写一个结合本节内容的开放性小项目。
                6. submissionMethod 必须明确提交方式。
                7. submissionStandard 必须明确提交标准。
                8. 禁止输出学校英文名、Logo、页脚、页码、学习策略、做笔记建议等噪声。%s
                JSON 格式：
                {
                  "assignments": {
                    "researchTask": "课程调研任务",
                    "exerciseTasks": ["习题1", "习题2", "习题3"],
                    "openProject": "开放性小项目",
                    "submissionMethod": "提交方式",
                    "submissionStandard": "提交标准"
                  }
                }

                证据包：
                %s
                """.formatted(retryInstruction, toJson(assignmentContext));
    }

    private AssignmentPlan parseAssignmentPlan(JsonNode root, String title) {
        JsonNode node = root.path("assignments");
        if (!node.isObject()) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少 assignments 结构化对象。");
        }
        String researchTask = requiredText(node, "researchTask", "课外学习要求");
        List<String> exerciseTasks = requiredTextList(node, "exerciseTasks", "课外学习要求");
        String openProject = requiredText(node, "openProject", "课外学习要求");
        String submissionMethod = requiredText(node, "submissionMethod", "课外学习要求");
        String submissionStandard = requiredText(node, "submissionStandard", "课外学习要求");
        AssignmentPlan plan = new AssignmentPlan(
                researchTask,
                exerciseTasks,
                openProject,
                submissionMethod,
                submissionStandard
        );
        validateAssignmentPlan(title, plan);
        return plan;
    }

    private void validateAssignmentPlan(String title, AssignmentPlan plan) {
        if (text(plan.researchTask()).isBlank()) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少课程调研。");
        }
        if (safeList(plan.exerciseTasks()).size() < 3 || safeList(plan.exerciseTasks()).size() > 5) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求习题数量必须为 3-5 道。");
        }
        if (text(plan.openProject()).isBlank()) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少开放性项目。");
        }
        if (text(plan.submissionMethod()).isBlank()) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少提交方式。");
        }
        if (text(plan.submissionStandard()).isBlank()) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少提交标准。");
        }
        validateNoNoise(
                "课外学习要求",
                plan.researchTask(),
                String.join("\n", safeList(plan.exerciseTasks())),
                plan.openProject(),
                plan.submissionMethod(),
                plan.submissionStandard()
        );
    }

    private List<CoursePlanDtos.MainContentBlock> parseMainContentBlocks(JsonNode root, int expectedMinutes, String title) {
        JsonNode blocksNode = root.path("mainContentBlocks");
        if (!blocksNode.isArray()) {
            throw new IllegalStateException("教学设计“" + title + "”的大模型结果缺少 mainContentBlocks 数组。");
        }
        List<ParsedMainContentBlock> parsedBlocks = new ArrayList<>();
        for (JsonNode item : blocksNode) {
            String blockTitle = requiredText(item, "title", "主要内容设计");
            validateMainContentBlockTitle(blockTitle);
            List<String> points = requiredTextList(item, "points", "主要内容设计");
            validateNoNoise("主要内容设计大纲", blockTitle, String.join("\n", points));
            parsedBlocks.add(new ParsedMainContentBlock(blockTitle, points));
        }
        if (parsedBlocks.size() < 5 || parsedBlocks.size() > 8) {
            throw new IllegalStateException("教学设计“" + title + "”的主要内容设计必须为 5-8 项。");
        }
        List<Integer> minutes = distributeMainContentMinutes(expectedMinutes, parsedBlocks.size(), title);
        List<CoursePlanDtos.MainContentBlock> blocks = new ArrayList<>();
        for (int i = 0; i < parsedBlocks.size(); i++) {
            ParsedMainContentBlock block = parsedBlocks.get(i);
            blocks.add(new CoursePlanDtos.MainContentBlock(block.title(), minutes.get(i), block.points()));
        }
        validateMainContentBlockDiversity(title, blocks);
        return blocks;
    }

    private List<Integer> distributeMainContentMinutes(int expectedMinutes, int blockCount, String title) {
        if (blockCount < 5 || blockCount > 8) {
            throw new IllegalStateException("教学设计“" + title + "”的主要内容设计必须为 5-8 项。");
        }
        if (expectedMinutes < blockCount) {
            throw new IllegalStateException("教学设计“" + title + "”的主要内容时间不足以分配给 " + blockCount + " 个内容块。");
        }
        int base = expectedMinutes / blockCount;
        int remainder = expectedMinutes % blockCount;
        List<Integer> minutes = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) {
            minutes.add(base + (i < remainder ? 1 : 0));
        }
        return minutes;
    }

    private void validateOutlineResult(
            String title,
            String introduction,
            List<CoursePlanDtos.MainContentBlock> blocks,
            String summary,
            List<String> assignments,
            List<String> remarks
    ) {
        if (text(introduction).isBlank()) {
            throw new IllegalStateException("教学设计“" + title + "”的内容导入为空。");
        }
        validateNoNoise("教学设计大纲加工", introduction, summary, String.join("\n", assignments), String.join("\n", remarks));
        validateSummary(title, summary);
        validateFormattedAssignments(title, assignments);
        for (CoursePlanDtos.MainContentBlock block : blocks) {
            if (safeList(block.points()).size() < MIN_BLOCK_POINTS || safeList(block.points()).size() > MAX_BLOCK_POINTS) {
                throw new IllegalStateException("教学设计“" + title + "”的主要内容大纲每块必须包含 2-4 个要点。");
            }
        }
        validateMainContentBlockDiversity(title, blocks);
    }

    private void validateExpandedBlock(
            CoursePlanDtos.MainContentBlock block,
            List<String> points,
            int requiredChars,
            int actualChars
    ) {
        if (points.size() < MIN_BLOCK_POINTS || points.size() > MAX_BLOCK_POINTS) {
            throw new IllegalStateException("主要内容块“" + block.title() + "”必须扩写为 2-4 个 points。");
        }
        validateMainContentBlockTitle(block.title());
        for (String point : points) {
            if (text(point).length() < 80) {
                throw new IllegalStateException("主要内容块“" + block.title() + "”存在少于 80 字的 point。");
            }
        }
        if (actualChars < requiredChars) {
            throw new IllegalStateException("主要内容块“" + block.title() + "”扩写不足 "
                    + requiredChars + " 字，当前 " + actualChars + " 字。");
        }
        validateNoNoise("主要内容块扩写", block.title(), String.join("\n", points));
    }

    private void validateTeachingDesignResult(
            String title,
            List<CoursePlanDtos.MainContentBlock> blocks,
            String summary,
            List<String> assignments,
            List<String> remarks
    ) {
        StringBuilder mainText = new StringBuilder();
        for (CoursePlanDtos.MainContentBlock block : blocks) {
            mainText.append(block.title());
            for (String point : safeList(block.points())) {
                mainText.append(point);
            }
        }
        int mainContentChars = mainText.length();
        if (mainContentChars < MIN_MAIN_CONTENT_CHARS) {
            throw new IllegalStateException("教学设计“" + title + "”的大模型主要内容不足 1500 字，当前 "
                    + mainContentChars + " 字。");
        }
        validateMainContentBlockDiversity(title, blocks);
        validateSummary(title, summary);
        String assignmentText = String.join("\n", assignments);
        validateFormattedAssignments(title, assignments);
        validateNoNoise("教学设计加工", mainText.toString(), summary, assignmentText, String.join("\n", remarks));
    }

    private void validateSummary(String title, String summary) {
        String value = text(summary);
        if (value.isBlank()) {
            throw new IllegalStateException("教学设计“" + title + "”的归纳总结为空。");
        }
        validateNoNoise("归纳总结", value);
        if (!value.contains("重点") || !value.contains("难点")) {
            throw new IllegalStateException("教学设计“" + title + "”的归纳总结未体现重点和难点。");
        }
        if (!value.contains("已解决问题") || !value.contains("待解决问题")) {
            throw new IllegalStateException("教学设计“" + title + "”的归纳总结未体现已解决问题和待解决问题。");
        }
    }

    private void validateFormattedAssignments(String title, List<String> assignments) {
        List<String> values = safeList(assignments);
        if (values.size() < 5) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求结构不完整。");
        }
        String assignmentText = String.join("\n", values);
        if (!assignmentText.contains("课程调研：")) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少课程调研。");
        }
        if (!assignmentText.contains("习题设计：")) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少习题设计。");
        }
        if (!assignmentText.contains("开放性小项目：")) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少开放性项目。");
        }
        if (!assignmentText.contains("提交方式：")) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少提交方式。");
        }
        if (!assignmentText.contains("提交标准：")) {
            throw new IllegalStateException("教学设计“" + title + "”的课外学习要求缺少提交标准。");
        }
    }

    private int mainContentChars(String title, List<String> points) {
        StringBuilder builder = new StringBuilder(text(title));
        for (String point : safeList(points)) {
            builder.append(text(point));
        }
        return builder.length();
    }

    private int mainContentChars(List<CoursePlanDtos.MainContentBlock> blocks) {
        StringBuilder builder = new StringBuilder();
        for (CoursePlanDtos.MainContentBlock block : safeList(blocks)) {
            builder.append(text(block.title()));
            for (String point : safeList(block.points())) {
                builder.append(text(point));
            }
        }
        return builder.length();
    }

    private int shortestBlockIndex(List<CoursePlanDtos.MainContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalStateException("主要内容设计为空，不能补强。");
        }
        int index = 0;
        int minChars = Integer.MAX_VALUE;
        for (int i = 0; i < blocks.size(); i++) {
            CoursePlanDtos.MainContentBlock block = blocks.get(i);
            int chars = mainContentChars(block.title(), block.points());
            if (chars < minChars) {
                minChars = chars;
                index = i;
            }
        }
        return index;
    }

    private void validateMainContentBlockDiversity(String title, List<CoursePlanDtos.MainContentBlock> blocks) {
        LinkedHashSet<String> normalizedTitles = new LinkedHashSet<>();
        for (CoursePlanDtos.MainContentBlock block : safeList(blocks)) {
            String blockTitle = validateMainContentBlockTitle(block.title());
            String tag = extractMainActivityTag(blockTitle);
            String titleBody = blockTitle.replace("【" + tag + "】", "").trim();
            String normalizedTitle = normalize(titleBody);
            if (normalizedTitle.isBlank()) {
                throw new IllegalStateException("教学设计“" + title + "”的主要内容块标题缺少具体主题：" + blockTitle);
            }
            if (!normalizedTitles.add(normalizedTitle)) {
                throw new IllegalStateException("教学设计“" + title + "”的主要内容块标题重复：" + blockTitle);
            }
        }
    }

    private String validateMainContentBlockTitle(String value) {
        String title = text(value);
        if (title.isBlank()) {
            throw new IllegalStateException("主要内容块标题为空。");
        }
        String tag = extractMainActivityTag(title);
        if (!MAIN_ACTIVITY_TAGS.contains(tag)) {
            throw new IllegalStateException("主要内容块标题活动类型不在允许范围内：" + title);
        }
        for (String fragment : FORBIDDEN_MAIN_CONTENT_TITLE_FRAGMENTS) {
            if (title.contains(fragment)) {
                throw new IllegalStateException("主要内容块标题存在固定套路短语“" + fragment + "”：" + title);
            }
        }
        return title;
    }

    private String extractMainActivityTag(String value) {
        String title = text(value);
        int firstOpen = title.indexOf('【');
        int firstClose = title.indexOf('】', firstOpen + 1);
        if (firstOpen < 0 || firstClose < 0 || firstClose <= firstOpen + 1) {
            throw new IllegalStateException("主要内容块标题必须包含 1 个活动类型标签：" + title);
        }
        if (title.indexOf('【', firstClose + 1) >= 0 || title.indexOf('】', firstClose + 1) >= 0) {
            throw new IllegalStateException("主要内容块标题只能包含 1 个活动类型标签，不能多标签堆叠：" + title);
        }
        return title.substring(firstOpen + 1, firstClose).trim();
    }

    private CoursePlanGenerationException generationException(
            CoursePlanDtos.GeneratedUnit unit,
            CoursePlanDtos.TeachingDesign design,
            String section,
            String blockTitle,
            Integer actualChars,
            Integer requiredChars,
            String message,
            Throwable cause
    ) {
        CoursePlanGenerationException exception = new CoursePlanGenerationException(
                new CoursePlanDtos.GenerationError(
                        unit == null ? null : unit.index(),
                        design == null ? null : design.index(),
                        design == null ? "" : text(design.title()),
                        section,
                        text(blockTitle),
                        actualChars,
                        requiredChars,
                        message
                )
        );
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private int generationStepCount(CoursePlanDtos.DocumentContent draft) {
        int unitCount = safeList(draft.units()).size();
        int designCount = 0;
        for (CoursePlanDtos.GeneratedUnit unit : safeList(draft.units())) {
            designCount += safeList(unit.teachingDesigns()).size();
        }
        return Math.max(1, 1 + unitCount + designCount);
    }

    private JsonNode requestJson(String prompt, int maxTokens, int timeoutSeconds, String sectionName) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是高校课程教案生成专家。必须基于用户提供的证据生成正式中文教案文本，只返回合法 JSON。不得编造材料，不得使用本地兜底。"),
                    Map.of("role", "user", "content", prompt)
            ));
            body.put("response_format", Map.of("type", "json_object"));
            body.put("temperature", 0.25);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek HTTP " + response.statusCode() + ": " + response.body());
            }
            String content = extractContent(response.body());
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(sectionName + "返回 JSON 格式不合法，已停止生成，不使用兜底。", e);
        } catch (IOException e) {
            throw new IllegalStateException(sectionName + "请求失败，已停止生成，不使用兜底：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(sectionName + "请求被中断，已停止生成，不使用兜底。", e);
        }
    }

    private String extractContent(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
        if (content.isBlank()) {
            throw new IllegalStateException("DeepSeek 返回内容为空");
        }
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return content;
    }

    private String requiredText(JsonNode node, String field, String sectionName) {
        String value = text(node.path(field).asText(""));
        if (value.isBlank()) {
            throw new IllegalStateException(sectionName + "缺少字段：" + field);
        }
        return value;
    }

    private List<String> requiredTextList(JsonNode node, String field, String sectionName) {
        JsonNode array = node.path(field);
        if (!array.isArray()) {
            throw new IllegalStateException(sectionName + "缺少数组字段：" + field);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            String value = text(item.asText(""));
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            throw new IllegalStateException(sectionName + "字段为空：" + field);
        }
        return values;
    }

    private Map<String, Object> toDraftDesignMap(CoursePlanDtos.TeachingDesign design, int mainMinutes) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", text(design.title()));
        value.put("focus", text(design.focus()));
        value.put("totalMinutes", design.totalMinutes());
        value.put("afterClassReviewMinutes", design.afterClassReviewMinutes());
        value.put("afterClassReview", text(design.afterClassReview()));
        value.put("introductionMinutes", design.introductionMinutes());
        value.put("summaryMinutes", design.summaryMinutes());
        value.put("assignmentMinutes", design.assignmentMinutes());
        value.put("mainContentMinutes", mainMinutes);
        value.put("draftMainContent", safeList(design.mainContentBlocks()));
        return value;
    }

    private CoursePlanDtos.UnitAnalysis findUnitAnalysis(CoursePlanDtos.AnalysisResult analysis, Integer unitIndex) {
        for (CoursePlanDtos.UnitAnalysis unit : safeList(analysis.units())) {
            if (unit != null && unit.index() != null && unit.index().equals(unitIndex)) {
                return unit;
            }
        }
        return null;
    }

    private List<String> calendarTopics(CoursePlanDtos.UnitAnalysis unit) {
        List<String> topics = new ArrayList<>();
        for (CoursePlanDtos.TeachingCalendarEntry entry : safeList(unit.teachingCalendarEntries())) {
            if (entry != null && !text(entry.topic()).isBlank()) {
                topics.add(entry.topic());
            }
        }
        return distinct(topics);
    }

    private List<String> collectPptTitles(CoursePlanDtos.SourceContext sourceContext) {
        List<String> titles = new ArrayList<>();
        for (CoursePlanDtos.PptMaterial ppt : safeList(sourceContext.pptMaterials())) {
            if (ppt != null) {
                titles.add(firstNonBlank(ppt.title(), ppt.fileName()));
            }
        }
        return compactList(titles, 12, 80);
    }

    @SafeVarargs
    private final List<String> mergeLists(List<String>... lists) {
        List<String> merged = new ArrayList<>();
        for (List<String> list : lists) {
            merged.addAll(safeList(list));
        }
        return distinct(merged);
    }

    private List<String> compactList(List<String> values, int limit, int itemMaxLength) {
        List<String> result = new ArrayList<>();
        for (String value : distinct(values)) {
            String cleaned = text(value);
            if (cleaned.isBlank()) {
                continue;
            }
            result.add(abbreviate(cleaned, itemMaxLength));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<String> distinct(List<String> values) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : safeList(values)) {
            String cleaned = text(value);
            if (!cleaned.isBlank()) {
                set.add(cleaned);
            }
        }
        return new ArrayList<>(set);
    }

    private void validateNoNoise(String sectionName, String... values) {
        for (String value : values) {
            String normalized = text(value)
                    .replace(" ", "")
                    .replace("　", "")
                    .replace("\n", "")
                    .toLowerCase(Locale.ROOT);
            if (normalized.contains("学习策略与技巧")
                    || normalized.contains("好记性不如烂笔头")
                    || normalized.contains("做笔记是个好习惯")
                    || normalized.contains("课程教学基本条件")
                    || normalized.contains("neusoftinstituteofinformation")
                    || normalized.contains("chengduneusoftuniversity")) {
                throw new IllegalStateException(sectionName + "包含原文噪声，已停止生成，不使用兜底。");
            }
        }
    }

    private void requireConfigured() {
        if (!"deepseek".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DeepSeek API Key，已按要求停止课程教案生成，不使用本地兜底。");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("课程教案大模型上下文序列化失败", e);
        }
    }

    private String trimTrailingSlash(String value) {
        String result = text(value);
        if (result.isBlank()) {
            return "https://api.deepseek.com";
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String text = text(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String abbreviate(String value, int maxLength) {
        String text = text(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        return text(value).replaceAll("[\\s　，。；;：:、,.（）()《》\"“”‘’【】\\-—_]", "");
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class ProgressCounter {
        private final CoursePlanGenerationProgress progress;
        private final int total;
        private int current;

        private ProgressCounter(CoursePlanGenerationProgress progress, int total) {
            this.progress = progress == null ? CoursePlanGenerationProgress.NOOP : progress;
            this.total = Math.max(1, total);
            this.current = 0;
        }

        private void update(String stage, String message) {
            progress.update(stage, current, total, message);
        }

        private void step() {
            current = Math.min(total, current + 1);
        }
    }

    private record ResourcePolishResult(
            String textbooksAndReferences,
            String otherTeachingResources,
            String courseEnvironment
    ) {
    }

    private record TeachingDesignOutline(
            String introduction,
            List<CoursePlanDtos.MainContentBlock> mainContentBlocks,
            String summary,
            List<String> assignments,
            List<String> remarks
    ) {
    }

    private record ParsedMainContentBlock(
            String title,
            List<String> points
    ) {
    }

    private record SummaryPlan(
            String knowledgeSummary,
            String keyPoints,
            String difficultPoints,
            String resolvedProblems,
            String pendingProblems
    ) {
        private String toText() {
            return "知识点归纳：" + knowledgeSummary
                    + "\n重点梳理：" + keyPoints
                    + "\n难点辨析：" + difficultPoints
                    + "\n已解决问题：" + resolvedProblems
                    + "\n待解决问题：" + pendingProblems;
        }
    }

    private record AssignmentPlan(
            String researchTask,
            List<String> exerciseTasks,
            String openProject,
            String submissionMethod,
            String submissionStandard
    ) {
        private List<String> toList() {
            List<String> values = new ArrayList<>();
            values.add("课程调研：" + researchTask);
            List<String> numberedExercises = new ArrayList<>();
            for (int i = 0; i < safeExerciseTasks().size(); i++) {
                numberedExercises.add((i + 1) + ". " + safeExerciseTasks().get(i));
            }
            values.add("习题设计：" + String.join("；", numberedExercises));
            values.add("开放性小项目：" + openProject);
            values.add("提交方式：" + submissionMethod);
            values.add("提交标准：" + submissionStandard);
            return values;
        }

        private List<String> safeExerciseTasks() {
            return exerciseTasks == null ? List.of() : exerciseTasks;
        }
    }
}
