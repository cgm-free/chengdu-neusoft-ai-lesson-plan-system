package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CoursePlanContentBuilderService {
    private static final int MINUTES_PER_HOUR = 40;

    private final CoursePlanSchedulePlannerService coursePlanSchedulePlannerService;

    public CoursePlanContentBuilderService(CoursePlanSchedulePlannerService coursePlanSchedulePlannerService) {
        this.coursePlanSchedulePlannerService = coursePlanSchedulePlannerService;
    }

    public CoursePlanDtos.DocumentContent build(CoursePlanDtos.AnalysisResult analysis, String teacherRequirements) {
        if (analysis == null) {
            throw new IllegalArgumentException("缺少课程教案分析结果");
        }
        analysis = normalizeAnalysisForBuild(analysis);
        validateAnalysis(analysis);

        List<CoursePlanDtos.GeneratedUnit> units = new ArrayList<>();
        List<CoursePlanDtos.Issue> warnings = new ArrayList<>();
        String normalizedTeacherRequirements = safeText(firstNonBlank(teacherRequirements, analysis.teacherRequirements()));

        for (CoursePlanDtos.UnitAnalysis unit : analysis.units()) {
            units.add(buildUnit(unit, normalizedTeacherRequirements, warnings));
        }

        return new CoursePlanDtos.DocumentContent(
                analysis.basicInfo(),
                safeText(analysis.basicInfo().courseName()) + "课程教案",
                normalizedTeacherRequirements,
                "",
                "",
                buildCourseEnvironment(analysis, units),
                units,
                warnings
        );
    }

    private CoursePlanDtos.AnalysisResult normalizeAnalysisForBuild(CoursePlanDtos.AnalysisResult analysis) {
        if (analysis.sourceContext() == null) {
            return analysis;
        }
        var planned = coursePlanSchedulePlannerService.plan(
                analysis.units(),
                analysis.sourceContext().teachingCalendar(),
                analysis.basicInfo() == null ? null : analysis.basicInfo().totalHours()
        );
        return new CoursePlanDtos.AnalysisResult(
                analysis.templateFileName(),
                analysis.basicInfo(),
                analysis.templateCheck(),
                planned.units(),
                analysis.conflicts(),
                analysis.valid(),
                analysis.teacherRequirements(),
                planned.splitStrategy(),
                analysis.sourceContext()
        );
    }

    private void validateAnalysis(CoursePlanDtos.AnalysisResult analysis) {
        if (analysis.templateCheck() == null || !analysis.templateCheck().valid()) {
            throw new IllegalStateException("教案模板结构校验未通过，不能继续生成课程教案。");
        }
        if (analysis.basicInfo() == null || safeText(analysis.basicInfo().courseName()).isBlank()) {
            throw new IllegalStateException("课程名称为空，不能继续生成课程教案。");
        }
        if (analysis.units() == null || analysis.units().isEmpty()) {
            throw new IllegalStateException("未识别到课程单元，不能继续生成课程教案。");
        }
        int totalHours = 0;
        for (CoursePlanDtos.UnitAnalysis unit : analysis.units()) {
            if (unit.hours() == null || unit.hours() <= 0) {
                throw new IllegalStateException("单元“" + unit.name() + "”未提供有效学时。");
            }
            List<Integer> designHours = safeDesignHours(unit);
            if (designHours.isEmpty()) {
                throw new IllegalStateException("单元“" + unit.name() + "”未生成有效的教学设计学时分配。");
            }
            if (unit.teachingDesignCount() == null || unit.teachingDesignCount() != designHours.size()) {
                throw new IllegalStateException("单元“" + unit.name() + "”的教学设计数与学时不匹配。");
            }
            int summedDesignHours = designHours.stream().mapToInt(Integer::intValue).sum();
            if (summedDesignHours != unit.hours()) {
                throw new IllegalStateException("单元“" + unit.name() + "”的教学设计学时分配与单元学时不匹配。");
            }
            if (CoursePlanSchedulePlannerService.STRATEGY_FIXED_TWO_HOURS.equals(analysis.splitStrategy())
                    && designHours.stream().anyMatch(value -> value != 2)) {
                throw new IllegalStateException("单元“" + unit.name() + "”应按 2 学时拆分教学设计，但当前分配不符合固定 2 学时模式。");
            }
            if (unit.matchedPptFiles() == null || unit.matchedPptFiles().isEmpty()) {
                throw new IllegalStateException("单元“" + unit.name() + "”未匹配到任何 PPT/课件。");
            }
            totalHours += unit.hours();
        }
        if (analysis.basicInfo().totalHours() != null
                && analysis.basicInfo().totalHours() > 0
                && totalHours != analysis.basicInfo().totalHours()) {
            throw new IllegalStateException("单元学时总和与课程总学时不一致，不能继续生成课程教案。");
        }
    }

    private CoursePlanDtos.GeneratedUnit buildUnit(
            CoursePlanDtos.UnitAnalysis unit,
            String teacherRequirements,
            List<CoursePlanDtos.Issue> warnings
    ) {
        List<List<String>> sessionTopics = buildSessionTopicGroups(unit);
        if (sessionTopics.isEmpty()) {
            warnings.add(new CoursePlanDtos.Issue(
                    "unit.sessionTopicsMissing",
                    "warning",
                    "单元“" + unit.name() + "”未抽取到足够主题，已使用单元名称构建教学设计标题。"
            ));
        }

        return new CoursePlanDtos.GeneratedUnit(
                unit.index(),
                unit.code(),
                unit.name(),
                unit.hours(),
                unit.teachingDesignCount(),
                safeDesignHours(unit),
                safeText(unit.weekRange()),
                buildEnvironmentDesign(unit),
                firstNonBlank(unit.projectText(), "无"),
                buildTheoryObjectives(unit),
                buildSkillObjectives(unit),
                buildQualityObjectives(unit, teacherRequirements),
                normalizeList(firstNonBlankList(unit.keyPoints(), unit.contentItems(), List.of(unit.name()))),
                normalizeList(firstNonBlankList(unit.difficultPoints(), unit.keyPoints(), List.of("围绕重点内容组织辨析与讲评"))),
                buildTeachingMethods(unit),
                buildTeachingOrganization(unit, sessionTopics),
                buildProjectIntroduction(unit),
                normalizeList(unit.matchedPptTitles()),
                buildTeachingDesigns(unit, sessionTopics, teacherRequirements),
                normalizeList(unit.resources()),
                normalizeList(unit.assessments())
        );
    }

    private List<String> buildCoreTopics(CoursePlanDtos.UnitAnalysis unit) {
        List<String> topics = new ArrayList<>();
        List<String> calendarTopics = calendarTopics(unit);
        topics.addAll(calendarTopics);
        topics.addAll(normalizeList(unit.contentItems()));
        topics.addAll(normalizeList(unit.keyPoints()));
        topics.addAll(normalizeList(unit.difficultPoints()));
        topics.addAll(normalizeList(unit.implementationSuggestions()));
        if (calendarTopics.isEmpty()) {
            topics.addAll(normalizeList(unit.slideHeadings()));
        }
        if (topics.isEmpty()) {
            topics.add(unit.name());
        }
        return normalizeList(topics);
    }

    private List<List<String>> buildSessionTopicGroups(CoursePlanDtos.UnitAnalysis unit) {
        int count = safeDesignHours(unit).size();
        if (count <= 0) {
            return List.of();
        }
        List<String> calendarTopics = calendarTopics(unit);
        if (!calendarTopics.isEmpty()) {
            List<List<String>> groups = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String topic = index < calendarTopics.size()
                        ? calendarTopics.get(index)
                        : unit.name() + "第" + (index + 1) + "次课";
                groups.add(List.of(topic));
            }
            return groups;
        }
        return chunkTopics(buildCoreTopics(unit), count, unit.name());
    }

    private List<List<String>> chunkTopics(List<String> topics, int count, String unitName) {
        if (count <= 0) {
            return List.of();
        }
        List<String> source = new ArrayList<>(normalizeList(topics));
        if (source.isEmpty()) {
            source.add(unitName);
        }
        while (source.size() < count) {
            source.add(unitName + "第" + (source.size() + 1) + "次课重点");
        }
        List<List<String>> result = new ArrayList<>();
        int baseSize = Math.max(1, source.size() / count);
        int remainder = source.size() % count;
        int cursor = 0;
        for (int i = 0; i < count; i++) {
            int size = baseSize + (i < remainder ? 1 : 0);
            if (cursor + size > source.size()) {
                size = Math.max(1, source.size() - cursor);
            }
            List<String> chunk = new ArrayList<>(source.subList(cursor, Math.min(source.size(), cursor + size)));
            cursor = Math.min(source.size(), cursor + size);
            if (chunk.isEmpty()) {
                chunk.add(unitName + "课堂讲评");
            }
            result.add(normalizeList(chunk));
        }
        return result;
    }

    private List<CoursePlanDtos.TeachingDesign> buildTeachingDesigns(
            CoursePlanDtos.UnitAnalysis unit,
            List<List<String>> sessionTopics,
            String teacherRequirements
    ) {
        List<CoursePlanDtos.TeachingDesign> designs = new ArrayList<>();
        List<CoursePlanDtos.TeachingCalendarEntry> calendarEntries = safeCalendarEntries(unit);
        List<Integer> designHours = safeDesignHours(unit);
        for (int i = 0; i < sessionTopics.size(); i++) {
            List<String> chunk = sessionTopics.get(i);
            CoursePlanDtos.TeachingCalendarEntry calendarEntry = i < calendarEntries.size() ? calendarEntries.get(i) : null;
            int sessionHours = i < designHours.size() ? designHours.get(i) : inferSessionHours(calendarEntry, unit);
            String calendarTopic = calendarEntry == null ? "" : safeText(calendarEntry.topic());
            String mainTopic = firstNonBlank(calendarTopic, chunk.isEmpty() ? "" : chunk.get(0), unit.name());
            List<String> focusTopics = buildSessionFocusTopics(unit, calendarEntry, mainTopic, chunk);
            List<String> knowledgePoints = buildKnowledgePoints(unit, calendarEntry, mainTopic, focusTopics);
            List<String> casePoints = buildCasePoints(unit, calendarEntry, mainTopic, focusTopics);
            List<String> practicePoints = buildPracticePoints(unit, calendarEntry, mainTopic, focusTopics);
            List<String> matchedSlides = buildMatchedSlides(mainTopic, focusTopics);
            List<String> remarkLines = buildRemarkLines(unit, mainTopic, matchedSlides);
            int totalMinutes = hoursToMinutes(sessionHours);
            SectionDurations durations = buildSectionDurations(totalMinutes);
            List<CoursePlanDtos.MainContentBlock> mainContentBlocks = buildMainContentBlocks(
                    unit,
                    calendarEntry,
                    mainTopic,
                    focusTopics,
                    durations.mainContentMinutes(),
                    totalMinutes
            );

            designs.add(new CoursePlanDtos.TeachingDesign(
                    i + 1,
                    "第" + unit.index() + "单元 第" + (i + 1) + "次课："
                            + abbreviate(mainTopic, 28),
                    buildFocus(calendarTopic, focusTopics),
                    sessionHours,
                    durations.totalMinutes(),
                    durations.afterClassReviewMinutes(),
                    i == 0
                            ? "结合课前阅读和已上传课件，检查学生对本单元基础概念、关键术语和预习问题的准备情况。"
                            : "回顾上一教学设计的核心概念、典型问题和课堂产出，围绕共性错误进行集中讲评。",
                    durations.introductionMinutes(),
                    buildIntroduction(i, mainTopic, focusTopics, calendarTopic),
                    mainContentBlocks,
                    durations.summaryMinutes(),
                    buildSummary(mainTopic, focusTopics),
                    durations.assignmentMinutes(),
                    buildAssignments(unit, focusTopics, teacherRequirements),
                    matchedSlides,
                    remarkLines
            ));
        }
        return designs;
    }

    private SectionDurations buildSectionDurations(int totalMinutes) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int afterClassReview = boundedSectionMinutes(totalMinutes, 0.10, 3, 12, random);
        int introduction = boundedSectionMinutes(totalMinutes, 0.12, 4, 14, random);
        int summary = boundedSectionMinutes(totalMinutes, 0.10, 3, 12, random);
        int assignment = boundedSectionMinutes(totalMinutes, 0.08, 3, 10, random);
        int mainContent = Math.max(20, totalMinutes - afterClassReview - introduction - summary - assignment);
        return new SectionDurations(
                totalMinutes,
                afterClassReview,
                introduction,
                mainContent,
                summary,
                assignment
        );
    }

    private List<CoursePlanDtos.MainContentBlock> buildMainContentBlocks(
            CoursePlanDtos.UnitAnalysis unit,
            CoursePlanDtos.TeachingCalendarEntry calendarEntry,
            String mainTopic,
            List<String> focusTopics,
            int mainMinutes,
            int totalMinutes
    ) {
        int minMainContentChars = requiredMainContentChars(totalMinutes);
        List<String> evidence = collectMainContentEvidence(unit, calendarEntry, mainTopic, focusTopics);
        if (evidence.size() < 5 || totalTextLength(evidence) < 100) {
            throw new IllegalStateException("单元“" + unit.name() + "”课次“" + mainTopic
                    + "”可用于主要内容设计的课程标准、PPT 或教学日历信息不足，不能生成不少于 " + minMainContentChars + " 字的主要内容。");
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int minBlocks = minMainContentBlocks(totalMinutes);
        int maxBlocks = Math.min(maxMainContentBlocks(totalMinutes), evidence.size());
        if (maxBlocks < minBlocks) {
            maxBlocks = minBlocks;
        }
        int blockCount = random.nextInt(minBlocks, maxBlocks + 1);
        List<Integer> minutes = distributeMainContentMinutes(mainMinutes, blockCount);
        String[] titles = {
                "【讲解】概念框架与学习任务",
                "【演示】结构过程与案例分析",
                "【互动】关键问题辨析",
                "【练习】课堂训练与即时反馈",
                "【讲解】方法边界与易错点",
                "【演示】应用场景与结果验证",
                "【互动】迁移讨论与表达训练",
                "【归纳】重点难点整理"
        };

        List<CoursePlanDtos.MainContentBlock> blocks = new ArrayList<>();
        for (int index = 0; index < blockCount; index++) {
            String topic = evidence.get(index % evidence.size());
            String related = evidence.get((index + 1) % evidence.size());
            String extension = evidence.get((index + 2) % evidence.size());
            List<String> points = new ArrayList<>();
            points.add(buildDetailedTeachingPoint(index, topic, related, mainTopic));
            points.add(buildDetailedTeachingActivity(index, topic, extension, calendarEntry));
            blocks.add(new CoursePlanDtos.MainContentBlock(
                    titles[index % titles.length] + "：" + abbreviate(topic, 18),
                    minutes.get(index),
                    points
            ));
        }

        int cursor = 0;
        while (mainContentTextLength(blocks) < minMainContentChars) {
            CoursePlanDtos.MainContentBlock block = blocks.get(cursor % blocks.size());
            String topic = evidence.get(cursor % evidence.size());
            String related = evidence.get((cursor + 3) % evidence.size());
            List<String> points = new ArrayList<>(block.points());
            points.add(buildDetailedTeachingExtension(cursor, topic, related, mainTopic));
            blocks.set(cursor % blocks.size(), new CoursePlanDtos.MainContentBlock(
                    block.title(),
                    block.minutes(),
                    points
            ));
            cursor++;
            if (cursor > evidence.size() * 8 && mainContentTextLength(blocks) < minMainContentChars) {
                throw new IllegalStateException("单元“" + unit.name() + "”课次“" + mainTopic
                        + "”来源信息不足，不能生成不少于 " + minMainContentChars + " 字的主要内容设计。");
            }
        }
        return blocks;
    }

    private List<String> collectMainContentEvidence(
            CoursePlanDtos.UnitAnalysis unit,
            CoursePlanDtos.TeachingCalendarEntry calendarEntry,
            String mainTopic,
            List<String> focusTopics
    ) {
        List<String> evidence = new ArrayList<>();
        appendEvidenceItems(evidence, List.of(mainTopic));
        appendEvidenceItems(evidence, focusTopics);
        if (calendarEntry != null) {
            List<String> calendarEvidence = new ArrayList<>();
            calendarEvidence.add(calendarEntry.topic());
            calendarEvidence.add(calendarEntry.lessonType());
            calendarEvidence.add(calendarEntry.rawText());
            appendEvidenceItems(evidence, calendarEvidence);
        }
        appendEvidenceItems(evidence, unit.contentItems());
        appendEvidenceItems(evidence, unit.keyPoints());
        appendEvidenceItems(evidence, unit.difficultPoints());
        appendEvidenceItems(evidence, unit.implementationSuggestions());
        appendEvidenceItems(evidence, unit.assessments());
        appendEvidenceItems(evidence, unit.slideHeadings());
        return normalizeList(evidence);
    }

    private void appendEvidenceItems(List<String> target, List<String> values) {
        for (String value : normalizeList(values)) {
            String cleaned = cleanGeneratedText(value);
            if (isTeachingEnvironmentOnly(cleaned)) {
                continue;
            }
            List<String> segments = splitEvidenceSegments(cleaned);
            if (segments.isEmpty()) {
                appendDistinct(target, abbreviate(cleaned, 90));
                continue;
            }
            appendDistinct(target, segments);
        }
    }

    private List<String> splitEvidenceSegments(String text) {
        List<String> segments = new ArrayList<>();
        for (String item : safeText(text).split("[。；;\\n]+")) {
            String cleaned = cleanGeneratedText(item);
            if (cleaned.length() >= 4 && cleaned.length() <= 90 && !isTeachingEnvironmentOnly(cleaned)) {
                segments.add(cleaned);
            }
        }
        return normalizeList(segments);
    }

    private List<Integer> distributeMainContentMinutes(int mainMinutes, int blockCount) {
        if (mainMinutes < blockCount * 5) {
            throw new IllegalStateException("主要内容设计剩余时间不足，无法拆分为 5-8 个教学块。");
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Integer> minutes = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) {
            minutes.add(5);
        }
        int remaining = mainMinutes - blockCount * 5;
        while (remaining > 0) {
            int index = random.nextInt(blockCount);
            minutes.set(index, minutes.get(index) + 1);
            remaining--;
        }
        return minutes;
    }

    private String buildDetailedTeachingPoint(int index, String topic, String related, String mainTopic) {
        return switch (index % 5) {
            case 0 -> "【讲解】围绕“" + topic + "”先界定概念、结构和适用边界，再联系“" + related
                    + "”说明本次课要解决的核心问题。教师通过板书或课件图示把术语、操作步骤和判断依据串联起来，要求学生能够用自己的语言解释该内容与“" + mainTopic + "”之间的关系。";
            case 1 -> "【演示】以“" + topic + "”为课堂演示对象，展示从问题描述、条件分析、过程推导到结果验证的完整路径。演示过程中穿插“" + related
                    + "”的对比，让学生观察不同处理方式带来的差异，并记录关键步骤、输入输出和容易遗漏的约束。";
            case 2 -> "【互动】围绕“" + topic + "”设计提问和小组讨论，先让学生判断关键条件，再请学生说明依据。教师根据学生回答追问“" + related
                    + "”中的易混点，及时纠正常见误解，推动学生从记忆概念转向解释原因和表达过程。";
            case 3 -> "【练习】安排与“" + topic + "”对应的随堂练习，要求学生独立完成分析、表示或实现步骤，并在课堂内提交关键过程。讲评时对照“" + related
                    + "”指出规范写法、边界条件和检查方法，使学生形成可复用的解题流程。";
            default -> "【归纳】把“" + topic + "”放回本次课的知识链条中，梳理它与“" + related
                    + "”的先后关系、依赖关系和应用场景。教师引导学生总结已经掌握的判断依据，并标出后续学习仍需继续解决的问题。";
        };
    }

    private String buildDetailedTeachingActivity(
            int index,
            String topic,
            String related,
            CoursePlanDtos.TeachingCalendarEntry calendarEntry
    ) {
        String calendarHint = calendarEntry == null || safeText(calendarEntry.topic()).isBlank()
                ? "课程标准中的本单元要求"
                : "教学日历中的“" + calendarEntry.topic() + "”";
        return switch (index % 4) {
            case 0 -> "【任务】依据" + calendarHint + "，把“" + topic + "”拆成可检查的课堂任务：识别条件、说明方法、完成过程记录、给出结论。教师要求学生在任务单上写出“" + related
                    + "”对应的判断理由，便于后续讲评时定位理解偏差。";
            case 1 -> "【对比】组织学生比较“" + topic + "”和“" + related
                    + "”在概念、步骤或使用场景上的差异。教师把学生回答整理成表格或流程图，明确哪些内容属于必须掌握，哪些内容需要在实验或课后练习中继续巩固。";
            case 2 -> "【反馈】围绕“" + topic + "”收集学生即时反馈，重点观察学生能否说清楚操作依据、能否发现边界情况、能否把“" + related
                    + "”迁移到新问题中。对反馈中暴露的共性问题，教师现场补充例题并要求学生二次修正。";
            default -> "【衔接】将“" + topic + "”与下一环节的“" + related
                    + "”建立衔接，说明当前知识点为什么是后续任务的基础。学生需要在课堂记录中写出一个应用场景、一个注意事项和一个尚未完全理解的问题。";
        };
    }

    private String buildDetailedTeachingExtension(int index, String topic, String related, String mainTopic) {
        return "【深化】围绕“" + topic + "”补充课堂追问：它在“" + mainTopic + "”中的作用是什么，与“" + related
                + "”相比最容易出错的位置在哪里，学生应如何检查自己的过程和结论。教师通过追问、板书整理和即时反馈，把知识点落实到可观察的课堂产出，避免只停留在概念复述。";
    }

    private int mainContentTextLength(List<CoursePlanDtos.MainContentBlock> blocks) {
        int length = 0;
        for (CoursePlanDtos.MainContentBlock block : blocks) {
            length += safeText(block.title()).length();
            for (String point : block.points()) {
                length += safeText(point).length();
            }
        }
        return length;
    }

    private int totalTextLength(List<String> values) {
        int length = 0;
        for (String value : values) {
            length += safeText(value).length();
        }
        return length;
    }

    private List<String> buildSessionFocusTopics(
            CoursePlanDtos.UnitAnalysis unit,
            CoursePlanDtos.TeachingCalendarEntry calendarEntry,
            String mainTopic,
            List<String> chunk
    ) {
        List<String> topics = new ArrayList<>();
        appendDistinct(topics, mainTopic);
        if (isReviewTopic(mainTopic)) {
            appendDistinct(topics, selectPoints(unit.keyPoints(), 2, unit.name()));
            appendDistinct(topics, selectPoints(unit.contentItems(), 2, unit.name()));
            appendDistinct(topics, selectPoints(unit.matchedPptTitles(), 2, unit.name()));
            return limitTopics(topics, 3, mainTopic);
        }
        appendDistinct(topics, selectRelevantTopics(unit.contentItems(), mainTopic, 2));
        appendDistinct(topics, selectRelevantTopics(unit.keyPoints(), mainTopic, 2));
        if (isPracticeSession(calendarEntry)) {
            appendDistinct(topics, selectRelevantTopics(unit.assessments(), mainTopic, 1));
        }
        appendDistinct(topics, chunk);
        appendDistinct(topics, selectPoints(unit.contentItems(), 2, unit.name()));
        return limitTopics(topics, 3, mainTopic);
    }

    private List<String> buildKnowledgePoints(
            CoursePlanDtos.UnitAnalysis unit,
            CoursePlanDtos.TeachingCalendarEntry calendarEntry,
            String mainTopic,
            List<String> focusTopics
    ) {
        List<String> topics = new ArrayList<>();
        appendDistinct(topics, mainTopic);
        appendDistinct(topics, focusTopics);
        appendDistinct(topics, selectRelevantTopics(unit.contentItems(), mainTopic, 2));
        appendDistinct(topics, selectRelevantTopics(unit.keyPoints(), mainTopic, 2));
        if (isReviewTopic(mainTopic)) {
            appendDistinct(topics, selectPoints(unit.keyPoints(), 2, unit.name()));
            appendDistinct(topics, selectPoints(unit.matchedPptTitles(), 2, unit.name()));
        }
        return limitTopics(topics, 3, firstNonBlank(mainTopic, focusTopics.isEmpty() ? "" : focusTopics.get(0), unit.name()));
    }

    private List<String> buildCasePoints(
            CoursePlanDtos.UnitAnalysis unit,
            CoursePlanDtos.TeachingCalendarEntry calendarEntry,
            String mainTopic,
            List<String> focusTopics
    ) {
        List<String> topics = new ArrayList<>();
        appendDistinct(topics, mainTopic);
        appendDistinct(topics, selectRelevantTopics(unit.slideHeadings(), mainTopic, 1));
        appendDistinct(topics, selectRelevantTopics(unit.keyPoints(), mainTopic, 1));
        if (isPracticeSession(calendarEntry)) {
            appendDistinct(topics, selectRelevantTopics(unit.resources(), mainTopic, 1));
        }
        appendDistinct(topics, focusTopics);
        return limitTopics(topics, 3, mainTopic);
    }

    private List<String> buildPracticePoints(
            CoursePlanDtos.UnitAnalysis unit,
            CoursePlanDtos.TeachingCalendarEntry calendarEntry,
            String mainTopic,
            List<String> focusTopics
    ) {
        List<String> topics = new ArrayList<>();
        appendDistinct(topics, mainTopic);
        appendDistinct(topics, selectRelevantTopics(unit.difficultPoints(), mainTopic, 1));
        appendDistinct(topics, selectRelevantTopics(unit.assessments(), mainTopic, 1));
        if (isPracticeSession(calendarEntry)) {
            appendDistinct(topics, selectRelevantTopics(unit.resources(), mainTopic, 1));
        }
        appendDistinct(topics, focusTopics);
        return limitTopics(topics, 3, mainTopic);
    }

    private List<String> buildMatchedSlides(String mainTopic, List<String> focusTopics) {
        List<String> topics = new ArrayList<>();
        appendDistinct(topics, mainTopic);
        appendDistinct(topics, focusTopics);
        return limitTopics(topics, 3, mainTopic);
    }

    private String buildFocus(String calendarTopic, List<String> focusTopics) {
        String focus = String.join("、", focusTopics.subList(0, Math.min(3, focusTopics.size())));
        if (!safeText(calendarTopic).isBlank()) {
            return "依据教学日历“" + calendarTopic + "”，围绕“" + focus + "”组织本次 2 学时教学设计。";
        }
        return "围绕“" + focus + "”组织本次 2 学时教学设计。";
    }

    private String buildIntroduction(int sessionIndex, String mainTopic, List<String> focusTopics, String calendarTopic) {
        String reviewTarget = sessionIndex == 0
                ? "课前预习内容、本单元基础概念和课程标准中的学习要求"
                : "上一次课的核心概念、课堂练习和学生共性问题";
        String questionTarget = firstNonBlank(focusTopics.isEmpty() ? "" : focusTopics.get(0), mainTopic);
        String taskSource = !safeText(calendarTopic).isBlank()
                ? "依据教学日历“" + calendarTopic + "”"
                : "结合课程标准和 PPT 章节脉络";
        return "复习旧课：回顾" + reviewTarget + "，帮助学生把已有知识与本次课内容建立联系。\n"
                + "问题/案例导入：围绕“" + questionTarget + "”提出问题或展示课件案例，引导学生明确为什么要学习该内容。\n"
                + "本次任务说明：" + taskSource + "，说明本次 2 学时需要完成的知识理解、课堂练习和课后产出。";
    }

    private List<String> buildAssignments(
            CoursePlanDtos.UnitAnalysis unit,
            List<String> focusTopics,
            String teacherRequirements
    ) {
        List<String> tasks = new ArrayList<>();
        String topic = firstNonBlank(focusTopics.isEmpty() ? "" : focusTopics.get(0), unit.name());
        List<String> topics = limitTopics(focusTopics, 5, topic);
        tasks.add("课程调研：围绕“" + topic + "”查阅教材、课程资源或可靠技术资料，整理 300 字以上调研记录，说明该知识点的应用场景、关键概念和一个待进一步讨论的问题。提交方式：以文档或学习平台文本形式提交；提交标准：资料来源明确、观点完整、表述规范。");
        tasks.add("习题设计：结合“" + String.join("、", topics.subList(0, Math.min(3, topics.size()))) + "”设计 3-5 道课后习题，题型至少包含概念辨析、过程分析和应用练习。提交方式：提交题目、参考答案与评分要点；提交标准：题目对应本节课重点难点，答案步骤清晰。");
        if (!unit.projectText().isBlank()) {
            tasks.add("开放性小项目：结合本单元项目/实验要求和本节课内容，完成一个可展示的小任务，提交设计思路、关键步骤、运行或验证结果及反思说明。提交标准：能体现本节课核心方法，过程记录完整，结果可检查。");
        } else {
            tasks.add("开放性小项目：围绕“" + topic + "”设计一个小型应用、案例分析或算法验证任务，说明问题背景、处理步骤、预期结果和评价方法。提交方式：提交项目说明、过程截图或关键代码/过程记录；提交标准：任务与本节课内容直接相关，能够体现知识迁移。");
        }
        if (!teacherRequirements.isBlank()) {
            tasks.add("落实教师补充要求：%s".formatted(abbreviate(teacherRequirements, 48)));
        }
        return normalizeList(tasks);
    }

    private String buildSummary(String mainTopic, List<String> focusTopics) {
        String focusedTopics = String.join("、", limitTopics(focusTopics, 3, mainTopic));
        return "组织学生针对本节课涉及的知识点、重点和难点进行归纳总结，重点围绕“" + focusedTopics
                + "”梳理概念、步骤、适用条件和易错点。教师引导学生说明本次课已经解决的问题、仍需继续辨析的问题以及与后续学习内容的衔接关系，要求学生用条目化方式完成课堂小结，并明确自己在理解或实践中还存在的疑问。";
    }

    private List<String> buildRemarkLines(CoursePlanDtos.UnitAnalysis unit, String mainTopic, List<String> matchedSlides) {
        List<String> lines = new ArrayList<>();
        lines.add("单元：" + safeText(unit.name()));
        lines.add("教学焦点：" + abbreviate(mainTopic, 36));
        if (!matchedSlides.isEmpty()) {
            lines.add("课件提示：" + String.join("、", limitTopics(matchedSlides, 2, mainTopic)));
        }
        lines.add("课堂观察：关注本次课重点、难点的理解情况与练习反馈。");
        return normalizeList(lines);
    }

    private String buildEnvironmentDesign(CoursePlanDtos.UnitAnalysis unit) {
        List<String> lines = new ArrayList<>();
        List<String> resourceItems = cleanResourceItems(unit.resources());
        List<String> teachingMaterials = collectTeachingMaterialLabels(resourceItems);
        if (!teachingMaterials.isEmpty()) {
            lines.add("教学材料：" + String.join("、", teachingMaterials));
        }
        if (!unit.matchedPptTitles().isEmpty()) {
            lines.add("课程PPT/课件：" + String.join("、", collectPptTitles(unit.matchedPptTitles(), 4)));
        }
        if (!unit.projectText().isBlank()) {
            lines.add("实验/项目条件：" + abbreviate(unit.projectText(), 42));
        }
        return String.join("\n", deduplicateEnvironmentLines(lines));
    }

    private List<String> buildTheoryObjectives(CoursePlanDtos.UnitAnalysis unit) {
        List<String> objectives = new ArrayList<>();
        for (String item : firstNonBlankList(unit.contentItems(), unit.keyPoints(), List.of(unit.name()))) {
            objectives.add("围绕“" + abbreviate(item, 26) + "”建立清晰的知识框架，能够准确复述核心概念、结构特征或基本原理。");
            if (objectives.size() >= 3) {
                break;
            }
        }
        if (objectives.isEmpty()) {
            objectives.add("能够围绕本单元内容梳理核心知识点，并形成结构化的概念理解。");
        }
        return normalizeList(objectives);
    }

    private List<String> buildSkillObjectives(CoursePlanDtos.UnitAnalysis unit) {
        List<String> objectives = new ArrayList<>();
        if (!unit.requirementText().isBlank()) {
            objectives.add("能够依据课程标准中的学习要求完成相应的分析、实现、验证或表达任务。");
        }
        if (!unit.projectText().isBlank()) {
            objectives.add("能够围绕“" + abbreviate(unit.projectText(), 30) + "”组织课堂实践，提交可检查的过程记录或结果材料。");
        }
        for (String item : unit.difficultPoints()) {
            objectives.add("能够针对“" + abbreviate(item, 24) + "”选择合适的方法进行辨析、推导或课堂练习。");
            if (objectives.size() >= 3) {
                break;
            }
        }
        if (objectives.isEmpty()) {
            objectives.add("能够结合本单元内容完成课堂练习、案例分析或过程性展示。");
        }
        return normalizeList(objectives);
    }

    private List<String> buildQualityObjectives(CoursePlanDtos.UnitAnalysis unit, String teacherRequirements) {
        List<String> objectives = new ArrayList<>();
        if (!unit.assessments().isEmpty()) {
            objectives.add("在课堂讨论、练习和讲评中形成按要求提交过程证据、及时反馈问题的学习习惯。");
        }
        if (!unit.implementationSuggestions().isEmpty()) {
            objectives.add("在本单元学习过程中强化规范表达、过程记录和课堂协作意识。");
        }
        if (!teacherRequirements.isBlank()) {
            objectives.add("结合教师补充要求，落实本单元教学中的规范执行、结果复盘和持续改进。");
        }
        if (objectives.isEmpty()) {
            objectives.add("在本单元学习中形成严谨表达、主动复盘和按时提交课堂成果的基本素养。");
        }
        return normalizeList(objectives);
    }

    private String buildTeachingMethods(CoursePlanDtos.UnitAnalysis unit) {
        List<String> methods = new ArrayList<>();
        methods.addAll(filterByKeyword(unit.implementationSuggestions(), "讲授", "演示", "讨论", "翻转", "探究", "实验", "任务驱动", "案例"));
        if (methods.isEmpty()) {
            methods.add("讲授、案例分析、课堂讨论、随堂练习");
        }
        List<String> media = collectTeachingMaterialLabels(cleanResourceItems(unit.resources()));
        if (!unit.matchedPptTitles().isEmpty()) {
            appendDistinct(media, "课程PPT");
        }
        if (!media.isEmpty()) {
            methods.add("教学媒介：%s".formatted(String.join("、", media)));
        }
        return String.join("；", normalizeList(methods));
    }

    private String buildTeachingOrganization(CoursePlanDtos.UnitAnalysis unit, List<List<String>> sessionTopics) {
        List<String> items = new ArrayList<>();
        List<CoursePlanDtos.TeachingCalendarEntry> calendarEntries = safeCalendarEntries(unit);
        for (int i = 0; i < sessionTopics.size(); i++) {
            List<String> chunk = sessionTopics.get(i);
            String topic = i < calendarEntries.size() ? safeText(calendarEntries.get(i).topic()) : "";
            List<String> focus = normalizeList(List.of(firstNonBlank(topic, chunk.isEmpty() ? "" : chunk.get(0), unit.name())));
            items.add((i + 1) + ". 第" + (i + 1) + "次课围绕“"
                    + String.join("、", focus.subList(0, Math.min(2, focus.size())))
                    + "”组织导入、讲解、互动练习和总结。");
        }
        if (!unit.projectText().isBlank()) {
            items.add("配套项目/实验：" + abbreviate(unit.projectText(), 50));
        }
        return String.join("\n", items);
    }

    private String buildCourseEnvironment(
            CoursePlanDtos.AnalysisResult analysis,
            List<CoursePlanDtos.GeneratedUnit> units
    ) {
        List<String> lines = new ArrayList<>();
        List<String> resourcePool = new ArrayList<>();
        List<String> pptTitles = new ArrayList<>();
        for (CoursePlanDtos.GeneratedUnit unit : units) {
            resourcePool.addAll(normalizeList(unit.resources()));
            pptTitles.addAll(normalizeList(unit.matchedPpts()));
        }
        List<String> teachingMaterials = collectTeachingMaterialLabels(cleanResourceItems(resourcePool));
        if (!teachingMaterials.isEmpty()) {
            lines.add("教学材料：" + String.join("、", teachingMaterials));
        }
        if (!pptTitles.isEmpty()) {
            lines.add("课程PPT/课件：" + String.join("、", collectPptTitles(pptTitles, 8)));
        }
        if (analysis != null && analysis.sourceContext() != null && analysis.sourceContext().teachingCalendar() != null) {
            lines.add("授课安排：结合教学日历对应课次组织课堂教学");
        }
        return String.join("\n", deduplicateEnvironmentLines(lines));
    }

    private List<String> selectRelevantTopics(List<String> source, String topic, int limit) {
        List<String> candidates = normalizeList(source);
        if (candidates.isEmpty() || safeText(topic).isBlank() || limit <= 0) {
            return List.of();
        }
        List<ScoredTopic> scored = new ArrayList<>();
        for (String candidate : candidates) {
            int score = relevanceScore(topic, candidate);
            if (score > 0) {
                scored.add(new ScoredTopic(candidate, score));
            }
        }
        scored.sort((left, right) -> {
            int byScore = Integer.compare(right.score(), left.score());
            if (byScore != 0) {
                return byScore;
            }
            return Integer.compare(left.value().length(), right.value().length());
        });
        List<String> result = new ArrayList<>();
        for (ScoredTopic item : scored) {
            appendDistinct(result, item.value());
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private int relevanceScore(String topic, String candidate) {
        String normalizedTopic = normalizeForMatch(topic);
        String normalizedCandidate = normalizeForMatch(candidate);
        if (normalizedTopic.isBlank() || normalizedCandidate.isBlank()) {
            return 0;
        }
        int score = 0;
        if (normalizedTopic.equals(normalizedCandidate)) {
            score += 80;
        }
        if (normalizedTopic.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedTopic)) {
            score += 45;
        }
        for (String fragment : topicFragments(topic)) {
            String normalizedFragment = normalizeForMatch(fragment);
            if (normalizedFragment.length() < 2) {
                continue;
            }
            if (normalizedCandidate.contains(normalizedFragment) || normalizedFragment.contains(normalizedCandidate)) {
                score += 18;
            }
        }
        return score;
    }

    private List<String> topicFragments(String value) {
        List<String> result = new ArrayList<>();
        for (String item : safeText(value).split("[、，,；;：:（）()\\s]+")) {
            String cleaned = safeText(item).replace("的", "");
            if (!cleaned.isBlank()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private String normalizeForMatch(String value) {
        return safeText(value)
                .replace("　", "")
                .replace(" ", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("的", "")
                .toLowerCase(Locale.ROOT);
    }

    private void appendDistinct(List<String> target, String value) {
        String cleaned = safeText(value);
        if (isSemanticFragment(cleaned) && !target.contains(cleaned)) {
            target.add(cleaned);
        }
    }

    private void appendDistinct(List<String> target, List<String> values) {
        for (String value : values) {
            appendDistinct(target, value);
        }
    }

    private List<String> limitTopics(List<String> values, int limit, String fallback) {
        List<String> normalized = normalizeList(values);
        if (normalized.isEmpty()) {
            return normalizeList(List.of(fallback));
        }
        return normalized.subList(0, Math.min(limit, normalized.size()));
    }

    private boolean isPracticeSession(CoursePlanDtos.TeachingCalendarEntry calendarEntry) {
        return calendarEntry != null && safeText(calendarEntry.lessonType()).contains("实践");
    }

    private boolean isReviewTopic(String value) {
        String cleaned = safeText(value);
        return cleaned.contains("复习") || cleaned.contains("总结");
    }

    private List<String> calendarTopics(CoursePlanDtos.UnitAnalysis unit) {
        List<String> topics = new ArrayList<>();
        for (CoursePlanDtos.TeachingCalendarEntry entry : safeCalendarEntries(unit)) {
            topics.add(entry.topic());
        }
        return normalizeList(topics);
    }

    private List<CoursePlanDtos.TeachingCalendarEntry> safeCalendarEntries(CoursePlanDtos.UnitAnalysis unit) {
        if (unit == null || unit.teachingCalendarEntries() == null) {
            return List.of();
        }
        return unit.teachingCalendarEntries().stream()
                .filter(entry -> entry != null && isSemanticFragment(entry.topic()))
                .toList();
    }

    private List<Integer> safeDesignHours(CoursePlanDtos.UnitAnalysis unit) {
        if (unit == null || unit.teachingDesignHours() == null || unit.teachingDesignHours().isEmpty()) {
            return List.of();
        }
        return unit.teachingDesignHours().stream()
                .filter(value -> value != null && value > 0)
                .toList();
    }

    private int inferSessionHours(CoursePlanDtos.TeachingCalendarEntry calendarEntry, CoursePlanDtos.UnitAnalysis unit) {
        if (calendarEntry != null) {
            Integer allocatedHours = calendarEntry.allocatedHours();
            if (allocatedHours != null && allocatedHours > 0) {
                return allocatedHours;
            }
            Integer periodCount = calendarEntry.periodCount();
            if (periodCount != null && periodCount > 0) {
                return periodCount;
            }
        }
        int count = unit == null || unit.teachingDesignCount() == null || unit.teachingDesignCount() <= 0 ? 1 : unit.teachingDesignCount();
        return Math.max(1, (int) Math.ceil((double) Math.max(1, value(unit == null ? null : unit.hours())) / count));
    }

    private int hoursToMinutes(int hours) {
        return Math.max(40, hours * MINUTES_PER_HOUR);
    }

    private int boundedSectionMinutes(
            int totalMinutes,
            double ratio,
            int minValue,
            int maxValue,
            ThreadLocalRandom random
    ) {
        int base = (int) Math.round(totalMinutes * ratio);
        int bounded = Math.max(minValue, Math.min(maxValue, base));
        int delta = bounded <= minValue ? 0 : random.nextInt(0, Math.min(3, bounded - minValue + 1));
        return Math.max(minValue, Math.min(maxValue, bounded - 1 + delta));
    }

    private int requiredMainContentChars(int totalMinutes) {
        if (totalMinutes <= 50) {
            return 800;
        }
        if (totalMinutes <= 90) {
            return 1500;
        }
        return 2200;
    }

    private int minMainContentBlocks(int totalMinutes) {
        if (totalMinutes <= 50) {
            return 3;
        }
        if (totalMinutes <= 90) {
            return 5;
        }
        return 6;
    }

    private int maxMainContentBlocks(int totalMinutes) {
        if (totalMinutes <= 50) {
            return 4;
        }
        if (totalMinutes <= 90) {
            return 6;
        }
        return 8;
    }

    private int value(Integer value) {
        return value == null || value <= 0 ? 0 : value;
    }

    private String buildProjectIntroduction(CoursePlanDtos.UnitAnalysis unit) {
        if (!unit.projectText().isBlank()) {
            return unit.projectText();
        }
        if (!unit.assessments().isEmpty()) {
            return "本单元以过程性评价为主，围绕课堂表现、作业、练习或实验记录组织项目化成果检查。";
        }
        return "本单元未在课程标准中单列项目/实验要求，教学设计以知识讲解、案例分析与课堂练习为主。";
    }

    private List<String> selectPoints(List<String> source, int limit, String fallback) {
        List<String> normalized = normalizeList(source);
        if (normalized.isEmpty()) {
            return List.of(fallback);
        }
        return normalized.subList(0, Math.min(limit, normalized.size()));
    }

    private List<String> filterByKeyword(List<String> source, String... keywords) {
        List<String> result = new ArrayList<>();
        for (String item : normalizeList(source)) {
            String lower = item.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    result.add(item);
                    break;
                }
            }
        }
        return normalizeList(result);
    }

    private List<String> cleanResourceItems(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String item : normalizeList(source)) {
            String cleaned = cleanGeneratedText(item);
            if (!isSemanticFragment(cleaned) || isTeachingEnvironmentNoise(cleaned)) {
                continue;
            }
            result.add(cleaned);
        }
        return normalizeList(result);
    }

    private List<String> collectTeachingMaterialLabels(List<String> source) {
        List<String> labels = new ArrayList<>();
        for (String item : cleanResourceItems(source)) {
            String lower = item.toLowerCase(Locale.ROOT);
            if (lower.contains("ppt") || item.contains("课件")) {
                appendDistinct(labels, "课件");
            }
            if (item.contains("讲义")) {
                appendDistinct(labels, "讲义");
            }
            if (item.contains("微课") || item.contains("视频")) {
                appendDistinct(labels, "视频");
            }
            if (item.contains("题库") || item.contains("习题")) {
                appendDistinct(labels, "习题库");
            }
            if (item.contains("实验指导书")) {
                appendDistinct(labels, "实验指导书");
            }
        }
        return labels;
    }

    private List<String> collectPptTitles(List<String> source, int limit) {
        List<String> titles = new ArrayList<>();
        for (String item : normalizeList(source)) {
            String cleaned = cleanGeneratedText(item);
            if (isTeachingEnvironmentNoise(cleaned) || isTeachingEnvironmentOnly(cleaned)) {
                continue;
            }
            appendDistinct(titles, abbreviate(cleaned, 42));
            if (titles.size() >= limit) {
                break;
            }
        }
        return titles;
    }

    private List<String> deduplicateEnvironmentLines(List<String> lines) {
        List<String> result = new ArrayList<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String line : normalizeList(lines)) {
            String cleaned = cleanGeneratedText(line);
            if (cleaned.isBlank()) {
                continue;
            }
            String key = normalizeForMatch(cleaned)
                    .replace("教学材料：", "")
                    .replace("课程ppt/课件：", "")
                    .replace("课程ppt课件：", "");
            if (keys.add(key)) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private boolean isTeachingEnvironmentOnly(String value) {
        String cleaned = safeText(value);
        if (cleaned.isBlank()) {
            return true;
        }
        String normalized = normalizeForMatch(cleaned);
        return normalized.equals("课件")
                || normalized.equals("讲义")
                || normalized.equals("视频")
                || normalized.equals("习题库")
                || normalized.equals("实验指导书")
                || normalized.equals("课程ppt")
                || normalized.equals("ppt")
                || normalized.startsWith("教学材料")
                || normalized.startsWith("课程ppt课件");
    }

    private boolean isTeachingEnvironmentNoise(String value) {
        String normalized = normalizeForMatch(value);
        return normalized.contains("好记性不如烂笔头")
                || normalized.contains("做笔记是个好习惯")
                || normalized.contains("准备单独的笔记本")
                || normalized.contains("以备总结复习")
                || normalized.contains("教学法")
                || normalized.contains("课程思政")
                || normalized.contains("培养目标");
    }

    private String cleanGeneratedText(String value) {
        return safeText(value)
                .replaceAll("[\\s　]+", " ")
                .replaceAll("[。；;]+$", "")
                .replace("。；", "。")
                .replace("；。", "。")
                .trim();
    }

    private List<String> splitItems(String text) {
        List<String> result = new ArrayList<>();
        for (String item : safeText(text).split("[；;\\n]+")) {
            String cleaned = safeText(item);
            if (!cleaned.isBlank()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private List<String> firstNonBlankList(List<String>... candidates) {
        for (List<String> candidate : candidates) {
            List<String> normalized = normalizeList(candidate);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return List.of();
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = safeText(value);
            if (isSemanticFragment(cleaned)) {
                distinct.add(cleaned);
            }
        }
        return new ArrayList<>(distinct);
    }

    private boolean isSemanticFragment(String value) {
        String cleaned = safeText(value);
        if (cleaned.length() <= 1) {
            return false;
        }
        if (cleaned.matches("^[\\d\\s.．、:：\\-—_]+$")) {
            return false;
        }
        if (isNoiseText(cleaned)) {
            return false;
        }
        return cleaned.matches(".*[\\p{IsHan}A-Za-z].*");
    }

    private boolean isNoiseText(String value) {
        String normalized = safeText(value)
                .replace("　", "")
                .replace(" ", "")
                .replace("\n", "")
                .replace("\r", "")
                .toLowerCase(Locale.ROOT);
        return normalized.contains("neusoft")
                || normalized.contains("chengduneusoft")
                || normalized.contains("instituteofinformation")
                || normalized.contains("成都东软学院")
                || normalized.contains("东软学院")
                || normalized.contains("thankyou")
                || normalized.contains("thanks")
                || normalized.contains("谢谢")
                || normalized.contains("感谢")
                || normalized.contains("视频链接")
                || normalized.contains("youtube")
                || normalized.contains("bilibili")
                || normalized.contains("copyright")
                || normalized.contains("版权所有")
                || normalized.contains("logo")
                || normalized.contains("好记性不如烂笔头")
                || normalized.contains("做笔记是个好习惯")
                || normalized.contains("准备单独的笔记本");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = safeText(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private String abbreviate(String text, int maxLength) {
        String cleaned = safeText(text);
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record ScoredTopic(String value, int score) {
    }

    private record SectionDurations(
            int totalMinutes,
            int afterClassReviewMinutes,
            int introductionMinutes,
            int mainContentMinutes,
            int summaryMinutes,
            int assignmentMinutes
    ) {
    }
}
